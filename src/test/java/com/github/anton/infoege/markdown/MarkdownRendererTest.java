package com.github.anton.infoege.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    private String render(String md) {
        return renderer.render(md, name -> name.equals("graph.png") ? "/files/7" : null);
    }

    @Test
    void rendersBasicMarkdownAndTables() {
        String html = render("**Жирный** и `код`\n\n| a | b |\n|---|---|\n| 1 | 2 |");

        assertThat(html).contains("<strong>Жирный</strong>", "<code>код</code>", "<table>", "<td>1</td>");
    }

    @Test
    void allowsSubAndSup() {
        assertThat(render("2<sup>10</sup> и A<sub>16</sub>")).contains("<sup>10</sup>", "<sub>16</sub>");
    }

    @Test
    void removesScriptsAndEventHandlers() {
        String html = render("<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n<a href=\"javascript:alert(1)\">x</a>");

        assertThat(html).doesNotContain("<script", "onerror", "javascript:");
    }

    @Test
    void removesIframeAndStyle() {
        String html = render("<iframe src=\"https://evil\"></iframe><style>body{}</style><p style=\"position:fixed\">t</p>");

        assertThat(html).doesNotContain("<iframe", "<style", "position:fixed");
    }

    @Test
    void resolvesTaskImages() {
        assertThat(render("![граф](file:graph.png)")).contains("src=\"/files/7\"", "alt=\"граф\"");
    }

    @Test
    void decodesNonAsciiImageNames() {
        String html = renderer.render("![](file:схема.png)", name -> name.equals("схема.png") ? "/files/9" : null);

        assertThat(html).contains("src=\"/files/9\"");
    }

    @Test
    void dropsUnknownTaskImages() {
        assertThat(render("![x](file:missing.png)")).doesNotContain("<img", "file:");
    }

    @Test
    void emptyMarkdownGivesEmptyHtml() {
        assertThat(render(null)).isEmpty();
        assertThat(render("  ")).isEmpty();
    }
}
