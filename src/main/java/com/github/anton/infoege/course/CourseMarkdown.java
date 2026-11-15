package com.github.anton.infoege.course;

import com.github.anton.infoege.markdown.MarkdownRenderer;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown уроков курса с блоками-директивами. Каждый блок начинается строкой {@code :::имя [заголовок]}
 * и заканчивается строкой {@code :::}; содержимое — обычный Markdown, который проходит санитайзер.
 * Обёртки блоков собирает этот класс из проверенных данных, поэтому классы и data-атрибуты в них разрешены,
 * а в самом Markdown — нет.
 *
 * <pre>
 * :::try              вопрос, строка ---, ответ под спойлером «Показать ответ»
 * :::quiz             вопрос, варианты «- [ ] …» / «- [x] …» (ровно один верный), строка ---, объяснение
 * :::warn [заголовок] жёлтая плашка «Так нельзя»
 * :::note [заголовок] синяя плашка «Запомни»
 * :::cheat [заголовок] шпаргалка урока
 * :::steps [заголовок] разбор по шагам: нумерованный список, шаги можно открывать по одному
 * :::widget имя k=v k="v w"     интерактивный виджет (одна строка, без закрывающей :::)
 * :::python курс/урок/задача текст   ссылка «Проверь программой» на задачу другого курса (одна строка)
 * :::try вопрос ||| ответ :::        то же в одну строку; так же :::warn текст :::, :::note …, :::cheat …
 * ![СКРИНШОТ: описание](images/todo-имя.png)  отдельной строкой: пока файла нет — на странице ничего не выводится
 * </pre>
 * Внутри блока другие блоки не допускаются. Директивы внутри ``` блоков кода не разбираются.
 */
final class CourseMarkdown {

    /** Виджеты, у которых есть код в static/js/course-widgets.js. */
    static final Set<String> WIDGETS = Set.of(
            "bits", "units", "base-converter", "bin-groups", "logic-calc", "number-line", "div-grid",
            "bitwise", "fano-tree", "password", "media-calc", "pixel-grid", "graph-table", "graph-paths", "ip-mask");

    private static final Pattern BLOCK_START = Pattern.compile("^:::(try|quiz|warn|note|cheat|steps)(?:\\s+(.*))?$");
    private static final Pattern WIDGET = Pattern.compile("^:::widget\\s+([a-z][a-z0-9-]*)(.*)$");
    private static final Pattern WIDGET_PARAM = Pattern.compile("([a-z][a-z0-9-]*)=(?:\"([^\"]*)\"|(\\S+))");
    private static final Pattern PYTHON = Pattern.compile("^:::python\\s+([a-z0-9-]+)/([a-z0-9-]+)/([A-Za-z0-9_-]+)\\s+(.+)$");
    private static final Pattern INLINE = Pattern.compile("^:::(try|warn|note|cheat)\\s+(.+?)\\s*:::$");
    private static final Pattern SCREENSHOT =
            Pattern.compile("^!\\[СКРИНШОТ:\\s*(.+)]\\((images/todo-[a-z0-9-]+\\.(?:png|jpg|webp))\\)$");
    private static final Pattern OPTION = Pattern.compile("^- \\[( |x)]\\s+(.+)$");

    private static final Map<String, String> DEFAULT_TITLES = Map.of(
            "warn", "Так нельзя",
            "note", "Запомни",
            "cheat", "Шпаргалка",
            "steps", "Разбор по шагам");

    /** Ссылка из урока на задачу другого курса: проверяется, когда загружены все курсы. */
    record TaskLink(String where, String course, String lesson, String task) {
    }

    private final MarkdownRenderer renderer;

    CourseMarkdown(MarkdownRenderer renderer) {
        this.renderer = renderer;
    }

    String render(String markdown, Function<String, String> imageResolver, String where, List<String> errors,
                  List<TaskLink> links) {
        return render(markdown, imageResolver, where, errors, links, path -> true);
    }

    /** fileExists — есть ли файл курса по относительному пути: нужен для заглушек скриншотов. */
    String render(String markdown, Function<String, String> imageResolver, String where, List<String> errors,
                  List<TaskLink> links, java.util.function.Predicate<String> fileExists) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String[] lines = markdown.split("\n", -1);
        StringBuilder html = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
            }
            Matcher shot = SCREENSHOT.matcher(trimmed);
            if (!inFence && shot.matches() && !fileExists.test(shot.group(2))) {
                // Скриншота ещё нет — строка просто пропускается; появится, когда файл положат в images/.
                continue;
            }
            if (inFence || !trimmed.startsWith(":::")) {
                plain.append(line).append('\n');
                continue;
            }
            flush(plain, html, imageResolver);
            Matcher widget = WIDGET.matcher(trimmed);
            Matcher python = PYTHON.matcher(trimmed);
            Matcher block = BLOCK_START.matcher(trimmed);
            Matcher inline = INLINE.matcher(trimmed);
            if (inline.matches()) {
                String kind = inline.group(1);
                String body = inline.group(2);
                if (kind.equals("try")) {
                    int sep = body.indexOf("|||");
                    if (sep < 0) {
                        errors.add(where + ": строка " + (i + 1) + ": в :::try нет ||| между вопросом и ответом");
                    } else {
                        html.append(tryBlock(List.of(body.substring(0, sep).strip(), "---",
                                body.substring(sep + 3).strip()), imageResolver, where, errors));
                    }
                } else {
                    html.append(block(kind, null, List.of(body), imageResolver, where, errors));
                }
            } else if (widget.matches()) {
                html.append(widget(widget.group(1), widget.group(2), where, errors));
            } else if (python.matches()) {
                links.add(new TaskLink(where, python.group(1), python.group(2), python.group(3)));
                html.append(pythonLink(python.group(1), python.group(2), python.group(3), python.group(4)));
            } else if (block.matches()) {
                int end = i + 1;
                while (end < lines.length && !lines[end].strip().equals(":::")) {
                    if (lines[end].strip().startsWith(":::")) {
                        errors.add(where + ": строка " + (end + 1) + ": блок внутри блока :::" + block.group(1));
                    }
                    end++;
                }
                if (end == lines.length) {
                    errors.add(where + ": строка " + (i + 1) + ": блок :::" + block.group(1) + " не закрыт строкой :::");
                    return html.toString();
                }
                List<String> body = List.of(lines).subList(i + 1, end);
                html.append(block(block.group(1), block.group(2), body, imageResolver, where + ": строка " + (i + 1),
                        errors));
                i = end;
            } else {
                errors.add(where + ": строка " + (i + 1) + ": неизвестная директива «" + trimmed + "»");
            }
        }
        flush(plain, html, imageResolver);
        return html.toString();
    }

    private void flush(StringBuilder plain, StringBuilder html, Function<String, String> imageResolver) {
        if (!plain.isEmpty()) {
            html.append(renderer.renderWithImages(plain.toString(), imageResolver));
            plain.setLength(0);
        }
    }

    private String block(String kind, String title, List<String> body, Function<String, String> imageResolver,
                         String where, List<String> errors) {
        return switch (kind) {
            case "try" -> tryBlock(body, imageResolver, where, errors);
            case "quiz" -> quiz(body, imageResolver, where, errors);
            default -> {
                String heading = title == null || title.isBlank() ? DEFAULT_TITLES.get(kind) : title.strip();
                String icon = kind.equals("warn") ? "⚠️ " : "";
                String extra = kind.equals("steps") ? " data-steps" : "";
                yield "<div class=\"lesson-box box-" + kind + "\"" + extra + "><div class=\"box-title\">" + icon
                        + escape(heading) + "</div>" + md(String.join("\n", body), imageResolver) + "</div>\n";
            }
        };
    }

    private String tryBlock(List<String> body, Function<String, String> imageResolver, String where,
                            List<String> errors) {
        int sep = separator(body);
        if (sep < 0) {
            errors.add(where + ": в :::try нет строки --- между вопросом и ответом");
            return "";
        }
        return "<div class=\"lesson-box box-try\"><div class=\"box-title\">Попробуй сам</div>"
                + md(String.join("\n", body.subList(0, sep)), imageResolver)
                + "<details class=\"try-answer\"><summary>Показать ответ</summary>"
                + md(String.join("\n", body.subList(sep + 1, body.size())), imageResolver)
                + "</details></div>\n";
    }

    private String quiz(List<String> body, Function<String, String> imageResolver, String where, List<String> errors) {
        int sep = separator(body);
        List<String> head = sep < 0 ? body : body.subList(0, sep);
        List<String> question = new ArrayList<>();
        Map<String, Boolean> options = new LinkedHashMap<>();
        for (String line : head) {
            Matcher m = OPTION.matcher(line.strip());
            if (m.matches()) {
                options.put(m.group(2), m.group(1).equals("x"));
            } else {
                question.add(line);
            }
        }
        long correct = options.values().stream().filter(Boolean::booleanValue).count();
        if (options.size() < 2 || correct != 1) {
            errors.add(where + ": в :::quiz нужно не меньше двух вариантов «- [ ] …» и ровно один «- [x] …»");
            return "";
        }
        StringBuilder html = new StringBuilder("<div class=\"lesson-box box-quiz\" data-quiz>"
                + "<div class=\"box-title\">Проверь себя</div>")
                .append(md(String.join("\n", question), imageResolver))
                .append("<div class=\"quiz-options\">");
        options.forEach((text, ok) -> html.append("<button type=\"button\" class=\"quiz-option\" data-correct=\"")
                .append(ok).append("\">").append(inline(text, imageResolver)).append("</button>"));
        html.append("</div>");
        if (sep >= 0) {
            html.append("<details class=\"quiz-explain\"><summary>Ответ и объяснение</summary>")
                    .append(md(String.join("\n", body.subList(sep + 1, body.size())), imageResolver))
                    .append("</details>");
        }
        return html.append("</div>\n").toString();
    }

    private static String widget(String name, String params, String where, List<String> errors) {
        if (!WIDGETS.contains(name)) {
            errors.add(where + ": неизвестный виджет «" + name + "»");
            return "";
        }
        StringBuilder html = new StringBuilder("<div class=\"widget\" data-widget=\"").append(name).append('"');
        Matcher m = WIDGET_PARAM.matcher(params);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            html.append(" data-").append(m.group(1)).append("=\"").append(escape(value)).append('"');
        }
        return html.append("><p class=\"widget-fallback hint\">Здесь интерактивный тренажёр. "
                + "Если он не появился, обновите страницу.</p></div>\n").toString();
    }

    private static String pythonLink(String course, String lesson, String task, String text) {
        return "<div class=\"lesson-box box-python\"><div class=\"box-title\">Проверь программой</div><p>"
                + escape(text) + " <a href=\"/courses/" + course + "/" + lesson + "#" + task
                + "\">Открыть задачу в курсе Python →</a></p></div>\n";
    }

    private String md(String markdown, Function<String, String> imageResolver) {
        return renderer.renderWithImages(markdown, imageResolver);
    }

    /** Markdown одной строки без обёртки в абзац — для вариантов ответа. */
    String inline(String markdown, Function<String, String> imageResolver) {
        String html = md(markdown, imageResolver).strip();
        if (html.startsWith("<p>") && html.endsWith("</p>") && html.indexOf("<p>", 1) < 0) {
            html = html.substring(3, html.length() - 4);
        }
        return html;
    }

    private static int separator(List<String> body) {
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i).strip().equals("---")) {
                return i;
            }
        }
        return -1;
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
