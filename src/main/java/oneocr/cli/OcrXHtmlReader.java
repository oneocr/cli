package oneocr.cli;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import oneocr.api.*;
import static oneocr.api.OcrWord.ocrWord;
import static oneocr.cli.PagedOcrData.PagedOcrResult;

/**
 * Reads the semantic XHTML back into OCR records. Every page already written to disk carries its
 * boxes, so anything that works off geometry can be re-run over an existing document without
 * touching the OCR engine again.
 */
public final class OcrXHtmlReader {

    private OcrXHtmlReader() {}

    public static List<PagedOcrResult> read(String xhtml) {
        var doc = Jsoup.parse(xhtml, "", Parser.xmlParser());
        var pages = new ArrayList<PagedOcrResult>();
        var sections = doc.select("section[class=win11OneOcrPage]");
        for (var i = 0; i < sections.size(); i++) {
            var sec = sections.get(i);
            pages.add(new PagedOcrResult(
                intAttr(sec, "pageNum", i + 1),
                result(sec),
                sec.attr("srcName"),
                intAttr(sec, "imgWidth", 0),
                intAttr(sec, "imgHeight", 0)));
        }
        return pages;
    }

    private static OcrResult result(Element section) {
        var lines = new ArrayList<OcrLine>();
        for (var seg : section.select("segment")) {
            var words = new ArrayList<OcrWord>();
            for (var w : seg.select("w")) {
                var confidence = 0.0;
                try { confidence = Double.parseDouble(w.attr("p")); } catch (NumberFormatException ignored) { }
                words.add(ocrWord(w.text(), bounds(w.attr("b")), confidence));
            }
            var text = String.join(" ", words.stream().map(OcrWord::text).toList());
            lines.add(new OcrLine(text, bounds(seg.attr("b")), words));
        }
        var angle = 0.0;
        try { angle = Double.parseDouble(section.attr("angle")); } catch (NumberFormatException ignored) { }
        var text = String.join("\n", lines.stream().map(OcrLine::text).toList());
        return new OcrResult(text, angle, lines);
    }

    private static BoundingBox bounds(String value) {
        if (value == null || value.isBlank()) return null;
        var parts = value.split(",");
        if (parts.length < 8) return null;
        var v = new double[8];
        for (var i = 0; i < 8; i++) {
            try { v[i] = Double.parseDouble(parts[i].trim()); } catch (NumberFormatException e) { return null; }
        }
        return new BoundingBox(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
    }

    private static int intAttr(Element e, String name, int fallback) {
        try { return Integer.parseInt(e.attr(name)); } catch (NumberFormatException ex) { return fallback; }
    }
}
