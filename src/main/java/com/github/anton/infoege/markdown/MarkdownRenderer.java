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
                    .allowElements("pre", "code", "hr", "sub", "sup")
                    .toFactory());

    private final Parser parser = Parser.builder().extensions(List.of(TablesExtension.create())).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(List.of(TablesExtension.create())).build();

    public String render(String markdown, Function<String, String> fileUrlResolver) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        resolveFileImages(document, fileUrlResolver);
        return POLICY.sanitize(renderer.render(document));
    }

    private void resolveFileImages(Node document, Function<String, String> fileUrlResolver) {
        List<Image> images = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                images.add(image);
            }
        });
        for (Image image : images) {
            String destination = image.getDestination();
            if (!destination.startsWith(FILE_PREFIX)) {
                continue;
            }
            String url = fileUrlResolver.apply(destination.substring(FILE_PREFIX.length()));
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
