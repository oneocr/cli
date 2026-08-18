package oneocr.cli;

import luvml.HtmlAttribute;
import luvx.Frag_I;
import static oneocr.cli.OcrToSemanticXHtml.*;

/**
 * DSL factory methods for OCR elements and attributes.
 * Provides clean, concise syntax for creating OCR fragments.
 * Follows the same pattern as AnnotationDSL.
 */
public class OcrDSL {
    
    // OCR element factory methods
    
    /**
     * Creates OCR root element
     */
    public static Ocr_E ocr(Frag_I<?>... fragments) {
        return new Ocr_E(fragments);
    }
    
    /**
     * Creates segment element (detected text segment: lines/cells/blocks)
     */
    public static Segment_E segment(Frag_I<?>... fragments) {
        return new Segment_E(fragments);
    }
    
    /**
     * Creates word element
     */
    public static W_E w(Frag_I<?>... fragments) {
        return new W_E(fragments);
    }
    
    // OCR attribute factory methods
    
    /**
     * Index attribute for wordsCount: i="#23"
     */
    public static HtmlAttribute i(String value) {
        return new HtmlAttribute("i", value);
    }
    
    /**
     * Confidence attribute for wordsCount: p="0.487" 
     */
    public static HtmlAttribute p(String value) {
        return new HtmlAttribute("p", value);
    }
    
    /**
     * Bounds attribute: b="398.1,106.8,436.3,106.8,435.9,126.4,435.9,126.2"
     */
    public static HtmlAttribute b(String value) {
        return new HtmlAttribute("b", value);
    }

    public static HtmlAttribute srcName(String value) {
        return new HtmlAttribute("srcName", value);
    }

    public static HtmlAttribute srcParent(String value) {
        return new HtmlAttribute("srcParent", value);
    }
    
    public static HtmlAttribute srcMD5(String value) {
        return new HtmlAttribute("srcMD5", value);
    }
    
    public static HtmlAttribute srcGDriveId(String value) {
        return new HtmlAttribute("srcGDriveId", value);
    }
    
    public static HtmlAttribute imgWidth(int width) {
        return new HtmlAttribute("imgWidth", String.valueOf(width));//easier for ai to understand
    }
    
    public static HtmlAttribute imgHeight(int height) {
        return new HtmlAttribute("imgHeight", String.valueOf(height));//easier for ai to understand
    }
    
    /**
     * Which engines produced this text: engine="oneocr" or engine="oneocr+tesseract"
     */
    public static HtmlAttribute engine(String value) {
        return new HtmlAttribute("engine", value);
    }

    public static HtmlAttribute timestamp(String value) {
        return new HtmlAttribute("timestamp", value);
    }

    public static HtmlAttribute angle(String value) {
        return new HtmlAttribute("angle", value);
    }
    
    public static HtmlAttribute ocrSegmentsCount(String value) {
        return new HtmlAttribute("ocrSegmentsCount", value);
    }
    
    public static HtmlAttribute num(String value) {
        return new HtmlAttribute("num", value);
    }
    
    public static HtmlAttribute num(int value) {
        return new HtmlAttribute("num", String.valueOf(value));
    }
    
    public static HtmlAttribute ocrWordsCount(String value) {
        return new HtmlAttribute("ocrWordsCount", value);
    }
    
    public static HtmlAttribute averageOcrConfidence(String value) {
        return new HtmlAttribute("averageConfidence", value);
    }
    
    public static HtmlAttribute highConfWordsRatio(String value) {
        return new HtmlAttribute("highConfWordsRatio", value);
    }
    
    public static HtmlAttribute mediumConfWordsRatio(String value) {
        return new HtmlAttribute("mediumConfWordsRatio", value);
    }
    
    public static HtmlAttribute lowConfWordsRatio(String value) {
        return new HtmlAttribute("lowConfWordsRatio", value);
    }
    
    public static HtmlAttribute pageNum(String value) {
        return new HtmlAttribute("pageNum", value);
    }
    
    public static HtmlAttribute pagesCount(String value) {
        return new HtmlAttribute("pagesCount", value);
    }
}