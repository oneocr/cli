package xyz.jphil.win11_oneocr.tools.ui;

import xyz.jphil.win11_oneocr.OneOcrApi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Objects;

/**
 * Manages execution history for the UI command
 * Saves and loads execution configurations to/from JSON files
 */
public class ExecutionHistory {
    
    private static final String HISTORY_FILE = "execution_history.json";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final Path historyFile;
    
    public ExecutionHistory() throws IOException {
        Path userDir = OneOcrApi.getAppHome();
        Files.createDirectories(userDir);
        this.historyFile = userDir.resolve(HISTORY_FILE);
    }
    
    /**
     * Execution record for different types of OCR operations
     */
    public static abstract class ExecutionRecord {
        public final String timestamp;
        public final String type;
        
        public ExecutionRecord(String type) {
            this.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            this.type = type;
        }
        
        public abstract String getDisplayName();
        public abstract Map<String, Object> toMap();
        public static ExecutionRecord fromMap(Map<String, Object> map) {
            String type = (String) map.get("type");
            switch (type) {
                case "folder":
                    return FolderExecutionRecord.fromMap(map);
                case "pdf":
                    return PdfExecutionRecord.fromMap(map);
                case "image":
                    return ImageExecutionRecord.fromMap(map);
                default:
                    throw new IllegalArgumentException("Unknown execution type: " + type);
            }
        }
    }
    
    /**
     * Folder processing execution record
     */
    public static class FolderExecutionRecord extends ExecutionRecord {
        public final String inputFolder;
        public final String outputFolder;
        public final int threads;
        public final boolean recursive;
        public final boolean verbose;
        public final boolean generateSvg;
        
        public FolderExecutionRecord(String inputFolder, String outputFolder, int threads, 
                                   boolean recursive, boolean verbose, boolean generateSvg) {
            super("folder");
            this.inputFolder = inputFolder;
            this.outputFolder = outputFolder;
            this.threads = threads;
            this.recursive = recursive;
            this.verbose = verbose;
            this.generateSvg = generateSvg;
        }
        
