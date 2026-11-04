package com.github.anton.infoege.taskset;

import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskRepository;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskSetService {

    private final TaskSetRepository taskSetRepository;
    private final TaskRepository taskRepository;

    public TaskSetService(TaskSetRepository taskSetRepository, TaskRepository taskRepository) {
        this.taskSetRepository = taskSetRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskSet> list() {
        return taskSetRepository.findAllWithItems();
    }

    /** Подборка вместе с заданиями. */
    @Transactional(readOnly = true)
    public TaskSet get(Long id) {
        return taskSetRepository.findWithItemsById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public TaskSet create(TaskSetForm form) {
        TaskSet set = new TaskSet();
        form.applyTo(set);
        return taskSetRepository.save(set);
    }

    @Transactional
    public void update(Long id, TaskSetForm form) {
        form.applyTo(get(id));
    }

    /**
     * Удаляет подборку, если её ещё никому не назначали.
     *
     * @return false, если удалять нельзя
     */
    @Transactional
    public boolean delete(Long id) {
        TaskSet set = get(id);
        if (taskSetRepository.hasAssignments(id)) {
            return false;
        }
        taskSetRepository.delete(set);
        return true;
    }

    /** Добавляет задания из банка. Архивные и уже добавленные пропускаются. */
    @Transactional
    public void addTasks(Long setId, List<Long> taskIds) {
        TaskSet set = get(setId);
        for (Task task : taskRepository.findAllById(taskIds)) {
            if (!task.isArchived()) {
                set.addTask(task);
            }
        }
    }

    @Transactional
    public void removeTask(Long setId, Long taskId) {
        get(setId).removeTask(taskId);
    }

    @Transactional
    public void move(Long setId, Long taskId, int delta) {
        get(setId).move(taskId, delta);
    }

    /** Копия подборки как шаблон: те же задания и настройки, без назначений. */
    @Transactional
    public TaskSet copy(Long id) {
        TaskSet source = get(id);
        TaskSet copy = new TaskSet();
        copy.setType(source.getType());
        String title = source.getTitle() + " (копия)";
        copy.setTitle(title.length() > 300 ? title.substring(0, 300) : title);
        copy.setDescription(source.getDescription());
        copy.setTimeLimitMinutes(source.getTimeLimitMinutes());
        source.getItems().forEach(item -> copy.addTask(item.getTask()));
        return taskSetRepository.save(copy);
    }
}
