package com.github.anton.infoege.course;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.anton.infoege.course.Course.CourseTask;
import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * JSON для задач курсов: тесты для запуска в браузере и проверка выводов.
 */
@RestController
@RequestMapping("/api/courses/{courseSlug}/tasks/{taskId}")
public class CourseApiController {

    private final CourseRegistry registry;
    private final CourseCheckService checkService;

    public CourseApiController(CourseRegistry registry, CourseCheckService checkService) {
        this.registry = registry;
        this.checkService = checkService;
    }

    /** Тест для клиента. У скрытых тестов поля output нет: эталон остаётся на сервере. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClientTest(int index, boolean hidden, String input, String output, Map<String, String> files) {
    }

    public record CheckRequest(String code, List<String> outputs, List<String> statuses) {
    }

    @GetMapping("/tests")
    public List<ClientTest> tests(@PathVariable String courseSlug, @PathVariable String taskId) {
        CourseTask task = task(courseSlug, taskId);
        return IntStream.range(0, task.tests().size())
                .mapToObj(i -> {
                    Course.CourseTest t = task.tests().get(i);
                    return new ClientTest(i, t.hidden(), t.input(), t.hidden() ? null : t.output(), t.files());
                })
                .toList();
    }

    @PostMapping("/check")
    public CourseCheckService.CheckResult check(@PathVariable String courseSlug, @PathVariable String taskId,
                                                @RequestBody CheckRequest request,
                                                @AuthenticationPrincipal AppUserDetails user) {
        CourseTask task = task(courseSlug, taskId);
        return checkService.check(user.getId(), courseSlug, task, request.code(), request.outputs(), request.statuses());
    }

    private CourseTask task(String courseSlug, String taskId) {
        return registry.course(courseSlug).flatMap(c -> c.task(taskId)).orElseThrow(NotFoundException::new);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CourseCheckService.TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> tooMany(CourseCheckService.TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
    }
}
