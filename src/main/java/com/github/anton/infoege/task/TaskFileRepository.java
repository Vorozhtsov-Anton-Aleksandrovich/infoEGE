package com.github.anton.infoege.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskFileRepository extends JpaRepository<TaskFile, Long> {

    List<TaskFile> findByTaskIdOrderById(Long taskId);

    List<TaskFile> findByTaskIdInOrderById(Collection<Long> taskIds);

    Optional<TaskFile> findByIdAndTaskId(Long id, Long taskId);

    boolean existsByTaskIdAndOriginalName(Long taskId, String originalName);
}
