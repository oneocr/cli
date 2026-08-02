package xyz.jphil.win11_oneocr.tools.improve;

import xyz.jphil.win11_oneocr.OcrResult;

/** A page as OneOCR left it. {@code ocrImageWidth} is the width the boxes are expressed in. */
public record ImprovePage(int pageNo, OcrResult result, int ocrImageWidth) {
}
