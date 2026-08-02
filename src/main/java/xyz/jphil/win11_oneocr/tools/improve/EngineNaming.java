package xyz.jphil.win11_oneocr.tools.improve;

import java.io.File;
import xyz.jphil.win11_oneocr.tools.OcrMetadata;

/**
 * Names the second-pass outputs. An existing ".oneocr" tag is replaced rather than appended, so
 * "doc.pdf.oneocr.txt" becomes "doc.pdf.oneocr+tesseract.txt" and not "doc.pdf.oneocr.oneocr+tesseract.txt".
 */
public final class EngineNaming {

    private EngineNaming() {}

    public static String improved(String name) {
        var tag = OcrMetadata.ENGINE_ONEOCR_TESSERACT;
        var marker = "." + OcrMetadata.ENGINE_ONEOCR;
        var dot = name.lastIndexOf('.');
        var stem = dot <= 0 ? name : name.substring(0, dot);
        var ext = dot <= 0 ? "" : name.substring(dot);
        if (stem.endsWith(marker)) {
            stem = stem.substring(0, stem.length() - marker.length());
        }
        return stem + "." + tag + ext;
    }

    public static File improved(File original) {
        return new File(original.getParentFile(), improved(original.getName()));
    }
}
