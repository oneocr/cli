package xyz.jphil.win11_oneocr.tools.improve;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Supplies the page image for the second pass. Implementations may re-render at a higher DPI
 * than OneOCR used; the improver derives the scale factor from the returned width.
 */
@FunctionalInterface
public interface PageImageSupplier {
    BufferedImage image(int pageNo) throws IOException;
}
