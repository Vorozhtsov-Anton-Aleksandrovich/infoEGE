package com.github.anton.infoege.markdown;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Markdown → безопасный HTML.
 * Картинки из файлов задания пишутся как {@code ![подпись](file:имя.png)}; адрес подставляет
 * вызывающий код через {@code fileUrlResolver}, потому что он разный для преподавателя и ученика.
 * Подчёркивание пишется HTML-тегом {@code <u>…</u>} (в Markdown своего синтаксиса нет).
 */
@Component
public class MarkdownRenderer {

    public static final String FILE_PREFIX = "file:";

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES)
            .and(new HtmlPolicyBuilder()
                    // details/summary — спойлеры «Показать ответ» в уроках курсов.
                    .allowElements("pre", "code", "hr", "sub", "sup", "details", "summary")
                    .toFactory());

    private final Parser parser = Parser.builder().extensions(List.of(TablesExtension.create())).build();
    // Одиночный Enter в тексте — перенос строки, как ожидает преподаватель (по стандарту Markdown строки склеились бы).
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create()))
            .softbreak("<br />")
            .build();

    public String render(String markdown, Function<String, String> fileUrlResolver) {
        return renderWithImages(markdown, destination -> destination.startsWith(FILE_PREFIX)
                ? fileUrlResolver.apply(destination.substring(FILE_PREFIX.length()))
                : destination);
    }

    /**
     * То же, но адрес каждой картинки целиком проходит через {@code imageResolver}:
     * он возвращает итоговый адрес или null, если картинку нужно убрать (останется подпись).
     */
    public String renderWithImages(String markdown, Function<String, String> imageResolver) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        resolveImages(document, imageResolver);
        return POLICY.sanitize(renderer.render(document));
    }

    private void resolveImages(Node document, Function<String, String> imageResolver) {
        List<Image> images = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                images.add(image);
            }
        });
        for (Image image : images) {
            String url = imageResolver.apply(image.getDestination());
            if (url != null) {
                image.setDestination(url);
            } else {
                // Файла нет: вместо картинки остаётся подпись (дочерние узлы картинки).
                Node child = image.getFirstChild();
                while (child != null) {
                    Node next = child.getNext();
                    image.insertBefore(child);
                    child = next;
                }
                image.unlink();
            }
        }
    }
}
