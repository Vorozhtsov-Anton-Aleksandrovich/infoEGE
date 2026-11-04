package com.github.anton.infoege.result;

import com.github.anton.infoege.attempt.AttemptAnswer;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.taskset.TaskSetType;
import com.github.anton.infoege.web.Dates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Экспорт результатов: сводка (строка на назначение) и подробно (строка на задание попытки).
 */
@Component
public class ResultCsvExporter {

    private final TaskService taskService;
    private final Dates dates;

    public ResultCsvExporter(TaskService taskService, Dates dates) {
        this.taskService = taskService;
        this.dates = dates;
    }

    public String summary(List<ResultService.AssignmentResult> results) {
        Csv csv = new Csv().row(List.of("Ученик", "Логин", "Работа", "Тип", "Статус", "Назначено", "Срок",
                "Начато", "Завершено", "Время, с", "Автобалл", "Ручной балл", "Итог", "Максимум", "Комментарий"));
        for (ResultService.AssignmentResult r : results) {
            var a = r.assignment();
            var at = r.attempt();
            csv.row(Arrays.asList(
                    a.getStudent().getDisplayName(), a.getStudent().getLogin(), a.getTaskSet().getTitle(),
                    a.getTaskSet().getType() == TaskSetType.VARIANT ? "вариант" : "подборка",
                    r.status().getLabel(), dates.format(a.getAssignedAt()), dates.format(a.getDeadlineAt()),
                    at == null ? null : dates.format(at.getStartedAt()),
                    at == null ? null : dates.format(at.getFinishedAt()),
                    r.totalSeconds(),
                    at == null ? null : at.getScore(),
                    at == null ? null : at.getManualScore(),
                    r.finalScore(), r.maxScore(),
                    at == null ? null : at.getManualComment()));
        }
        return csv.toString();
    }

    public String tasks(List<ResultService.AssignmentResult> results) {
        List<AttemptAnswer> all = results.stream().flatMap(r -> r.answers().stream()).toList();
        Map<Long, Task> tasks = all.isEmpty() ? Map.of()
                : taskService.tasksById(all.stream().map(AttemptAnswer::getTaskId).distinct().toList());
        Csv csv = new Csv().row(List.of("Ученик", "Логин", "Работа", "№", "Номер ЕГЭ", "Задание", "Ответ ученика",
                "Правильный ответ", "Верно", "Баллы", "Максимум", "Время на задание, с", "Смен ответа",
                "Первое открытие", "Последнее сохранение"));
        for (ResultService.AssignmentResult r : results) {
            int number = 0;
            for (AttemptAnswer answer : r.answers()) {
                number++;
                Task task = tasks.get(answer.getTaskId());
                csv.row(Arrays.asList(
                        r.assignment().getStudent().getDisplayName(), r.assignment().getStudent().getLogin(),
                        r.assignment().getTaskSet().getTitle(), number, task.getEgeNumber(), task.getTitle(),
                        format(answer.getAnswer()), format(answer.getAnswerSnapshot()),
                        answer.getIsCorrect() == null ? null : answer.getIsCorrect() ? "да" : "нет",
                        answer.getPoints(), answer.getPointsSnapshot(), answer.getTimeSpentSeconds(),
                        answer.getAnswerChanges(), dates.format(answer.getFirstOpenedAt()),
                        dates.format(answer.getLastSavedAt())));
            }
        }
        return csv.toString();
    }

    /** Таблица ответа в одну строку: ячейки через пробел, строки через « / ». */
    static String format(List<List<String>> table) {
        if (table == null) {
            return null;
        }
        List<String> rows = new ArrayList<>();
        for (List<String> row : table) {
            rows.add(String.join(" ", row));
        }
        return String.join(" / ", rows);
    }
}
