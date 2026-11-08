package com.github.anton.infoege.assignment;

import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.taskset.TaskSetRepository;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final TaskSetRepository taskSetRepository;
    private final UserRepository userRepository;

    public AssignmentService(AssignmentRepository assignmentRepository, TaskSetRepository taskSetRepository,
                             UserRepository userRepository) {
        this.assignmentRepository = assignmentRepository;
        this.taskSetRepository = taskSetRepository;
        this.userRepository = userRepository;
    }

    /** Итог назначения: сколько создано и кому работа уже была назначена раньше. */
    public record AssignResult(int created, List<String> alreadyAssigned) {
    }

    /**
     * Назначает работу ученикам. Id учеников приходят из формы, поэтому каждый проверяется:
     * это должен быть активный ученик. Повторно та же работа тому же ученику не назначается.
     */
    @Transactional
    public AssignResult assign(Long taskSetId, List<Long> studentIds, Instant deadlineAt, ShowResultsMode mode) {
        return assign(taskSetId, studentIds, deadlineAt, mode, false);
    }

    /** То же, с разрешением паузы («Закончить потом»). */
    @Transactional
    public AssignResult assign(Long taskSetId, List<Long> studentIds, Instant deadlineAt, ShowResultsMode mode,
                               boolean allowPause) {
        TaskSet set = taskSetRepository.findById(taskSetId).orElseThrow(NotFoundException::new);
        if (mode == ShowResultsMode.AFTER_DEADLINE && deadlineAt == null) {
            throw new IllegalArgumentException("AFTER_DEADLINE requires a deadline");
        }
        int created = 0;
        List<String> already = new ArrayList<>();
        for (Long studentId : studentIds.stream().distinct().toList()) {
            User student = userRepository.findById(studentId)
                    .filter(u -> u.getRole() == Role.STUDENT && u.isActive())
                    .orElseThrow(NotFoundException::new);
            if (assignmentRepository.existsByTaskSetIdAndStudentId(taskSetId, studentId)) {
                already.add(student.getDisplayName());
                continue;
            }
            assignmentRepository.save(new Assignment(student, set, deadlineAt, mode, allowPause));
            created++;
        }
        return new AssignResult(created, already);
    }

    @Transactional(readOnly = true)
    public List<Assignment> forTaskSet(Long taskSetId) {
        return assignmentRepository.findByTaskSetId(taskSetId);
    }

    @Transactional(readOnly = true)
    public List<Assignment> forStudent(Long studentId) {
        return assignmentRepository.findByStudentId(studentId);
    }

    /**
     * Снимает назначение этой работы, если ученик ещё не начинал её.
     *
     * @return false, если попытка уже начата и снять назначение нельзя
     */
    @Transactional
    public boolean delete(Long taskSetId, Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .filter(a -> a.getTaskSet().getId().equals(taskSetId))
                .orElseThrow(NotFoundException::new);
        if (assignmentRepository.hasAttempt(assignmentId)) {
            return false;
        }
        assignmentRepository.delete(assignment);
        return true;
    }
}