        @Override
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("<html><b>Folder Processing</b> - %s<br/>", timestamp));
            sb.append(String.format("Input: %s<br/>", inputFolder));
            if (outputFolder != null && !outputFolder.isEmpty()) {
                sb.append(String.format("Output: %s<br/>", outputFolder));
            }
            sb.append(String.format("Config: %d threads%s%s%s</html>", 
                threads,
                recursive ? ", recursive" : "",
                generateSvg ? ", SVG" : "",
                verbose ? ", verbose" : ""));
            return sb.toString();
        }
        
        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", timestamp);
            map.put("type", type);
            map.put("inputFolder", inputFolder);
            map.put("outputFolder", outputFolder);
            map.put("threads", threads);
            map.put("recursive", recursive);
            map.put("verbose", verbose);
            map.put("generateSvg", generateSvg);
            return map;
        }
        
        public static FolderExecutionRecord fromMap(Map<String, Object> map) {
            return new FolderExecutionRecord(
                (String) map.get("inputFolder"),
                (String) map.get("outputFolder"),
                ((Number) map.get("threads")).intValue(),
                (Boolean) map.get("recursive"),
                (Boolean) map.get("verbose"),
                (Boolean) map.get("generateSvg")
            );
        }
    }
    
    /**
     * PDF processing execution record
     */
    public static class PdfExecutionRecord extends ExecutionRecord {
        public final String pdfFile;
        public final String outputDir;
        public final int threads;
        public final String imageFormat;
        public final Integer targetDpi;
        public final boolean verbose;
        
        public PdfExecutionRecord(String pdfFile, String outputDir, int threads, 
                                String imageFormat, Integer targetDpi, boolean verbose) {
            super("pdf");
            this.pdfFile = pdfFile;
            this.outputDir = outputDir;
            this.threads = threads;
            this.imageFormat = imageFormat;
            this.targetDpi = targetDpi;
            this.verbose = verbose;
        }
        
        @Override
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("<html><b>PDF Processing</b> - %s<br/>", timestamp));
            sb.append(String.format("Input: %s<br/>", pdfFile));
            if (outputDir != null && !outputDir.isEmpty()) {
                sb.append(String.format("Output: %s<br/>", outputDir));
            }
            sb.append(String.format("Config: %s format", imageFormat));
            if (targetDpi != null) {
                sb.append(String.format(", %d DPI", targetDpi));
            }
            sb.append(String.format(", %d threads%s</html>", 
                threads,
                verbose ? ", verbose" : ""));
            return sb.toString();
        }
        
        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", timestamp);
            map.put("type", type);
            map.put("pdfFile", pdfFile);
            map.put("outputDir", outputDir);
            map.put("threads", threads);
            map.put("imageFormat", imageFormat);
            map.put("targetDpi", targetDpi);
            map.put("verbose", verbose);
            return map;
        }
        
        public static PdfExecutionRecord fromMap(Map<String, Object> map) {
            Object targetDpiObj = map.get("targetDpi");
            Integer targetDpi = targetDpiObj != null ? ((Number) targetDpiObj).intValue() : null;
            
            return new PdfExecutionRecord(
                (String) map.get("pdfFile"),
                (String) map.get("outputDir"),
                ((Number) map.get("threads")).intValue(),
                (String) map.get("imageFormat"),
                targetDpi,
                (Boolean) map.get("verbose")
            );
        }
    }
    
    /**
     * Image processing execution record
     */
    public static class ImageExecutionRecord extends ExecutionRecord {
        public final String imageFile;
        public final boolean generateSvg;
        public final boolean generateJson;
        public final boolean generateXhtml;
        public final boolean verbose;
        
        public ImageExecutionRecord(String imageFile, boolean generateSvg, boolean generateJson, 
                                  boolean generateXhtml, boolean verbose) {
            super("image");
            this.imageFile = imageFile;
            this.generateSvg = generateSvg;
            this.generateJson = generateJson;
            this.generateXhtml = generateXhtml;
            this.verbose = verbose;
        }
        
        @Override
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("<html><b>Image Processing</b> - %s<br/>", timestamp));
            sb.append(String.format("Input: %s<br/>", imageFile));
            
            List<String> outputs = new ArrayList<>();
            if (generateSvg) outputs.add("SVG");
            if (generateJson) outputs.add("JSON");
            if (generateXhtml) outputs.add("XHTML");
            
            sb.append("Config: ");
            if (outputs.isEmpty()) {
                sb.append("text only");
            } else {
                sb.append(String.join(", ", outputs));
            }
            if (verbose) {
                sb.append(", verbose");
            }
            sb.append("</html>");
            return sb.toString();
        }
        
        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", timestamp);
            map.put("type", type);
            map.put("imageFile", imageFile);
            map.put("generateSvg", generateSvg);
            map.put("generateJson", generateJson);
            map.put("generateXhtml", generateXhtml);
            map.put("verbose", verbose);
            return map;
        }
        
        public static ImageExecutionRecord fromMap(Map<String, Object> map) {
            return new ImageExecutionRecord(
                (String) map.get("imageFile"),
                (Boolean) map.get("generateSvg"),
                (Boolean) map.get("generateJson"),
                (Boolean) map.get("generateXhtml"),
                (Boolean) map.get("verbose")
            );
        }
    }
    
    /**
     * Add an execution record to the history
     * Prevents duplicate configurations from being added
     */
    public void addRecord(ExecutionRecord record) throws IOException {
        List<ExecutionRecord> history = loadHistory();
        
        // Check if this exact configuration already exists (excluding timestamp)
        boolean isDuplicate = history.stream().anyMatch(existing -> 
            areConfigurationsEqual(existing, record));
        
        if (!isDuplicate) {
            history.add(0, record); // Add to the beginning (most recent first)
            
            // Keep only the last 50 records
            if (history.size() > 50) {
                history = history.subList(0, 50);
            }
            
            saveHistory(history);
        }
    }
    
    /**
     * Check if two execution records have the same configuration (ignoring timestamp)
     */
    private boolean areConfigurationsEqual(ExecutionRecord record1, ExecutionRecord record2) {
        if (!record1.type.equals(record2.type)) {
            return false;
        }
        
        if (record1 instanceof FolderExecutionRecord && record2 instanceof FolderExecutionRecord) {
            FolderExecutionRecord f1 = (FolderExecutionRecord) record1;
            FolderExecutionRecord f2 = (FolderExecutionRecord) record2;
            return Objects.equals(f1.inputFolder, f2.inputFolder) &&
                   Objects.equals(f1.outputFolder, f2.outputFolder) &&
                   f1.threads == f2.threads &&
                   f1.recursive == f2.recursive &&
                   f1.verbose == f2.verbose &&
                   f1.generateSvg == f2.generateSvg;
        }
        
        if (record1 instanceof PdfExecutionRecord && record2 instanceof PdfExecutionRecord) {
            PdfExecutionRecord p1 = (PdfExecutionRecord) record1;
            PdfExecutionRecord p2 = (PdfExecutionRecord) record2;
            return Objects.equals(p1.pdfFile, p2.pdfFile) &&
                   Objects.equals(p1.outputDir, p2.outputDir) &&
                   p1.threads == p2.threads &&
                   Objects.equals(p1.imageFormat, p2.imageFormat) &&
                   Objects.equals(p1.targetDpi, p2.targetDpi) &&
                   p1.verbose == p2.verbose;
        }
        
        if (record1 instanceof ImageExecutionRecord && record2 instanceof ImageExecutionRecord) {
            ImageExecutionRecord i1 = (ImageExecutionRecord) record1;
            ImageExecutionRecord i2 = (ImageExecutionRecord) record2;
            return Objects.equals(i1.imageFile, i2.imageFile) &&
                   i1.generateSvg == i2.generateSvg &&
                   i1.generateJson == i2.generateJson &&
                   i1.generateXhtml == i2.generateXhtml &&
                   i1.verbose == i2.verbose;
        }
        
        return false;
    }
    
    /**
     * Load execution history from JSON file
     */
    public List<ExecutionRecord> loadHistory() throws IOException {
        if (!Files.exists(historyFile)) {
            return new ArrayList<>();
        }
        
        try {
            String json = Files.readString(historyFile);
            return parseHistoryJson(json);
        } catch (Exception e) {
            // If file is corrupted, start fresh
            return new ArrayList<>();
        }
    }
    
    /**
     * Save execution history to JSON file
     */
    private void saveHistory(List<ExecutionRecord> history) throws IOException {
        String json = toHistoryJson(history);
        Files.writeString(historyFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    /**
     * Clear all execution history
     */
    public void clearHistory() throws IOException {
        Files.deleteIfExists(historyFile);
    }
    
    /**
     * Simple JSON serialization (avoiding external dependencies)
     */
    private String toHistoryJson(List<ExecutionRecord> history) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) json.append(",\n");
            json.append("  ").append(toRecordJson(history.get(i)));
        }
        
        json.append("\n]");
        return json.toString();
    }
    
    private String toRecordJson(ExecutionRecord record) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        Map<String, Object> map = record.toMap();
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (count > 0) json.append(",\n");
            json.append("    \"").append(entry.getKey()).append("\": ");
            json.append(valueToJson(entry.getValue()));
            count++;
        }
        
        json.append("\n  }");
        return json.toString();
    }
    
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJsonString((String) value) + "\"";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else {
            return "\"" + value.toString() + "\"";
        }
    }
    
    private String escapeJsonString(String str) {
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    /**
     * Simple JSON parsing (avoiding external dependencies)
     * Note: This is a basic parser for our specific use case
     */
    private List<ExecutionRecord> parseHistoryJson(String json) {
        List<ExecutionRecord> records = new ArrayList<>();
        
        // Basic parsing - look for objects between { }
        int start = 0;
        while ((start = json.indexOf('{', start)) != -1) {
            int end = findMatchingBrace(json, start);
            if (end == -1) break;
            
            String recordJson = json.substring(start, end + 1);
            try {
                Map<String, Object> map = parseJsonObject(recordJson);
                ExecutionRecord record = ExecutionRecord.fromMap(map);
                records.add(record);
            } catch (Exception e) {
                // Skip corrupted records
            }
            
            start = end + 1;
        }
        
        return records;
    }
    
    private int findMatchingBrace(String json, int start) {
        int braceCount = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) return i;
            }
        }
        return -1;
    }
    
    private Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> map = new HashMap<>();
        
        // Remove braces and split by commas (basic parsing)
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex == -1) continue;
            
            String key = pair.substring(0, colonIndex).trim();
            String value = pair.substring(colonIndex + 1).trim();
            
            // Remove quotes from key
            key = key.replaceAll("^\"|\"$", "");
            
            // Parse value
            Object parsedValue = parseJsonValue(value);
            map.put(key, parsedValue);
        }
        
        return map;
    }
    
    private Object parseJsonValue(String value) {
        value = value.trim();
        
        if (value.equals("null")) {
            return null;
        } else if (value.equals("true")) {
            return true;
        } else if (value.equals("false")) {
            return false;
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            // String value
            return value.substring(1, value.length() - 1)
                       .replace("\\\"", "\"")
                       .replace("\\\\", "\\")
                       .replace("\\n", "\n")
                       .replace("\\r", "\r")
                       .replace("\\t", "\t");
        } else {
            // Try to parse as number
            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                } else {
                    return Integer.parseInt(value);
                }
            } catch (NumberFormatException e) {
                return value; // Return as string if can't parse
            }
        }
    }
    
    /**
     * Utility method to get short path for display
     */
    private static String getShortPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }
        
        Path path = Path.of(fullPath);
        String fileName = path.getFileName().toString();
        
        if (fullPath.length() <= 40) {
            return fullPath;
        }
        
        String parent = path.getParent().toString();
        if (parent.length() > 20) {
            parent = "..." + parent.substring(parent.length() - 20);
        }
        
        return parent + "/" + fileName;
    }
}