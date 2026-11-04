package com.github.anton.infoege.attempt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, AttemptAnswer.Key> {

    List<AttemptAnswer> findByAttemptIdOrderByPosition(Long attemptId);

    Optional<AttemptAnswer> findByAttemptIdAndTaskId(Long attemptId, Long taskId);

    boolean existsByAttemptIdAndTaskId(Long attemptId, Long taskId);
}
