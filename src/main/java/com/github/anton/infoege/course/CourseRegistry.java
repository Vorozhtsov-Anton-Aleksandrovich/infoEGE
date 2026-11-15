package com.github.anton.infoege.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anton.infoege.course.Course.CourseTask;
import com.github.anton.infoege.course.Course.CourseTest;
import com.github.anton.infoege.course.Course.Lesson;
import com.github.anton.infoege.course.Course.TaskType;
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
import java.util.function.Function;
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
    /** Файлы задач (скачиваются учениками): files/имя.xlsx|csv|txt. */
    public static final Pattern FILE_NAME = Pattern.compile("[a-z0-9-]+\\.(xlsx|csv|txt)");

    private static final Pattern BINARY = Pattern.compile("[01]+");
    private static final Pattern NUMBER_LIST = Pattern.compile("\\d+(,\\d+)*");

    private final MarkdownRenderer markdownRenderer;
    private final CourseMarkdown courseMarkdown;
    private final ObjectMapper objectMapper;
    private final Map<String, Course> courses = new LinkedHashMap<>();
    /** course.json каждого курса: от него отсчитываются пути к урокам, задачам и картинкам. */
    private final Map<String, Resource> manifests = new LinkedHashMap<>();

    public CourseRegistry(MarkdownRenderer markdownRenderer, ObjectMapper objectMapper,
                          @Value("${app.courses.location:classpath*:courses/*/course.json}") String location) {
        this.markdownRenderer = markdownRenderer;
        this.courseMarkdown = new CourseMarkdown(markdownRenderer);
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

    /** Файл задачи или пусто, если курса или файла нет. */
    public Optional<Resource> file(String courseSlug, String fileName) {
        Resource manifest = manifests.get(courseSlug);
        if (manifest == null || !FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        try {
            Resource file = manifest.createRelative("files/" + fileName);
            return file.exists() ? Optional.of(file) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ---------- Загрузка ----------

    /** order — место курса в списке (меньше — выше), без него курс идёт в конце. */
    record CourseJson(String slug, String title, String description, Integer order, List<LessonJson> lessons) {
    }

    record LessonJson(String slug, String title, String file, String tasks) {
    }

    record TaskFileJson(String lesson, List<TaskJson> tasks) {
    }

    record TaskJson(String id, String type, String title, String statement, String starterCode, String hint,
                    List<TestJson> tests, List<String> answers, List<String> options, List<String> variables,
                    String expression, Integer numbers, String solution, String answer, String answerFormat,
                    List<String> files) {
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
        List<CourseMarkdown.TaskLink> links = new ArrayList<>();
        List<Course> loaded = new ArrayList<>();
        for (Resource manifest : manifests) {
            String where = describe(manifest);
            try {
                CourseJson json = objectMapper.readValue(read(manifest), CourseJson.class);
                Course course = loadCourse(json, manifest, where, taskIds, links, errors);
                if (course != null) {
                    if (this.manifests.containsKey(course.slug())) {
                        errors.add(where + ": курс с slug «" + course.slug() + "» уже есть");
                    } else {
                        loaded.add(course);
                        this.manifests.put(course.slug(), manifest);
                    }
                }
            } catch (IOException e) {
                errors.add(where + ": не удалось прочитать (" + e.getMessage() + ")");
            }
        }
        loaded.sort(Comparator.comparingInt(Course::order));   // сортировка устойчива: при равном order — по папке
        loaded.forEach(c -> courses.put(c.slug(), c));
        for (CourseMarkdown.TaskLink link : links) {
            boolean exists = course(link.course()).flatMap(c -> c.lesson(link.lesson()))
                    .map(l -> l.tasks().stream().anyMatch(t -> t.id().equals(link.task()))).orElse(false);
            if (!exists) {
                errors.add(link.where() + ": ссылка :::python на несуществующую задачу "
                        + link.course() + "/" + link.lesson() + "/" + link.task());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Ошибки в контенте курсов:\n  - " + String.join("\n  - ", errors));
        }
        courses.values().forEach(c -> log.info("Курс «{}»: уроков {}, задач {}", c.slug(), c.lessons().size(), c.taskCount()));
    }

    private Course loadCourse(CourseJson json, Resource manifest, String where, Set<String> taskIds,
                              List<CourseMarkdown.TaskLink> links, List<String> errors) throws IOException {
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
            String html = courseMarkdown.render(theory, imageResolver(slug, manifest, lessonWhere, errors),
                    lessonWhere, errors, links, name -> exists(manifest, name));
            lessons.add(new Lesson(lessons.size() + 1, lj.slug(), lj.title(), html, List.copyOf(tasks)));
        }
        if (errors.size() > errorsBefore) {
            return null;
        }
        return new Course(slug, json.title(), json.description() == null ? "" : json.description(),
                json.order() == null ? Integer.MAX_VALUE : json.order(), List.copyOf(lessons));
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
        TaskType type;
        try {
            type = tj.type() == null ? TaskType.CODE : TaskType.valueOf(tj.type());
        } catch (IllegalArgumentException e) {
            errors.add(where + ": неизвестный type «" + tj.type() + "»");
            return null;
        }
        List<CourseTest> tests = new ArrayList<>();
        for (TestJson t : tj.tests() == null ? List.<TestJson>of() : tj.tests()) {
            if (t.output() == null) {
                errors.add(where + ": у теста нет output");
                continue;
            }
            tests.add(new CourseTest(t.input() == null ? "" : t.input(), t.output(), Boolean.TRUE.equals(t.hidden()),
                    t.files() == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(t.files()))));
        }
        // «answer» — короткая запись для одного ответа, «answers» — несколько допустимых.
        List<String> answers = new ArrayList<>(tj.answers() == null ? List.of() : tj.answers());
        if (tj.answer() != null) {
            answers.add(tj.answer());
        }
        Course.AnswerFormat format;
        try {
            format = tj.answerFormat() == null ? Course.AnswerFormat.FREE : Course.AnswerFormat.valueOf(tj.answerFormat());
        } catch (IllegalArgumentException e) {
            errors.add(where + ": неизвестный answerFormat «" + tj.answerFormat() + "»");
            return null;
        }
        if (type != TaskType.ANSWER && format != Course.AnswerFormat.FREE) {
            errors.add(where + ": answerFormat бывает только у задач ANSWER");
        }
        for (String a : answers) {
            if (type == TaskType.ANSWER && a != null && !CourseAnswers.validFormat(format, CourseAnswers.normalize(type, format, a))) {
                errors.add(where + ": ответ «" + a + "» не подходит к формату " + format);
            }
        }
        List<String> files = tj.files() == null ? List.of() : List.copyOf(tj.files());
        for (String f : files) {
            if (f == null || !FILE_NAME.matcher(f).matches()) {
                errors.add(where + ": файл «" + f + "» — допускаются только имена [a-z0-9-] с расширением xlsx, csv или txt");
            } else if (!exists(manifest, "files/" + f)) {
                errors.add(where + ": нет файла files/" + f);
            }
        }
        List<String> variables = tj.variables() == null ? List.of() : List.copyOf(tj.variables());
        List<String> options = tj.options() == null ? List.of() : tj.options();
        int numbers = tj.numbers() == null ? 0 : tj.numbers();
        if (type == TaskType.CODE) {
            if (tests.stream().allMatch(CourseTest::hidden)) {
                errors.add(where + ": нужен хотя бы один открытый тест");
            }
        } else {
            validateAnswers(type, answers, options, variables, numbers, where, errors);
            if (!tests.isEmpty()) {
                errors.add(where + ": тесты бывают только у задач CODE");
            }
            require(tj.solution(), where + ": у задачи без кода нужен разбор (solution)", errors);
        }
        Function<String, String> images = imageResolver(courseSlug, manifest, where, errors);
        List<String> optionsHtml = options.stream().map(o -> courseMarkdown.inline(o, images)).toList();
        return new CourseTask(tj.id(), lessonSlug, type, tj.title(),
                renderMarkdown(tj.statement(), courseSlug, manifest, where, errors),
                renderMarkdown(tj.hint(), courseSlug, manifest, where, errors),
                tj.starterCode() == null ? "" : tj.starterCode(), List.copyOf(tests),
                answers.stream().map(a -> CourseAnswers.normalize(type, format, a)).toList(), optionsHtml, variables,
                tj.expression() == null ? "" : tj.expression(), numbers,
                renderMarkdown(tj.solution(), courseSlug, manifest, where, errors), format, files);
    }

    /** Правильные ответы должны подходить к типу задачи: иначе ученик не сможет их ввести. */
    private static void validateAnswers(TaskType type, List<String> answers, List<String> options,
                                        List<String> variables, int numbers, String where, List<String> errors) {
        if (answers.isEmpty() || answers.stream().anyMatch(a -> a == null || a.isBlank())) {
            errors.add(where + ": нет правильного ответа (answers)");
            return;
        }
        for (String raw : answers) {
            String a = CourseAnswers.normalize(type, raw);
            switch (type) {
                case CHOICE -> {
                    if (options.size() < 2) {
                        errors.add(where + ": у CHOICE нужно не меньше двух вариантов (options)");
                    } else if (!a.matches("\\d{1,2}") || Integer.parseInt(a) < 1 || Integer.parseInt(a) > options.size()) {
                        errors.add(where + ": ответ CHOICE — номер варианта от 1 до " + options.size());
                    }
                }
                case TRUTH_TABLE -> {
                    if (variables.isEmpty() || variables.size() > 5) {
                        errors.add(where + ": у TRUTH_TABLE должно быть от 1 до 5 переменных (variables)");
                    } else if (!BINARY.matcher(a).matches() || a.length() != 1 << variables.size()) {
                        errors.add(where + ": ответ TRUTH_TABLE — " + (1 << variables.size()) + " символов 0/1");
                    }
                }
                case MATCH_GRAPH -> {
                    if (variables.size() < 2 || numbers < variables.size()) {
                        errors.add(where + ": у MATCH_GRAPH нужны вершины (variables) и numbers не меньше их числа");
                    } else if (!NUMBER_LIST.matcher(a).matches() || a.split(",").length != variables.size()
                            || Arrays.stream(a.split(",")).mapToInt(Integer::parseInt).anyMatch(n -> n < 1 || n > numbers)) {
                        errors.add(where + ": ответ MATCH_GRAPH — " + variables.size()
                                + " номеров от 1 до " + numbers + " через запятую");
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Картинки допускаются только из папки images/ курса и должны существовать. */
    private Function<String, String> imageResolver(String courseSlug, Resource manifest, String where,
                                                   List<String> errors) {
        return destination -> {
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
        };
    }

    private String renderMarkdown(String markdown, String courseSlug, Resource manifest, String where,
                                  List<String> errors) {
        return markdownRenderer.renderWithImages(markdown, imageResolver(courseSlug, manifest, where, errors));
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

    private static boolean exists(Resource manifest, String path) {
        try {
            return !path.contains("..") && manifest.createRelative(path).exists();
        } catch (IOException e) {
            return false;
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
