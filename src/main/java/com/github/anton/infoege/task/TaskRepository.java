package com.github.anton.infoege.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @Query("select distinct t.topic from Task t where t.topic is not null order by t.topic")
    List<String> findAllTopics();

    /** Задание входит в подборку или в попытку: такое задание только архивируется. */
    @Query(value = "select exists(select 1 from task_set_item where task_id = :id)"
            + " or exists(select 1 from attempt_answer where task_id = :id)", nativeQuery = true)
    boolean isUsed(Long id);
}
