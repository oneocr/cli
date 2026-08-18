package oneocr.cli.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.File;
import java.nio.file.Files;

/**
 * Utility for extracting basic PDF information
 */
public class PdfInfoUtil {
    
    /**
     * PDF information record with page count and first page dimensions
     * pg0Width/pg0Height are -1 if PDF has no pages or measurement fails
     */
    public record PdfInfo(int pageCount, long fileSize, double pg0Width, double pg0Height) {
        
        /**
         * Calculate average file size budget per page
         * @return average bytes per page, or 0 if no pages
         */
        public long perPageAverageSize() {
            return pageCount > 0 ? fileSize / pageCount : 0;
        }
    }
    
    /**
     * Extract PDF page count and first page dimensions
     * @param pdfFile PDF file to analyze
     * @return PdfInfo with page count and first page dimensions (in points, 1/72 inch)
     */
    public static PdfInfo getPdfInfo(File pdfFile) throws Exception {
        long fileSize = -1;
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageCount = document.getNumberOfPages();
            fileSize = Files.size(pdfFile.toPath());
            if (pageCount == 0) {
                return new PdfInfo(0, fileSize, -1, -1);
            }
            
            try {
                // Get dimensions from first page (page 0)
                var page = document.getPage(0);
                var mediaBox = page.getMediaBox();
                double width = mediaBox.getWidth();   // in points (1/72 inch)
                double height = mediaBox.getHeight(); // in points (1/72 inch)
                
                return new PdfInfo(pageCount,fileSize,width, height);
            } catch (Exception e) {
                // Error measuring page dimensions - return -1 for dimensions
                return new PdfInfo(pageCount,fileSize,-1,-1);
            }
        }
    }
    
    /**
     * Mathematical DPI calculation for preview images based on page dimensions and file size constraints
     * Uses actual page dimensions from PDF to calculate optimal DPI that fits within size budget
     */
    public static int calculateSimpleDpi(PdfInfo pdfInfo, boolean verbose) {
        final int DEFAULT_MAX_DPI = 100;
        final int MIN_DPI = 75;
        
        // Handle edge cases
        if (pdfInfo.pg0Width() <= 0 || pdfInfo.pg0Height() <= 0 || pdfInfo.pageCount() <= 0) {
            return DEFAULT_MAX_DPI; // Fallback if dimensions unavailable
        }
        
        // Convert points to inches (1 point = 1/72 inch)
        double pageWidthInches = pdfInfo.pg0Width() / 72.0;
        double pageHeightInches = pdfInfo.pg0Height() / 72.0;
        
        // Calculate per-page size budget
        long maxBytesPerPage = pdfInfo.perPageAverageSize();
        
        // Mathematical relationship for image size:
        // pixels = (widthInches * dpi) * (heightInches * dpi) = pageArea * dpi²
        // WebP bytes ≈ pixels * 3 (RGB) * compressionRatio
        double pageAreaSquareInches = pageWidthInches * pageHeightInches;
        double webpCompressionRatio = 0.5; // Conservative WebP compression estimate
        
        // Solve for maximum DPI that fits budget:
        // maxBytesPerPage >= pageAreaSquareInches * dpi² * 3 * compressionRatio
        // dpi² <= maxBytesPerPage / (pageAreaSquareInches * 3 * compressionRatio)
        double maxDpiSquared = maxBytesPerPage / (pageAreaSquareInches * 3.0 * webpCompressionRatio);
        int calculatedDpi = (int) Math.sqrt(maxDpiSquared);
        
        // Apply bounds: minimum for readability, maximum for performance
        int finalDpi = Math.max(MIN_DPI, Math.min(DEFAULT_MAX_DPI, calculatedDpi));
        
        if (verbose) {
            System.err.printf("DPI calculation: page=%.1fx%.1f inches, budget=%s/page, calculated=%d, final=%d%n",
                pageWidthInches, pageHeightInches, formatBytes(maxBytesPerPage), calculatedDpi, finalDpi);
        }
        
        return finalDpi;
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}