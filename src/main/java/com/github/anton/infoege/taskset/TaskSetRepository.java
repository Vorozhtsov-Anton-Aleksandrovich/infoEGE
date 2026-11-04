package com.github.anton.infoege.taskset;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskSetRepository extends JpaRepository<TaskSet, Long> {

    @EntityGraph(attributePaths = {"items", "items.task"})
    @Query("select s from TaskSet s where s.id = :id")
    Optional<TaskSet> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"items"})
    @Query("select s from TaskSet s order by s.createdAt desc")
    List<TaskSet> findAllWithItems();

    @Query(value = "select exists(select 1 from assignment where task_set_id = :id)", nativeQuery = true)
    boolean hasAssignments(Long id);
}
