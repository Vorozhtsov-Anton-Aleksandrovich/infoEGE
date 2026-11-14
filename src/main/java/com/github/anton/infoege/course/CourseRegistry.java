package com.github.anton.infoege.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anton.infoege.course.Course.CourseTask;
import com.github.anton.infoege.course.Course.CourseTest;
import com.github.anton.infoege.course.Course.Lesson;
import com.github.anton.infoege.markdown.MarkdownRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Все курсы из {@code classpath:courses/<slug>/course.json}. Читаются один раз при старте и держатся в памяти;
 * новый курс — новая папка, без изменений кода. Любая ошибка в контенте останавливает запуск приложения
 * с перечнем всех найденных проблем.
 */
@Component
public class CourseRegistry {

    private static final Logger log = LoggerFactory.getLogger(CourseRegistry.class);

    public static final Pattern SLUG = Pattern.compile("[a-z0-9-]+");
    public static final Pattern IMAGE_NAME = Pattern.compile("[a-z0-9-]+\\.(svg|png|jpg|webp)");
    private static final Pattern IMAGE_REF = Pattern.compile("images/(" + IMAGE_NAME.pattern() + ")");

    private final MarkdownRenderer markdownRenderer;
    private final ObjectMapper objectMapper;
    private final Map<String, Course> courses = new LinkedHashMap<>();
    /** course.json каждого курса: от него отсчитываются пути к урокам, задачам и картинкам. */
    private final Map<String, Resource> manifests = new LinkedHashMap<>();

    public CourseRegistry(MarkdownRenderer markdownRenderer, ObjectMapper objectMapper,
                          @Value("${app.courses.location:classpath*:courses/*/course.json}") String location) {
        this.markdownRenderer = markdownRenderer;
        this.objectMapper = objectMapper;
        load(location);
    }

    public List<Course> courses() {
        return List.copyOf(courses.values());
    }

    public Optional<Course> course(String slug) {
        return Optional.ofNullable(courses.get(slug));
    }

