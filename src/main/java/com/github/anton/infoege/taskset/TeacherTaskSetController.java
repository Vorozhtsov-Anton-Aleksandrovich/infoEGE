package com.github.anton.infoege.taskset;

import com.github.anton.infoege.assignment.AssignForm;
import com.github.anton.infoege.assignment.AssignmentService;
import com.github.anton.infoege.assignment.ShowResultsMode;
import com.github.anton.infoege.student.StudentService;
import com.github.anton.infoege.task.TaskFilter;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.web.Dates;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;

@Controller
@RequestMapping("/teacher/sets")
public class TeacherTaskSetController {

    private final TaskSetService taskSetService;
    private final TaskService taskService;
    private final StudentService studentService;
    private final AssignmentService assignmentService;
    private final Dates dates;

    public TeacherTaskSetController(TaskSetService taskSetService, TaskService taskService,
                                    StudentService studentService, AssignmentService assignmentService, Dates dates) {
        this.taskSetService = taskSetService;
        this.taskService = taskService;
        this.studentService = studentService;
        this.assignmentService = assignmentService;
        this.dates = dates;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("sets", taskSetService.list());
        return "teacher/sets/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(defaultValue = "COLLECTION") TaskSetType type, Model model) {
        TaskSetForm form = new TaskSetForm();
        form.setType(type);
        if (type == TaskSetType.VARIANT) {
            form.setTimeLimitMinutes(235);
        }
        model.addAttribute("form", form);
        return "teacher/sets/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") TaskSetForm form, BindingResult binding) {
        validateTimeLimit(form, binding);
        if (binding.hasErrors()) {
            return "teacher/sets/new";
        }
        TaskSet set = taskSetService.create(form);
        return "redirect:/teacher/sets/" + set.getId();
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, @RequestParam(required = false) Integer egeNumber,
                       @RequestParam(required = false) String topic, Model model) {
        TaskSet set = taskSetService.get(id);
        model.addAttribute("form", TaskSetForm.from(set));
        model.addAttribute("assignForm", new AssignForm());
        addShowAttributes(set, egeNumber, topic, model);
        return "teacher/sets/show";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") TaskSetForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        validateTimeLimit(form, binding);
        if (binding.hasErrors()) {
            model.addAttribute("assignForm", new AssignForm());
            addShowAttributes(taskSetService.get(id), null, null, model);
            return "teacher/sets/show";
        }
        taskSetService.update(id, form);
        redirect.addFlashAttribute("message", "Сохранено.");
        return "redirect:/teacher/sets/" + id;
    }

    @PostMapping("/{id}/tasks")
    public String addTasks(@PathVariable Long id, @RequestParam(name = "taskIds", required = false) List<Long> taskIds,
                           RedirectAttributes redirect) {
        if (taskIds == null || taskIds.isEmpty()) {
            redirect.addFlashAttribute("error", "Отметьте задания, которые нужно добавить.");
        } else {
            taskSetService.addTasks(id, taskIds);
            redirect.addFlashAttribute("message", "Задания добавлены.");
        }
        return "redirect:/teacher/sets/" + id;
    }

    @PostMapping("/{id}/tasks/{taskId}/remove")
    public String removeTask(@PathVariable Long id, @PathVariable Long taskId) {
        taskSetService.removeTask(id, taskId);
        return "redirect:/teacher/sets/" + id;
    }

    @PostMapping("/{id}/tasks/{taskId}/move")
    public String moveTask(@PathVariable Long id, @PathVariable Long taskId, @RequestParam int delta) {
        taskSetService.move(id, taskId, delta < 0 ? -1 : 1);
        return "redirect:/teacher/sets/" + id;
    }

    @PostMapping("/{id}/copy")
    public String copy(@PathVariable Long id, RedirectAttributes redirect) {
        TaskSet copy = taskSetService.copy(id);
        redirect.addFlashAttribute("message", "Создана копия. Её можно изменить и назначить отдельно.");
        return "redirect:/teacher/sets/" + copy.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        if (!taskSetService.delete(id)) {
            redirect.addFlashAttribute("error", "Работа уже назначена ученикам, удалить её нельзя. Можно сделать копию.");
            return "redirect:/teacher/sets/" + id;
        }
        redirect.addFlashAttribute("message", "Удалено.");
        return "redirect:/teacher/sets";
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @Valid @ModelAttribute("assignForm") AssignForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        Instant deadline = dates.toInstant(form.getDeadline());
        if (deadline != null && deadline.isBefore(Instant.now())) {
            binding.rejectValue("deadline", "past", "Срок уже прошёл");
        }
        if (form.getShowResultsMode() == ShowResultsMode.AFTER_DEADLINE && deadline == null) {
            binding.rejectValue("deadline", "required", "Для показа результатов после срока нужен срок");
        }
        TaskSet set = taskSetService.get(id);
        if (set.getItems().isEmpty()) {
            binding.reject("empty", "В работе нет заданий");
        }
        if (binding.hasErrors()) {
            model.addAttribute("form", TaskSetForm.from(set));
            addShowAttributes(set, null, null, model);
            return "teacher/sets/show";
        }
        var result = assignmentService.assign(id, form.getStudentIds(), deadline, form.getShowResultsMode());
        String message = "Назначено: " + result.created() + ".";
        if (!result.alreadyAssigned().isEmpty()) {
            message += " Уже было назначено раньше: " + String.join(", ", result.alreadyAssigned()) + ".";
        }
        redirect.addFlashAttribute("message", message);
        return "redirect:/teacher/sets/" + id + "#assignments";
    }

    @PostMapping("/{id}/assignments/{assignmentId}/delete")
    public String deleteAssignment(@PathVariable Long id, @PathVariable Long assignmentId, RedirectAttributes redirect) {
        assignmentService.delete(id, assignmentId);
        redirect.addFlashAttribute("message", "Назначение снято.");
        return "redirect:/teacher/sets/" + id + "#assignments";
    }

    private void addShowAttributes(TaskSet set, Integer egeNumber, String topic, Model model) {
        model.addAttribute("set", set);
        model.addAttribute("bank", taskService.search(new TaskFilter(egeNumber, topic, null, TaskFilter.Archive.ACTIVE))
                .stream().filter(t -> !set.contains(t.getId())).toList());
        model.addAttribute("topics", taskService.topics());
        model.addAttribute("bankEgeNumber", egeNumber);
        model.addAttribute("bankTopic", topic);
        model.addAttribute("students", studentService.active());
        model.addAttribute("assignments", assignmentService.forTaskSet(set.getId()));
    }

    private static void validateTimeLimit(TaskSetForm form, BindingResult binding) {
        if (form.getType() == TaskSetType.VARIANT && form.getTimeLimitMinutes() == null) {
            binding.rejectValue("timeLimitMinutes", "required", "У варианта обязателен лимит времени");
        }
    }
}
