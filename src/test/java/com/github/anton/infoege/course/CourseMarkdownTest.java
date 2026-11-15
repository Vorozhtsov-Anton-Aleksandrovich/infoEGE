package com.github.anton.infoege.course;

import com.github.anton.infoege.markdown.MarkdownRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseMarkdownTest {

    private final CourseMarkdown markdown = new CourseMarkdown(new MarkdownRenderer());
    private final List<String> errors = new ArrayList<>();
    private final List<CourseMarkdown.TaskLink> links = new ArrayList<>();

    private String render(String md) {
        return markdown.render(md, d -> d, "урок", errors, links);
    }

    @Test
    void tryBlockHidesAnswerInSpoiler() {
        String html = render("Текст\n\n:::try\nСколько бит?\n---\n**5** бит\n:::\n\nДальше");

        assertThat(html).contains("<p>Текст</p>", "class=\"lesson-box box-try\"", "Попробуй сам", "<p>Сколько бит?</p>",
                "<details class=\"try-answer\"><summary>Показать ответ</summary>", "<strong>5</strong>", "<p>Дальше</p>");
        assertThat(errors).isEmpty();
    }

    @Test
    void boxesHaveDefaultOrCustomTitles() {
        String html = render(":::warn\nКбайт = 1024\n:::\n\n:::note Зачем это нужно\nЗадание 4\n:::\n\n:::cheat\n- N = 2^i\n:::");

        assertThat(html).contains("box-warn", "⚠️ Так нельзя", "box-note", "Зачем это нужно", "box-cheat", "Шпаргалка");
        assertThat(errors).isEmpty();
    }

    @Test
    void stepsBlockIsMarkedForStepByStepMode() {
        String html = render(":::steps\n1. Первый\n2. Второй\n:::");

        assertThat(html).contains("class=\"lesson-box box-steps\" data-steps", "Разбор по шагам", "<ol>", "<li>Первый</li>");
    }

    @Test
    void quizRendersOptionsWithCorrectFlag() {
        String html = render(":::quiz\nСколько бит на 100 вариантов?\n- [ ] 6\n- [x] **7**\n---\n2⁷ = 128\n:::");

        assertThat(html).contains("data-quiz", "<p>Сколько бит на 100 вариантов?</p>",
                "<button type=\"button\" class=\"quiz-option\" data-correct=\"false\">6</button>",
                "data-correct=\"true\"><strong>7</strong></button>", "class=\"quiz-explain\"", "2⁷ &#61; 128");
        assertThat(errors).isEmpty();
    }

    @Test
    void widgetBecomesPlaceholderWithEscapedParameters() {
        String html = render(":::widget logic-calc expr=\"x → <y>\" n=30");

        assertThat(html).contains("<div class=\"widget\" data-widget=\"logic-calc\" data-expr=\"x → &lt;y&gt;\" data-n=\"30\">");
        assertThat(errors).isEmpty();
    }

    @Test
    void pythonLinkIsRecordedForValidation() {
        String html = render(":::python python-ege/11-ege-tricks/py11-6 Перебор таблицы");

        assertThat(html).contains("box-python", "href=\"/courses/python-ege/11-ege-tricks#py11-6\"", "Перебор таблицы");
        assertThat(links).containsExactly(new CourseMarkdown.TaskLink("урок", "python-ege", "11-ege-tricks", "py11-6"));
    }

    @Test
    void directivesInsideCodeBlocksAreLeftAlone() {
        String html = render("```\n:::widget nope\n```");

        assertThat(html).contains(":::widget nope").doesNotContain("data-widget");
        assertThat(errors).isEmpty();
    }

    @Test
    void contentInsideBlocksIsSanitized() {
        String html = render(":::note\n<script>alert(1)</script><span class=\"hl-red\">1</span><span class=\"evil\">2</span>\n:::");

        assertThat(html).doesNotContain("<script", "evil").contains("<span class=\"hl-red\">1</span>");
    }

    @Test
    void oneLineTryAndWarn() {
        String html = render(":::try Сколько ячеек в A1:C5? ||| **15** :::\n\n:::warn Кбайт — это 1024 байта :::");

        assertThat(html).contains("box-try", "<p>Сколько ячеек в A1:C5?</p>", "Показать ответ", "<strong>15</strong>",
                "box-warn", "Кбайт — это 1024 байта");
        assertThat(errors).isEmpty();
    }

    @Test
    void missingScreenshotIsHiddenUntilFileAppears() {
        String md = "До\n\n![СКРИНШОТ: окно «Сортировка»](images/todo-sort.png)\n\nПосле";
        String missing = markdown.render(md, d -> d, "урок", errors, links, path -> false);
        String real = markdown.render(md, d -> "/x/" + d, "урок", errors, links, path -> true);

        assertThat(missing).contains("<p>До</p>", "<p>После</p>").doesNotContain("<img", "СКРИНШОТ", "Сортировка");
        assertThat(real).contains("<img", "src=\"/x/images/todo-sort.png\"");
        assertThat(errors).isEmpty();
    }

    @Test
    void reportsBrokenDirectives() {
        render(":::widget unknown-widget");
        render(":::try\nбез разделителя\n:::");
        render(":::quiz\nВопрос\n- [ ] а\n- [ ] б\n:::");
        render(":::note\nне закрыт");
        render(":::frobnicate");

        assertThat(String.join("\n", errors)).contains("неизвестный виджет «unknown-widget»", "в :::try нет строки ---",
                "ровно один «- [x] …»", "блок :::note не закрыт", "неизвестная директива «:::frobnicate»");
    }
}
