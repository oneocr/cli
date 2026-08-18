package xyz.jphil.win11_oneocr.tools.layout;

import java.util.Locale;
import picocli.CommandLine.Option;

/**
 * Shared flags for the layout-aware exports. Off by default: the existing .txt and .xhtml keep the
 * engine's own line order so nothing downstream shifts under anyone.
 */
public class LayoutCliOptions {

    @Option(names = {"--layout"},
        description = "Recover the page layout and write a parallel *.layout.txt and *.layout.html. "
            + "Lines that sit side by side on the page stay side by side instead of being "
            + "interleaved down one column.")
    public boolean layout;

    @Option(names = {"--layout-text"}, defaultValue = "columns",
        description = "How columns are written to the layout text: columns (tab separated), "
            + "aligned (space padded so they line up in a fixed width editor), or reading "
            + "(one column in full, then the next). Default: ${DEFAULT-VALUE}.")
    public String layoutText;

    @Option(names = {"--no-layout-html"}, description = "Skip the .layout.html, write only the text.")
    public boolean noLayoutHtml;

    @Option(names = {"--layout-min-gutter"}, defaultValue = "1.5",
        description = "Whitespace between columns, in multiples of the median line height, before it "
            + "counts as a column split (default: ${DEFAULT-VALUE}).")
    public double minGutterEm;

    @Option(names = {"--layout-min-row-gap"}, defaultValue = "0.6",
        description = "Whitespace between blocks, in multiples of the median line height, before it "
            + "counts as a block break (default: ${DEFAULT-VALUE}).")
    public double minRowGapEm;

    public boolean enabled() { return layout; }

    public TextMode mode() {
        var name = layoutText == null ? "" : layoutText.trim().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "ALIGNED" -> TextMode.ALIGNED;
            case "READING" -> TextMode.READING;
            default -> TextMode.COLUMNS;
        };
    }

    public LayoutOptions options() {
        var d = LayoutOptions.defaults();
        return new LayoutOptions(minGutterEm, d.minGutterFrac(), minRowGapEm, d.siblingGapRatio(),
            d.bandOverlapFrac(), d.alignTolEm(), d.tableBandFrac(), d.blankLineGapEm(),
            d.blockGapRatio(), d.gutterSupportFrac(), d.maxDepth());
    }
}
