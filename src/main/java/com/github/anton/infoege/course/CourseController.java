package com.github.anton.infoege.course;

import com.github.anton.infoege.course.Course.Lesson;
import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Раздел «Курсы»: список курсов, уроки курса, страница урока и картинки к урокам.
 * Доступен всем вошедшим пользователям (ученикам и преподавателю).
 */
@Controller
public class CourseController {

    private static final Map<String, MediaType> IMAGE_TYPES = Map.of(
            "svg", MediaType.valueOf("image/svg+xml"),
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "webp", MediaType.valueOf("image/webp"));

    private static final Map<String, MediaType> FILE_TYPES = Map.of(
            "xlsx", MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            "csv", MediaType.valueOf("text/csv;charset=UTF-8"),
            "txt", MediaType.valueOf("text/plain;charset=UTF-8"));

    private final CourseRegistry registry;
    private final CourseProgressRepository progress;
    private final CourseProgressService progressService;
    private final CourseWebConfig.Versions versions;

    public CourseController(CourseRegistry registry, CourseProgressRepository progress,
                            CourseProgressService progressService, CourseWebConfig.Versions versions) {
        this.registry = registry;
        this.progress = progress;
        this.progressService = progressService;
        this.versions = versions;
    }

    @GetMapping("/courses")
    public String list(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("courses", registry.courses());
        model.addAttribute("courseProgress", progressService.forUser(user.getId()));
        return "courses/list";
    }

    @GetMapping("/courses/{courseSlug}")
    public String course(@PathVariable String courseSlug, @AuthenticationPrincipal AppUserDetails user, Model model) {
        Course course = course(courseSlug);
        model.addAttribute("course", course);
        model.addAttribute("progress", progressService.forUser(user.getId(), course));
        return "courses/course";
    }

    @GetMapping("/courses/{courseSlug}/{lessonSlug}")
    public String lesson(@PathVariable String courseSlug, @PathVariable String lessonSlug,
                         @AuthenticationPrincipal AppUserDetails user, Model model) {
        Course course = course(courseSlug);
        Lesson lesson = course.lesson(lessonSlug).orElseThrow(NotFoundException::new);
        List<Lesson> lessons = course.lessons();
        int index = lesson.number() - 1;
        model.addAttribute("course", course);
        model.addAttribute("lesson", lesson);
        model.addAttribute("prev", index > 0 ? lessons.get(index - 1) : null);
        model.addAttribute("next", index + 1 < lessons.size() ? lessons.get(index + 1) : null);
        model.addAttribute("statuses", progress.statuses(user.getId(), courseSlug));
        model.addAttribute("fails", progress.failCounts(user.getId(), courseSlug));
        model.addAttribute("versions", versions);
        return "courses/lesson";
    }

    @GetMapping("/course-assets/{courseSlug}/images/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable String courseSlug, @PathVariable String fileName) {
        if (!CourseRegistry.IMAGE_NAME.matcher(fileName).matches()) {
            throw new NotFoundException();
        }
        Resource image = registry.image(courseSlug, fileName).orElseThrow(NotFoundException::new);
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        return ResponseEntity.ok()
                .contentType(IMAGE_TYPES.get(extension))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(image);
    }

    /** Файл к задаче курса: всегда скачивается (attachment), имя проверено по {@link CourseRegistry#FILE_NAME}. */
    @GetMapping("/course-assets/{courseSlug}/files/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable String courseSlug, @PathVariable String fileName) {
        if (!CourseRegistry.FILE_NAME.matcher(fileName).matches()) {
            throw new NotFoundException();
        }
        Resource file = registry.file(courseSlug, fileName).orElseThrow(NotFoundException::new);
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        return ResponseEntity.ok()
                .contentType(FILE_TYPES.get(extension))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(file);
    }

    private Course course(String slug) {
        return registry.course(slug).orElseThrow(NotFoundException::new);
    }
}