    /** Картинка курса или пусто, если курса или файла нет. Имя должно быть проверено по {@link #IMAGE_NAME}. */
    public Optional<Resource> image(String courseSlug, String fileName) {
        Resource manifest = manifests.get(courseSlug);
        if (manifest == null || !IMAGE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        try {
            Resource image = manifest.createRelative("images/" + fileName);
            return image.exists() ? Optional.of(image) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ---------- Загрузка ----------

    record CourseJson(String slug, String title, String description, List<LessonJson> lessons) {
    }

    record LessonJson(String slug, String title, String file, String tasks) {
    }

    record TaskFileJson(String lesson, List<TaskJson> tasks) {
    }

    record TaskJson(String id, String title, String statement, String starterCode, String hint, List<TestJson> tests) {
    }

    record TestJson(String input, String output, Boolean hidden, Map<String, String> files) {
    }

    private void load(String location) {
        List<String> errors = new ArrayList<>();
        Resource[] manifests;
        try {
            manifests = new PathMatchingResourcePatternResolver().getResources(location);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось найти курсы по пути " + location, e);
        }
        // Порядок курсов — по адресу папки, чтобы он не зависел от порядка в classpath.
        Arrays.sort(manifests, Comparator.comparing(CourseRegistry::describe));
        Set<String> taskIds = new HashSet<>();
        for (Resource manifest : manifests) {
            String where = describe(manifest);
            try {
                CourseJson json = objectMapper.readValue(read(manifest), CourseJson.class);
                Course course = loadCourse(json, manifest, where, taskIds, errors);
                if (course != null) {
                    if (courses.containsKey(course.slug())) {
                        errors.add(where + ": курс с slug «" + course.slug() + "» уже есть");
                    } else {
                        courses.put(course.slug(), course);
                        this.manifests.put(course.slug(), manifest);
                    }
                }
            } catch (IOException e) {
                errors.add(where + ": не удалось прочитать (" + e.getMessage() + ")");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Ошибки в контенте курсов:\n  - " + String.join("\n  - ", errors));
        }
        courses.values().forEach(c -> log.info("Курс «{}»: уроков {}, задач {}", c.slug(), c.lessons().size(), c.taskCount()));
    }

    private Course loadCourse(CourseJson json, Resource manifest, String where, Set<String> taskIds,
                              List<String> errors) throws IOException {
        int errorsBefore = errors.size();
        if (json.slug() == null || !SLUG.matcher(json.slug()).matches()) {
            errors.add(where + ": slug должен состоять из [a-z0-9-]");
            return null;
        }
        String slug = json.slug();
        require(json.title(), where + ": нет title", errors);
        if (json.lessons() == null || json.lessons().isEmpty()) {
            errors.add(where + ": нет уроков");
            return null;
        }
        List<Lesson> lessons = new ArrayList<>();
        Set<String> lessonSlugs = new HashSet<>();
        for (LessonJson lj : json.lessons()) {
            String lessonWhere = slug + "/" + lj.slug();
            if (lj.slug() == null || !SLUG.matcher(lj.slug()).matches()) {
                errors.add(where + ": slug урока «" + lj.slug() + "» должен состоять из [a-z0-9-]");
                continue;
            }
            if (!lessonSlugs.add(lj.slug())) {
                errors.add(lessonWhere + ": урок с таким slug уже есть");
                continue;
            }
            require(lj.title(), lessonWhere + ": нет title", errors);
            String theory = readRelative(manifest, lj.file(), lessonWhere, errors);
            String tasksText = readRelative(manifest, lj.tasks(), lessonWhere, errors);
            if (theory == null || tasksText == null) {
                continue;
            }
            List<CourseTask> tasks = new ArrayList<>();
            TaskFileJson taskFile = objectMapper.readValue(tasksText, TaskFileJson.class);
            if (!lj.slug().equals(taskFile.lesson())) {
                errors.add(lj.tasks() + ": lesson «" + taskFile.lesson() + "» не совпадает с уроком «" + lj.slug() + "»");
            }
            for (TaskJson tj : taskFile.tasks() == null ? List.<TaskJson>of() : taskFile.tasks()) {
                CourseTask task = loadTask(tj, slug, lj.slug(), manifest, taskIds, errors);
                if (task != null) {
                    tasks.add(task);
                }
            }
            String html = renderMarkdown(theory, slug, manifest, lessonWhere, errors);
            lessons.add(new Lesson(lessons.size() + 1, lj.slug(), lj.title(), html, List.copyOf(tasks)));
        }
        if (errors.size() > errorsBefore) {
            return null;
        }
        return new Course(slug, json.title(), json.description() == null ? "" : json.description(), List.copyOf(lessons));
    }

    private CourseTask loadTask(TaskJson tj, String courseSlug, String lessonSlug, Resource manifest,
                                Set<String> taskIds, List<String> errors) {
        String where = courseSlug + "/" + lessonSlug + "/" + tj.id();
        if (tj.id() == null || tj.id().isBlank()) {
            errors.add(courseSlug + "/" + lessonSlug + ": у задачи нет id");
            return null;
        }
        if (!taskIds.add(tj.id())) {
            errors.add(where + ": id задачи уже используется (id должны быть уникальны во всех курсах)");
            return null;
        }
        require(tj.title(), where + ": нет title", errors);
        require(tj.statement(), where + ": нет statement", errors);
        List<CourseTest> tests = new ArrayList<>();
        for (TestJson t : tj.tests() == null ? List.<TestJson>of() : tj.tests()) {
            if (t.output() == null) {
                errors.add(where + ": у теста нет output");
                continue;
            }
            tests.add(new CourseTest(t.input() == null ? "" : t.input(), t.output(), Boolean.TRUE.equals(t.hidden()),
                    t.files() == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(t.files()))));
        }
        if (tests.stream().allMatch(CourseTest::hidden)) {
            errors.add(where + ": нужен хотя бы один открытый тест");
        }
        return new CourseTask(tj.id(), lessonSlug, tj.title(),
                renderMarkdown(tj.statement(), courseSlug, manifest, where, errors),
                renderMarkdown(tj.hint(), courseSlug, manifest, where, errors),
                tj.starterCode() == null ? "" : tj.starterCode(), List.copyOf(tests));
    }

    /** Markdown → HTML; картинки допускаются только из папки images/ курса и должны существовать. */
    private String renderMarkdown(String markdown, String courseSlug, Resource manifest, String where,
                                  List<String> errors) {
        return markdownRenderer.renderWithImages(markdown, destination -> {
            Matcher m = IMAGE_REF.matcher(destination);
            if (!m.matches()) {
                errors.add(where + ": картинка «" + destination + "» — допускаются только images/имя.(svg|png|jpg|webp)");
                return null;
            }
            try {
                if (!manifest.createRelative(destination).exists()) {
                    errors.add(where + ": нет файла картинки " + destination);
                    return null;
                }
            } catch (IOException e) {
                errors.add(where + ": не удалось проверить картинку " + destination);
                return null;
            }
            return "/course-assets/" + courseSlug + "/images/" + m.group(1);
        });
    }

    private String readRelative(Resource manifest, String path, String where, List<String> errors) {
        if (path == null || path.isBlank() || path.contains("..")) {
            errors.add(where + ": неверный путь к файлу «" + path + "»");
            return null;
        }
        try {
            Resource resource = manifest.createRelative(path);
            if (!resource.exists()) {
                errors.add(where + ": нет файла " + path);
                return null;
            }
            return read(resource);
        } catch (IOException e) {
            errors.add(where + ": не удалось прочитать " + path);
            return null;
        }
    }

    private static void require(String value, String message, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(message);
        }
    }

    private static String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            // \r\n из Windows-редакторов не должен влиять на сравнение вывода.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static String describe(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }
}
