package oneocr.cli.improve;

import oneocr.api.OcrResult;

/** A page as OneOCR left it. {@code ocrImageWidth} is the width the boxes are expressed in. */
public record ImprovePage(int pageNo, OcrResult result, int ocrImageWidth) {
}
