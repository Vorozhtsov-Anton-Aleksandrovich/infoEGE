package com.github.anton.infoege.attempt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /**
     * Попытка ученика с блокировкой строки. Все изменения попытки идут через этот метод,
     * поэтому сохранение, heartbeat и завершение одной попытки не мешают друг другу.
     * Чужая попытка не находится: так проверяется доступ (IDOR).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a join fetch a.assignment s where a.id = :id and s.student.id = :studentId")
    Optional<Attempt> findOwnForUpdate(Long id, Long studentId);

    Optional<Attempt> findByAssignmentId(Long assignmentId);

    List<Attempt> findByAssignmentIdIn(Collection<Long> assignmentIds);

    boolean existsByAssignmentId(Long assignmentId);

    @Query("select a.id from Attempt a where a.status = 'IN_PROGRESS' and a.expiresAt <= :threshold")
    List<Long> findOverdueIds(Instant threshold);

    /** Попытки на паузе, у которых прошёл срок назначения: их тоже нужно закрыть. */
    @Query("select a.id from Attempt a join a.assignment s where a.status = 'IN_PROGRESS' and a.pausedAt is not null "
            + "and s.deadlineAt <= :threshold")
    List<Long> findPausedPastDeadlineIds(Instant threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :id")
    Optional<Attempt> findForUpdate(Long id);
}
