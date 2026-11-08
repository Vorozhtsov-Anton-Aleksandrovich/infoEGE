package com.github.anton.infoege.attempt;

import com.github.anton.infoege.user.AppUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * JSON-запросы тренажёра. В ответах только статус попытки и оставшееся время по часам сервера.
 */
@RestController
@RequestMapping("/trainer/attempts/{attemptId}")
public class TrainerApiController {

    private final AttemptService attemptService;

    public TrainerApiController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    public record SaveRequest(@NotNull Long taskId, @NotNull List<List<String>> cells) {
    }

    public record HeartbeatRequest(@NotNull Long taskId, int seconds) {
    }

    @PostMapping("/answers")
    public AttemptService.AttemptState save(@PathVariable Long attemptId, @Valid @RequestBody SaveRequest request,
                                            @AuthenticationPrincipal AppUserDetails student) {
        return attemptService.saveAnswer(attemptId, student.getId(), request.taskId(), request.cells());
    }

    /** Режим «Проверить ответ»: сохраняет ответ и отвечает верно/неверно, без правильного ответа. */
    @PostMapping("/check")
    public AttemptService.CheckState check(@PathVariable Long attemptId, @Valid @RequestBody SaveRequest request,
                                           @AuthenticationPrincipal AppUserDetails student) {
        return attemptService.checkAnswer(attemptId, student.getId(), request.taskId(), request.cells());
    }

    @PostMapping("/heartbeat")
    public AttemptService.AttemptState heartbeat(@PathVariable Long attemptId, @Valid @RequestBody HeartbeatRequest request,
                                                 @AuthenticationPrincipal AppUserDetails student) {
        return attemptService.heartbeat(attemptId, student.getId(), request.taskId(), request.seconds());
    }

    @PostMapping("/finish")
    public Map<String, String> finish(@PathVariable Long attemptId, @AuthenticationPrincipal AppUserDetails student) {
        Attempt attempt = attemptService.finish(attemptId, student.getId());
        return Map.of("status", attempt.getStatus().name(),
                "redirect", "/student/assignments/" + attempt.getAssignment().getId());
    }

    /** «Закончить потом»: попытка на паузе, время остановлено. */
    @PostMapping("/pause")
    public Map<String, String> pause(@PathVariable Long attemptId, @AuthenticationPrincipal AppUserDetails student) {
        Attempt attempt = attemptService.pause(attemptId, student.getId());
        return Map.of("status", AttemptService.PAUSED,
                "redirect", "/student/assignments/" + attempt.getAssignment().getId());
    }

    @ExceptionHandler(AttemptService.AttemptClosedException.class)
    public ResponseEntity<Map<String, String>> closed(AttemptService.AttemptClosedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", e.getStatus()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
