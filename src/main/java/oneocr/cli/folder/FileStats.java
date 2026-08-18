package oneocr.cli.folder;
public record FileStats(int totalFiles, int imageFiles, int pdfFiles, int processedFiles, int errorFiles) {
    public static FileStats empty() {
        return new FileStats(0, 0, 0, 0, 0);
    }

    public FileStats withTotal(int total) {
        return new FileStats(total, imageFiles, pdfFiles, processedFiles, errorFiles);
    }

    public FileStats withImages(int images) {
        return new FileStats(totalFiles, images, pdfFiles, processedFiles, errorFiles);
    }

    public FileStats withPdfs(int pdfs) {
        return new FileStats(totalFiles, imageFiles, pdfs, processedFiles, errorFiles);
    }

    public FileStats incProcessed() {
        return new FileStats(totalFiles, imageFiles, pdfFiles, processedFiles + 1, errorFiles);
    }

    public FileStats incErrors() {
        return new FileStats(totalFiles, imageFiles, pdfFiles, processedFiles, errorFiles + 1);
    }
}

