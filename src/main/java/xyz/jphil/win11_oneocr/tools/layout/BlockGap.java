package xyz.jphil.win11_oneocr.tools.layout;

/**
 * Decides how much vertical space counts as a paragraph break.
 *
 * <p>Measured against the leading of the rows inside the region itself. One page routinely carries
 * both a single spaced indented quotation and double spaced body text, and any single page-wide
 * number gets one of the two wrong - double spacing the body, or running the quotation together.
 * The children's own gaps are no use here either: the cut has already normalised those.
 */
public final class BlockGap {

    private BlockGap() {}

    public static double forNode(LayoutNode node, LayoutOptions o, double em) {
        var median = RowBands.medianGap(node.boxes(), o.bandOverlapFrac());
        return Math.max(o.blockGapRatio() * median, o.blankLineGapEm() * em);
    }
}
