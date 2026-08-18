package oneocr.cli.improve;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Crops a band out of the page. The margin is deliberately taken from the source image and the
 * remaining whitespace added as a white border: OneOCR's line boxes tile the text with no gap,
 * so widening the crop swallows the neighbouring lines while an exact crop clips the ai-matra
 * strokes that overflow above the box. Margins are in page-image pixels, calibrated at ~300 DPI.
 */
public final class BandCropper {

    private BandCropper() {}

    public static BufferedImage crop(BufferedImage page, LineBand band, double scale, ImproveOptions o) {
        var m = margin(scale, o);
        var x = (int) Math.round(band.x() * scale);
        var y = (int) Math.round(band.y() * scale) - m;
        var w = (int) Math.round(band.w() * scale);
        var h = (int) Math.round(band.h() * scale) + 2 * m;

        x = Math.max(0, Math.min(x, page.getWidth() - 1));
        y = Math.max(0, Math.min(y, page.getHeight() - 1));
        w = Math.max(1, Math.min(w, page.getWidth() - x));
        h = Math.max(1, Math.min(h, page.getHeight() - y));

        var sub = page.getSubimage(x, y, w, h);
        var b = o.whiteBorderPx;
        var out = new BufferedImage(w + 2 * b, h + 2 * b, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(sub, b, b, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Origin of the crop in page-image coordinates, needed to map word boxes back. */
    public static int[] origin(BufferedImage page, LineBand band, double scale, ImproveOptions o) {
        var x = (int) Math.round(band.x() * scale);
        var y = (int) Math.round(band.y() * scale) - margin(scale, o);
        x = Math.max(0, Math.min(x, page.getWidth() - 1));
        y = Math.max(0, Math.min(y, page.getHeight() - 1));
        return new int[]{x, y};
    }

    /**
     * The calibrated margin assumes boxes measured on the image being cropped. When the pass runs
     * at a higher DPI than OneOCR did, each source pixel of box quantisation becomes {@code scale}
     * pixels of error here, which would otherwise consume the whole allowance and clip matras.
     */
    static int margin(double scale, ImproveOptions o) {
        return o.verticalMarginPx + (scale > 1.01 ? (int) Math.ceil(scale) : 0);
    }
}
