package oneocr.cli;

import oneocr.api.OcrResult;

import java.nio.file.Path;

/**
 * Common data structures for multi-page OCR processing
 */
public class PagedOcrData {
    
    /**
     * Represents an extracted page image with metadata
     */
    public record PageImage(
        int pageNumber,
        Path imagePath,
        int width,
        int height
    ) {}
    
    /**
     * Represents OCR results for a specific page
     */
    public record PageOcrResult(
        int pageNumber,
        OcrResult ocrResult,
        PageImage pageImage
    ) {}
    
    /**
     * Represents OCR results with page context for XHTML generation
     */
    public record PagedOcrResult(
        int pageNumber,
        OcrResult ocrResult,
        String imageName,
        int imageWidth,
        int imageHeight
    ) {
        /**
         * Create from PageOcrResult
         */
        public static PagedOcrResult from(PageOcrResult pageOcrResult) {
            return new PagedOcrResult(
                pageOcrResult.pageNumber(),
                pageOcrResult.ocrResult(),
                pageOcrResult.pageImage().imagePath().getFileName().toString(),
                pageOcrResult.pageImage().width(),
                pageOcrResult.pageImage().height()
            );
        }
    }
}