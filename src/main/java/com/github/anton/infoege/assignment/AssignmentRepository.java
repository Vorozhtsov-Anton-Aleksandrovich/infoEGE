package com.github.anton.infoege.assignment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @EntityGraph(attributePaths = {"student"})
    @Query("select a from Assignment a where a.taskSet.id = :taskSetId order by a.assignedAt desc, a.id desc")
    List<Assignment> findByTaskSetId(Long taskSetId);

    @EntityGraph(attributePaths = {"taskSet", "taskSet.items"})
    @Query("select a from Assignment a where a.student.id = :studentId order by a.assignedAt desc, a.id desc")
    List<Assignment> findByStudentId(Long studentId);

    boolean existsByTaskSetId(Long taskSetId);

    boolean existsByTaskSetIdAndStudentId(Long taskSetId, Long studentId);
}
