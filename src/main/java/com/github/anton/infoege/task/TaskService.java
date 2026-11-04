package com.github.anton.infoege.task;

import com.github.anton.infoege.markdown.MarkdownRenderer;
import com.github.anton.infoege.storage.FileStorage;
import com.github.anton.infoege.storage.FileValidator;
import com.github.anton.infoege.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskFileRepository fileRepository;
    private final FileStorage fileStorage;
    private final FileValidator fileValidator;
    private final MarkdownRenderer markdownRenderer;

    public TaskService(TaskRepository taskRepository, TaskFileRepository fileRepository, FileStorage fileStorage,
                       FileValidator fileValidator, MarkdownRenderer markdownRenderer) {
        this.taskRepository = taskRepository;
        this.fileRepository = fileRepository;
        this.fileStorage = fileStorage;
        this.fileValidator = fileValidator;
        this.markdownRenderer = markdownRenderer;
    }

    @Transactional(readOnly = true)
    public List<Task> search(TaskFilter filter) {
        return taskRepository.findAll(filter.toSpecification(), Sort.by("egeNumber", "id"));
    }

    @Transactional(readOnly = true)
    public List<String> topics() {
        return taskRepository.findAllTopics();
    }

    @Transactional(readOnly = true)
    public Task get(Long id) {
        return taskRepository.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public Task create(TaskForm form) {
        Task task = new Task();
        form.applyTo(task);
        return taskRepository.save(task);
    }

    @Transactional
    public void update(Long id, TaskForm form) {
        form.applyTo(get(id));
    }

    @Transactional
    public void setArchived(Long id, boolean archived) {
        get(id).setArchived(archived);
    }

    /**
     * Физически удаляет задание вместе с файлами.
     * Задания, которые уже используются в подборках и попытках, удалять нельзя, только архивировать:
     * эта проверка добавляется вместе с подборками (этап 3) и попытками (этап 4).
     */
    @Transactional
    public void delete(Long id) {
        Task task = get(id);
        List<TaskFile> files = fileRepository.findByTaskIdOrderById(id);
        fileRepository.deleteAll(files);
        taskRepository.delete(task);
        files.forEach(this::deleteStoredQuietly);
    }

    @Transactional(readOnly = true)
    public List<TaskFile> files(Long taskId) {
        return fileRepository.findByTaskIdOrderById(taskId);
    }

    @Transactional(readOnly = true)
    public TaskFile file(Long taskId, Long fileId) {
        return fileRepository.findByIdAndTaskId(fileId, taskId).orElseThrow(NotFoundException::new);
    }

    public InputStream openFile(TaskFile file) throws IOException {
        return fileStorage.open(file.getStorageKey());
    }

    /**
     * Загружает файлы к заданию. Неподходящие файлы пропускаются.
     *
     * @return сообщения об отклонённых файлах
     */
    @Transactional
    public List<String> addFiles(Long taskId, List<MultipartFile> uploads, FileKind kind) {
        get(taskId);
        List<String> errors = new ArrayList<>();
        for (MultipartFile upload : uploads) {
            if (upload.getOriginalFilename() == null || upload.getOriginalFilename().isBlank()) {
                continue; // пустое поле формы без выбранного файла
            }
            String name = FileValidator.cleanName(upload.getOriginalFilename());
            String error = fileValidator.validate(name, upload.getSize(), kind == FileKind.IMAGE);
            if (error == null && fileRepository.existsByTaskIdAndOriginalName(taskId, name)) {
                error = "Файл с именем " + name + " уже есть у задания";
            }
            if (error != null) {
                errors.add(error);
                continue;
            }
            String key;
            try (InputStream in = upload.getInputStream()) {
                key = fileStorage.store(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            fileRepository.save(new TaskFile(taskId, name, key, upload.getSize(), fileValidator.contentType(name), kind));
        }
        return errors;
    }

    @Transactional
    public void deleteFile(Long taskId, Long fileId) {
        TaskFile file = file(taskId, fileId);
        fileRepository.delete(file);
        deleteStoredQuietly(file);
    }

    /**
     * Условие в HTML. Картинки {@code file:имя} превращаются в адреса через {@code fileUrl},
     * который получает id файла.
     */
    public String renderMarkdown(String markdown, List<TaskFile> files, Function<Long, String> fileUrl) {
        return markdownRenderer.render(markdown, name -> files.stream()
                .filter(f -> f.isImage() && f.getOriginalName().equals(name))
                .findFirst()
                .map(f -> fileUrl.apply(f.getId()))
                .orElse(null));
    }

    private void deleteStoredQuietly(TaskFile file) {
        try {
            fileStorage.delete(file.getStorageKey());
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete stored file {}", file.getStorageKey(), e);
        }
    }
}
