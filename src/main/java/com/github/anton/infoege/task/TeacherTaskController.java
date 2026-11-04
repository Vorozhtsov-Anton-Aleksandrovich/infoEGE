package com.github.anton.infoege.task;

import com.github.anton.infoege.checking.AnswerNormalizer;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/teacher/tasks")
public class TeacherTaskController {

    private final TaskService taskService;

    public TeacherTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public String list(TaskFilter filter, Model model) {
        model.addAttribute("filter", filter);
        model.addAttribute("tasks", taskService.search(filter));
        model.addAttribute("topics", taskService.topics());
        return "teacher/tasks/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new TaskForm());
        model.addAttribute("topics", taskService.topics());
        return "teacher/tasks/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") TaskForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        validateAnswer(form, binding);
        if (binding.hasErrors()) {
            model.addAttribute("topics", taskService.topics());
            return "teacher/tasks/form";
        }
        Task task = taskService.create(form);
        redirect.addFlashAttribute("message", "Задание создано. Теперь можно добавить файлы.");
        return "redirect:/teacher/tasks/" + task.getId() + "/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Task task = taskService.get(id);
        model.addAttribute("form", TaskForm.from(task));
        addEditAttributes(task, model);
        return "teacher/tasks/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") TaskForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        validateAnswer(form, binding);
        if (binding.hasErrors()) {
            addEditAttributes(taskService.get(id), model);
            return "teacher/tasks/form";
        }
        taskService.update(id, form);
        redirect.addFlashAttribute("message", "Задание сохранено.");
        return "redirect:/teacher/tasks/" + id + "/edit";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, @RequestParam boolean archived, RedirectAttributes redirect) {
        taskService.setArchived(id, archived);
        redirect.addFlashAttribute("message", archived ? "Задание перенесено в архив." : "Задание возвращено из архива.");
        return "redirect:/teacher/tasks/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        taskService.delete(id);
        redirect.addFlashAttribute("message", "Задание удалено.");
        return "redirect:/teacher/tasks";
    }

    /** Предпросмотр так, как задание увидит ученик. Разбор показывается ниже отдельно, только преподавателю. */
    @GetMapping("/{id}/preview")
    public String preview(@PathVariable Long id, Model model) {
        Task task = taskService.get(id);
        List<TaskFile> files = taskService.files(id);
        model.addAttribute("task", task);
        model.addAttribute("statementHtml", taskService.renderMarkdown(task.getStatementMd(), files, fileId -> fileUrl(id, fileId)));
        model.addAttribute("solutionHtml", taskService.renderMarkdown(task.getSolutionMd(), files, fileId -> fileUrl(id, fileId)));
        model.addAttribute("attachments", files.stream().filter(f -> !f.isImage()).toList());
        model.addAttribute("fileUrlBase", "/teacher/tasks/" + id + "/files/");
        return "teacher/tasks/preview";
    }

    /** HTMX: предпросмотр Markdown в редакторе. Приходит одно из полей: условие или разбор. */
    @PostMapping("/markdown-preview")
    public String markdownPreview(@RequestParam(required = false) String statementMd,
                                  @RequestParam(required = false) String solutionMd,
                                  @RequestParam(required = false) Long taskId,
                                  Model model) {
        String markdown = statementMd != null ? statementMd : solutionMd;
        List<TaskFile> files = taskId == null ? List.of() : taskService.files(taskId);
        model.addAttribute("html", taskService.renderMarkdown(markdown, files, fileId -> fileUrl(taskId, fileId)));
        return "teacher/tasks/markdown-preview";
    }

    @PostMapping("/{id}/files")
    public String uploadFiles(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files,
                              @RequestParam FileKind kind, RedirectAttributes redirect) {
        List<String> errors = taskService.addFiles(id, files, kind);
        if (errors.isEmpty()) {
            redirect.addFlashAttribute("message", "Файлы загружены.");
        } else {
            redirect.addFlashAttribute("fileErrors", errors);
        }
        return "redirect:/teacher/tasks/" + id + "/edit#files";
    }

    @PostMapping("/{id}/files/{fileId}/delete")
    public String deleteFile(@PathVariable Long id, @PathVariable Long fileId, RedirectAttributes redirect) {
        taskService.deleteFile(id, fileId);
        redirect.addFlashAttribute("message", "Файл удалён.");
        return "redirect:/teacher/tasks/" + id + "/edit#files";
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id, @PathVariable Long fileId) throws IOException {
        TaskFile file = taskService.file(id, fileId);
        ContentDisposition disposition = (file.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .body(new InputStreamResource(taskService.openFile(file)));
    }

    private void addEditAttributes(Task task, Model model) {
        model.addAttribute("task", task);
        model.addAttribute("files", taskService.files(task.getId()));
        model.addAttribute("topics", taskService.topics());
    }

    private static void validateAnswer(TaskForm form, BindingResult binding) {
        if (form.getCorrectAnswer() == null || form.getAnswerRows() == null || form.getAnswerCols() == null) {
            return;
        }
        var table = AnswerNormalizer.parse(form.getCorrectAnswer());
        if (!table.isEmpty() && !AnswerNormalizer.hasShape(table, form.getAnswerRows(), form.getAnswerCols())) {
            binding.rejectValue("correctAnswer", "shape",
                    "Ответ должен быть таблицей " + form.getAnswerRows() + " × " + form.getAnswerCols()
                            + ": по строке на строку таблицы, ячейки через пробел");
        }
    }

    private static String fileUrl(Long taskId, Long fileId) {
        return "/teacher/tasks/" + taskId + "/files/" + fileId;
    }
}
