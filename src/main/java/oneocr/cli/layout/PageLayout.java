package oneocr.cli.layout;

import java.util.List;
import oneocr.api.OcrResult;

/** One analysed page: the cut tree plus the measurements every renderer needs. */
public record PageLayout(
    String name, int pageNumber, int width, int height,
    LayoutNode root, Rect textBounds, double em, double rowGap, LayoutOptions options
) {
    public static PageLayout of(OcrResult result, String name, int pageNumber,
                                int width, int height, LayoutOptions options) {
        var boxes = TextBox.from(result);
        var bounds = boxes.isEmpty()
            ? new Rect(0, 0, Math.max(1, width), Math.max(1, height))
            : Rect.unionOf(boxes.stream().map(TextBox::rect).toList());
        return new PageLayout(name, pageNumber, width, height,
            XyCut.analyze(boxes, options), bounds, RowBands.em(boxes),
            RowBands.medianGap(boxes, options.bandOverlapFrac()), options);
    }

    public static List<PageLayout> single(OcrResult result, String name, int width, int height, LayoutOptions o) {
        return List.of(of(result, name, 1, width, height, o));
    }
}
