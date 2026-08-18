package oneocr.cli.layout;

/**
 * Thresholds for the cut. Everything vertical is expressed in <em>em</em>, meaning multiples of the
 * page's median OCR line height, so the same numbers hold at any DPI. Gutter width additionally has
 * a floor as a fraction of the region width, which is what keeps a hanging-indent list marker from
 * being mistaken for a column.
 */
public record LayoutOptions(
    double minGutterEm,
    double minGutterFrac,
    double minRowGapEm,
    double siblingGapRatio,
    double bandOverlapFrac,
    double alignTolEm,
    double tableBandFrac,
    double blankLineGapEm,
    double blockGapRatio,
    double gutterSupportFrac,
    int maxDepth
) {
    public static LayoutOptions defaults() {
        return new LayoutOptions(1.5, 0.03, 0.6, 0.6, 0.5, 0.6, 0.5, 0.9, 1.5, 0.5, 8);
    }
}
