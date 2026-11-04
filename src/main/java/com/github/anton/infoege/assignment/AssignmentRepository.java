package com.github.anton.infoege.assignment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @EntityGraph(attributePaths = {"student"})
    @Query("select a from Assignment a where a.taskSet.id = :taskSetId order by a.assignedAt desc, a.id desc")
    List<Assignment> findByTaskSetId(Long taskSetId);

    @EntityGraph(attributePaths = {"taskSet", "taskSet.items"})
    @Query("select a from Assignment a where a.student.id = :studentId order by a.assignedAt desc, a.id desc")
    List<Assignment> findByStudentId(Long studentId);

    @EntityGraph(attributePaths = {"student", "taskSet"})
    @Query("select a from Assignment a order by a.assignedAt desc, a.id desc")
    List<Assignment> findAllWithStudentAndSet();

    @EntityGraph(attributePaths = {"student", "taskSet"})
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findWithStudentAndSetById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findForUpdate(Long id);

    @Query(value = "select exists(select 1 from attempt where assignment_id = :id)", nativeQuery = true)
    boolean hasAttempt(Long id);

    boolean existsByTaskSetIdAndStudentId(Long taskSetId, Long studentId);
}
