package ai.aura.api.application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;

/** Extracts bounded plain text from an explicitly uploaded HTML document without fetching resources. */
@Component
public class HtmlDocumentTextExtractor {
    public static final int MAX_EXTRACTED_CHARS = 250_000;
    public static final int MAX_NODE_COUNT = 50_000;
    public static final int MAX_NESTING_DEPTH = 64;

    public synchronized String extract(String html) {
        Parser parser = Parser.htmlParser()
                .setMaxDepth(MAX_NESTING_DEPTH)
                .setTrackErrors(0)
                .setTrackPosition(false);
        Document document = Jsoup.parse(stripLeadingBom(html), "", parser);
        ensureNodeLimit(document);

        document.select("script,style,noscript,template,svg,iframe,object,embed,canvas,form,[hidden],[aria-hidden=true],input[type=hidden]")
                .remove();
        String text = document.body().text().strip();
        if (text.isBlank()) {
            throw new InvalidDocumentException("The HTML file has no extractable text.");
        }
        if (text.length() > MAX_EXTRACTED_CHARS) {
            throw new InvalidDocumentException("HTML extracted text must be 250,000 characters or less.");
        }
        return text;
    }

    private static void ensureNodeLimit(Node root) {
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.addLast(root);
        int visitedNodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.removeLast();
            if (++visitedNodes > MAX_NODE_COUNT) {
                throw new InvalidDocumentException("HTML documents may contain up to 50,000 parser nodes.");
            }
            for (Node child : node.childNodes()) pending.addLast(child);
        }
    }

    private static String stripLeadingBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
