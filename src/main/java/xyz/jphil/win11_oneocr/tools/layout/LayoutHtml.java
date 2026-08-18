package xyz.jphil.win11_oneocr.tools.layout;

import java.util.ArrayList;
import java.util.List;
import luvml.DocType;
import luvml.E;
import luvx.Frag_I;
import luvml.o.XHtmlStringRenderer;
import xyz.jphil.win11_oneocr.tools.Resources;
import static luvml.A.*;
import static luvml.E.*;
import static luvml.Frags.frags;
import static luvml.ProcessingInstruction.xmlDeclaration;

/**
 * A standalone, self-contained HTML rendering of the recovered page layout. This is the reading
 * copy: no bounding boxes, no confidences, no viewer script - just the text arranged the way it sits
 * on the page. The bounds-carrying record stays in the semantic XHTML output.
 */
public final class LayoutHtml {

    public static final String CSS_RESOURCE = "layout/layout.css";

    private LayoutHtml() {}

    public static String document(List<PageLayout> pages, String title) {
        var sections = new ArrayList<Frag_I<?>>();
        for (var page : pages) sections.add(section(page, pages.size() > 1));

        var doc = frags(
            xmlDeclaration("UTF-8"),
            DocType.html5(),
            html(xmlns("http://www.w3.org/1999/xhtml"),
                head(
                    meta(charset("UTF-8")),
                    meta(name("viewport"), content("width=device-width, initial-scale=1.0")),
                    meta(name("generator"), content("win11_oneocr layout export")),
                    E.title(title + " (layout)"),
                    E.style(Resources.text(CSS_RESOURCE))
                ),
                body(article(class_("ocrLayout"), frags(sections)))
            )
        );
        return XHtmlStringRenderer.asFormatted(doc, "");
    }

    private static Frag_I<?> section(PageLayout page, boolean showPageMark) {
        var content = new LayoutHtmlBody(page).node(page.root(), page.textBounds());
        var parts = new ArrayList<Frag_I<?>>();
        if (showPageMark) parts.add(div(class_("pageMark"), text("Page " + page.pageNumber())));
        parts.add(content);
        return E.section(class_("page"),
            data("page", String.valueOf(page.pageNumber())),
            data("src", page.name() == null ? "" : page.name()),
            data("size", page.width() + "x" + page.height()),
            frags(parts));
    }
}
