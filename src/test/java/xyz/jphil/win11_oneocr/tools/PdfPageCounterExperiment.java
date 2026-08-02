///usr/bin/env java "$0" "$@" ; exit $?
// Standalone Java code (not part of main project) - replaces bash/python/batch scripts with IDE-friendly, maintainable code using JDK 11/21/25 enhancements. To know why, refer to Cay Horstmann's JavaOne 2025 talk "Java for Small Coding Tasks" (https://youtu.be/04wFgshWMdA)

package xyz.jphil.win11_oneocr.tools;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Experimental tool for efficiently counting PDF pages using PDFBox random access API.
 * Designed to handle large folders (115GB+) by reading only metadata, not full content.
 * 
 * This is an EXPERIMENT to verify if PDFBox random access API can efficiently process
 * very large PDF collections by reading minimal data.
 * 
 * Usage: 
 *   java PdfPageCounterExperiment.java /path/to/folder
 *   java PdfPageCounterExperiment.java --single /path/to/single/file.pdf
 */
public class PdfPageCounterExperiment {

    /**
     * Diagnostic wrapper for RandomAccessRead that tracks actual bytes read.
     * This is the core of our experiment - to see how much data PDFBox actually reads
     * when we only want page count metadata.
     */
    private static class DiagnosticRandomAccessRead implements org.apache.pdfbox.io.RandomAccessRead {
        private final org.apache.pdfbox.io.RandomAccessRead delegate;
        private long totalBytesRead = 0;
        private long totalSeekOperations = 0;
        private long maxPosition = 0;

