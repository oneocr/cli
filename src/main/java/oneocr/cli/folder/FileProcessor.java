package oneocr.cli.folder;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.*;

/**
 * Handles recursive file discovery and processing coordination for folder mode OCR
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public class FileProcessor {
    
    private final Path rootFolder;
    private final boolean recursive;
    private final boolean verbose;
    
    // Supported image formats
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp", ".gif"
    );
    
    // Supported document formats  
    private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");
    
    
    /**
     * Discover all processable files in the folder (and subfolders if recursive)
     */
    public List<Path> discoverFiles() throws IOException {
        if (verbose) {
            System.err.printf("🔍 Discovering files in %s%s%n", 
                rootFolder, recursive ? " (recursive)" : "");
        }
        
        try (Stream<Path> paths = recursive ? 
            Files.walk(rootFolder) : Files.list(rootFolder)) {
                
            return paths
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFile)
                .sorted()
                .toList();
        }
    }
    
    /**
     * Analyze discovered files and return statistics
     */
    public FileStats analyzeFiles(List<Path> files) {
        var images = (int) files.stream().filter(this::isImageFile).count();
        var pdfs = (int) files.stream().filter(this::isPdfFile).count();
        
        if (verbose) {
            System.err.printf("📊 Found %d files: %d images, %d PDFs%n", 
                files.size(), images, pdfs);
        }
        
        return FileStats.empty()
            .withTotal(files.size())
            .withImages(images)
            .withPdfs(pdfs);
    }
    
    private boolean isSupportedFile(Path file) {
        return isImageFile(file) || isPdfFile(file);
    }
    
    private boolean isImageFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
    
    private boolean isPdfFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return PDF_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
    
    /**
     * Get file type for processing logic
     */
    public FileType getFileType(Path file) {
        if (isImageFile(file)) return FileType.IMAGE;
        if (isPdfFile(file)) return FileType.PDF;
        return FileType.UNSUPPORTED;
    }
    
    public enum FileType {
        IMAGE, PDF, UNSUPPORTED
    }
}