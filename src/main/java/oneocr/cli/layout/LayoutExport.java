package oneocr.cli.layout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import oneocr.cli.PagedOcrData.PagedOcrResult;

/** Turns OCR results into the layout files and names them consistently with the rest of the tool. */
public final class LayoutExport {

    private LayoutExport() {}

    /** "doc.pdf.oneocr.txt" becomes "doc.pdf.oneocr.layout.txt". */
    public static String name(String source, String ext) {
        var dot = source.lastIndexOf('.');
        var stem = dot <= 0 ? source : source.substring(0, dot);
        if (stem.endsWith(".layout")) stem = stem.substring(0, stem.length() - ".layout".length());
        return stem + ".layout." + ext;
    }

    public static File sibling(File source, String ext) {
        return new File(source.getParentFile(), name(source.getName(), ext));
    }

    public static List<PageLayout> analyze(List<PagedOcrResult> pages, LayoutOptions options) {
        var out = new ArrayList<PageLayout>();
        for (var p : pages)
            out.add(PageLayout.of(p.ocrResult(), p.imageName(), p.pageNumber(),
                p.imageWidth(), p.imageHeight(), options));
        return out;
    }

    public static void write(List<PageLayout> pages, String title, Path txt, Path html, TextMode mode)
            throws IOException {
        if (txt != null) Files.writeString(txt, LayoutText.render(pages, mode));
        if (html != null) Files.writeString(html, LayoutHtml.document(pages, title));
    }
}
