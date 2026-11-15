package com.github.anton.infoege.course;

import com.github.anton.infoege.student.StudentService;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Прогресс учеников по курсу (только чтение): таблица «ученики × уроки» и задачи отдельного ученика.
 */
@Controller
@RequestMapping("/teacher/courses/{courseSlug}")
public class TeacherCourseController {

    private final CourseRegistry registry;
    private final CourseProgressService progressService;
    private final StudentService studentService;

    public TeacherCourseController(CourseRegistry registry, CourseProgressService progressService,
                                   StudentService studentService) {
        this.registry = registry;
        this.progressService = progressService;
        this.studentService = studentService;
    }

    @GetMapping
    public String table(@PathVariable String courseSlug, Model model) {
        Course course = course(courseSlug);
        model.addAttribute("course", course);
        model.addAttribute("rows", progressService.table(course));
        return "teacher/courses/table";
    }

    @GetMapping("/students/{studentId}")
    public String student(@PathVariable String courseSlug, @PathVariable Long studentId, Model model) {
        Course course = course(courseSlug);
        User student = studentService.get(studentId);
        model.addAttribute("course", course);
        model.addAttribute("student", student);
        model.addAttribute("progress", progressService.forUser(student.getId(), course));
        model.addAttribute("lessons", progressService.tasksOf(student.getId(), course));
        return "teacher/courses/student";
    }

    private Course course(String slug) {
        return registry.course(slug).orElseThrow(NotFoundException::new);
    }
}
