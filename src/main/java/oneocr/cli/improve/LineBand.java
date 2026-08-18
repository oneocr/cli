package oneocr.cli.improve;

import java.util.List;

/**
 * One visual line of a page: OneOCR often splits "SPEAKER :- body text" into three boxes
 * because they are spatially separated, and recognising those separately hands Tesseract
 * 41-pixel slivers. A band is those boxes merged back into the line a reader sees.
 */
public record LineBand(
    int x, int y, int w, int h,
    List<Integer> lineIndices,
    boolean suspect
) {
    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public int firstLine() {
        return lineIndices.get(0);
    }

    public LineBand asSuspect(boolean s) {
        return new LineBand(x, y, w, h, lineIndices, s);
    }
}
