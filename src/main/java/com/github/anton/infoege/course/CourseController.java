package com.github.anton.infoege.course;

import com.github.anton.infoege.course.Course.Lesson;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private final CourseRegistry registry;

    public CourseController(CourseRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/courses")
    public String list(Model model) {
        model.addAttribute("courses", registry.courses());
        return "courses/list";
    }

    @GetMapping("/courses/{courseSlug}")
    public String course(@PathVariable String courseSlug, Model model) {
        model.addAttribute("course", course(courseSlug));
        return "courses/course";
    }

    @GetMapping("/courses/{courseSlug}/{lessonSlug}")
    public String lesson(@PathVariable String courseSlug, @PathVariable String lessonSlug, Model model) {
        Course course = course(courseSlug);
        Lesson lesson = course.lesson(lessonSlug).orElseThrow(NotFoundException::new);
        List<Lesson> lessons = course.lessons();
        int index = lesson.number() - 1;
        model.addAttribute("course", course);
        model.addAttribute("lesson", lesson);
        model.addAttribute("prev", index > 0 ? lessons.get(index - 1) : null);
        model.addAttribute("next", index + 1 < lessons.size() ? lessons.get(index + 1) : null);
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

    private Course course(String slug) {
        return registry.course(slug).orElseThrow(NotFoundException::new);
    }
}
