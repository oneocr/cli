package oneocr.cli.layout;

/** How side by side columns are written out as plain text. */
public enum TextMode {
    /** Columns kept side by side, separated by a tab. Looks like the page; one row per line. */
    COLUMNS,
    /** Columns kept side by side and padded with spaces so they line up in a fixed width editor. */
    ALIGNED,
    /** Each column written out in full before the next. Loses the visual pairing, keeps the sense. */
    READING
}