        public DiagnosticRandomAccessRead(org.apache.pdfbox.io.RandomAccessRead delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void seek(long position) throws IOException {
            totalSeekOperations++;
            maxPosition = Math.max(maxPosition, position);
            delegate.seek(position);
        }

        @Override
        public long getPosition() throws IOException {
            return delegate.getPosition();
        }

        @Override
        public int read() throws IOException {
            int result = delegate.read();
            if (result != -1) {
                totalBytesRead++;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = delegate.read(b, off, len);
            if (bytesRead > 0) {
                totalBytesRead += bytesRead;
            }
            return bytesRead;
        }

        @Override
        public long length() throws IOException {
            return delegate.length();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public boolean isEOF() throws IOException {
            return delegate.isEOF();
        }

        @Override
        public int peek() throws IOException {
            return delegate.peek();
        }

        @Override
        public void rewind(int bytes) throws IOException {
            delegate.rewind(bytes);
        }

        @Override
        public org.apache.pdfbox.io.RandomAccessReadView createView(long startPosition, long streamLength) throws IOException {
            return delegate.createView(startPosition, streamLength);
        }

        public long getTotalBytesRead() {
            return totalBytesRead;
        }

        public long getTotalSeekOperations() {
            return totalSeekOperations;
        }

        public long getMaxPosition() {
            return maxPosition;
        }
    }

    /**
     * Result class containing diagnostic information about PDF processing
     */
    private static class PdfDiagnosticResult {
        final int pageCount;
        final long bytesRead;
        final long seekOperations;
        final long maxPosition;

        PdfDiagnosticResult(int pageCount, long bytesRead, long seekOperations, long maxPosition) {
            this.pageCount = pageCount;
            this.bytesRead = bytesRead;
            this.seekOperations = seekOperations;
            this.maxPosition = maxPosition;
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("PDF Page Counter Experiment");
            System.out.println("Usage:");
            System.out.println("  java PdfPageCounterExperiment.java /path/to/folder      - Count all PDFs recursively");
            System.out.println("  java PdfPageCounterExperiment.java --single file.pdf   - Analyze single PDF");
            System.exit(1);
        }

        if (args.length == 2 && "--single".equals(args[0])) {
            analyzeSinglePdf(args[1]);
        } else {
            analyzeFolder(args[0]);
        }
    }

    /**
     * Analyze all PDFs in a folder recursively
     */
    private static void analyzeFolder(String folderPath) {
        Path rootPath = Paths.get(folderPath);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            System.err.println("Invalid folder path: " + folderPath);
            System.exit(1);
        }

        System.out.println("PDF Page Counter Experiment - Folder Analysis");
        System.out.println("=".repeat(60));
        System.out.println("Scanning PDF files in: " + rootPath);
        System.out.println("Starting recursive PDF page count with READ DIAGNOSTICS...\n");

        Instant startTime = Instant.now();
        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicLong totalPages = new AtomicLong(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicLong totalBytesActuallyRead = new AtomicLong(0);

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                    .forEach(pdfPath -> {
                        try {
                            long fileSize = Files.size(pdfPath);
                            int currentFileCount = totalFiles.incrementAndGet();
                            
                            System.out.printf("[%d] Processing: %s (%.2f MB)... ", 
                                currentFileCount,
                                pdfPath.getFileName(),
                                fileSize / (1024.0 * 1024.0));
                            System.out.flush();
                            
                            Instant fileStartTime = Instant.now();
                            PdfDiagnosticResult result = countPagesWithDiagnostics(pdfPath);
                            Instant fileEndTime = Instant.now();
                            long processingTimeMs = Duration.between(fileStartTime, fileEndTime).toMillis();
                            
                            totalPages.addAndGet(result.pageCount);
                            totalBytes.addAndGet(fileSize);
                            totalBytesActuallyRead.addAndGet(result.bytesRead);
                            
                            double efficiencyPercent = (result.bytesRead * 100.0) / fileSize;
                            
                            double seekRatio = result.bytesRead > 0 ? result.seekOperations / (double)result.bytesRead : 0;
                            
                            System.out.printf("✓ %d pages, read %.1f KB of %.2f MB (%.4f%%), %d seeks (%.1f seeks/byte), %dms%n", 
                                result.pageCount,
                                result.bytesRead / 1024.0,
                                fileSize / (1024.0 * 1024.0),
                                efficiencyPercent,
                                result.seekOperations,
                                seekRatio,
                                processingTimeMs);
                            
                            if (result.seekOperations > 1000) {
                                System.out.printf("    ⚠️  HIGH SEEK COUNT - Network storage will be very slow!%n");
                            }
                                
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            System.err.printf("✗ ERROR: %s%n", e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }

        Instant endTime = Instant.now();
        Duration elapsed = Duration.between(startTime, endTime);

        // Print summary
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EXPERIMENT RESULTS SUMMARY:");
        System.out.println("=".repeat(80));
        System.out.printf("Total PDF files processed: %,d%n", totalFiles.get());
        System.out.printf("Total pages: %,d%n", totalPages.get());
        System.out.printf("Total file size: %.2f GB%n", totalBytes.get() / (1024.0 * 1024.0 * 1024.0));
        System.out.printf("Total bytes actually read: %.2f MB%n", totalBytesActuallyRead.get() / (1024.0 * 1024.0));
        System.out.printf("Read efficiency: %.4f%% (read only %.1fMB of %.1fGB total)%n", 
            (totalBytesActuallyRead.get() * 100.0) / totalBytes.get(),
            totalBytesActuallyRead.get() / (1024.0 * 1024.0),
            totalBytes.get() / (1024.0 * 1024.0 * 1024.0));
        System.out.printf("Errors encountered: %,d%n", errorCount.get());
        System.out.printf("Processing time: %d minutes %d seconds%n", 
            elapsed.toMinutes(), elapsed.toSecondsPart());
        
        if (totalFiles.get() > 0) {
            System.out.printf("Average pages per file: %.1f%n", 
                totalPages.get() / (double) totalFiles.get());
            System.out.printf("Processing rate: %.1f files/second%n", 
                totalFiles.get() / (elapsed.toMillis() / 1000.0));
            System.out.printf("Average bytes read per file: %.1f KB%n",
                totalBytesActuallyRead.get() / (1024.0 * totalFiles.get()));
        }

        // Experimental conclusion
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EXPERIMENTAL CONCLUSION:");
        System.out.println("=".repeat(80));
        double efficiency = (totalBytesActuallyRead.get() * 100.0) / totalBytes.get();
        if (efficiency < 1.0) {
            System.out.println("✅ EXPERIMENT SUCCESS: PDFBox random access is HIGHLY EFFICIENT!");
            System.out.printf("   Only %.4f%% of total data was read to get all page counts.%n", efficiency);
            System.out.println("   This approach is practical for very large PDF collections.");
        } else if (efficiency < 10.0) {
            System.out.println("✅ EXPERIMENT PARTIAL SUCCESS: PDFBox random access is reasonably efficient.");
            System.out.printf("   %.2f%% of total data was read to get all page counts.%n", efficiency);
            System.out.println("   This approach should work for large PDF collections.");
        } else {
            System.out.println("⚠️  EXPERIMENT MIXED RESULTS: PDFBox random access reads significant data.");
            System.out.printf("   %.2f%% of total data was read to get all page counts.%n", efficiency);
            System.out.println("   Consider memory/performance implications for very large collections.");
        }
    }

    /**
     * Analyze a single PDF file with detailed diagnostics
     */
    private static void analyzeSinglePdf(String filePath) {
        Path pdfPath = Paths.get(filePath);
        if (!Files.exists(pdfPath)) {
            System.err.println("PDF file not found: " + filePath);
            System.exit(1);
        }
        
        System.out.println("PDF Page Counter Experiment - Single File Analysis");
        System.out.println("=".repeat(60));
        
        try {
            Instant start = Instant.now();
            PdfDiagnosticResult result = countPagesWithDiagnostics(pdfPath);
            Instant end = Instant.now();
            
            long fileSize = Files.size(pdfPath);
            double efficiencyPercent = (result.bytesRead * 100.0) / fileSize;
            
            System.out.printf("PDF: %s%n", pdfPath.getFileName());
            System.out.printf("File size: %.2f MB (%,d bytes)%n", fileSize / (1024.0 * 1024.0), fileSize);
            System.out.printf("Pages: %d%n", result.pageCount);
            System.out.printf("Processing time: %d ms%n", Duration.between(start, end).toMillis());
            System.out.println();
            System.out.println("READ EFFICIENCY ANALYSIS:");
            System.out.printf("Bytes actually read: %,d (%.1f KB)%n", result.bytesRead, result.bytesRead / 1024.0);
            System.out.printf("Read efficiency: %.4f%% (only %.4f%% of file was read)%n", efficiencyPercent, efficiencyPercent);
            System.out.printf("Seek operations: %,d%n", result.seekOperations);
            System.out.printf("Maximum file position accessed: %,d (%.1f KB)%n", result.maxPosition, result.maxPosition / 1024.0);
            System.out.printf("Data savings: %.2f MB (%.1f%% of file not read)%n", 
                (fileSize - result.bytesRead) / (1024.0 * 1024.0), 
                100.0 - efficiencyPercent);
            
        } catch (IOException e) {
            System.err.println("Error processing PDF: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Counts pages with detailed diagnostics about how much data was actually read.
     * This is the core experimental method using random access.
     */
    private static PdfDiagnosticResult countPagesWithDiagnostics(Path pdfPath) throws IOException {
        // Use Loader with our diagnostic wrapper
        try (var baseRead = new org.apache.pdfbox.io.RandomAccessReadBuffer(Files.newInputStream(pdfPath))) {
            DiagnosticRandomAccessRead diagnosticRead = new DiagnosticRandomAccessRead(baseRead);
            
            try (var document = org.apache.pdfbox.Loader.loadPDF(diagnosticRead)) {
                int pageCount = document.getNumberOfPages();
                
                return new PdfDiagnosticResult(
                    pageCount,
                    diagnosticRead.getTotalBytesRead(),
                    diagnosticRead.getTotalSeekOperations(),
                    diagnosticRead.getMaxPosition()
                );
            }
        }
    }
}