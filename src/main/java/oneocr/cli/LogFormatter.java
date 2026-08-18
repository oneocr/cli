package oneocr.cli;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Standardized logging formatter for consistent debug/verbose output across all OCR modes
 */
public class LogFormatter {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    private final boolean verbose;
    private final boolean includeTimestamp;
    
    public LogFormatter(boolean verbose, boolean includeTimestamp) {
        this.verbose = verbose;
        this.includeTimestamp = includeTimestamp;
    }
    
    public LogFormatter(boolean verbose) {
        this(verbose, false);
    }
    
    /**
     * Log info message with consistent formatting
     */
    public void info(String category, String message) {
        if (!verbose) return;
        System.err.printf("%s%s %s%n", timestamp(), category(category), message);
    }
    
    /**
     * Log success message with consistent formatting
     */
    public void success(String category, String message) {
        if (!verbose) return;
        System.err.printf("%s✅ %s %s%n", timestamp(), category(category), message);
    }
    
    /**
     * Log warning message with consistent formatting
     */
    public void warning(String category, String message) {
        if (!verbose) return;
        System.err.printf("%s⚠️ %s %s%n", timestamp(), category(category), message);
    }
    
    /**
     * Log error message with consistent formatting (always shown regardless of verbose)
     */
    public void error(String category, String message) {
        System.err.printf("%s❌ %s %s%n", timestamp(), category(category), message);
    }
    
    /**
     * Log debug details (only in verbose mode)
     */
    public void debug(String category, String message) {
        if (!verbose) return;
        System.err.printf("%s🔍 %s %s%n", timestamp(), category(category), message);
    }
    
    /**
     * Log step/progress information
     */
    public void step(String category, String message) {
        if (!verbose) return;
        System.err.printf("%s▶️ %s %s%n", timestamp(), category(category), message);
    }
    
    /**
     * Log completion information
     */
    public void complete(String category, String message) {
        System.err.printf("%s🏁 %s %s%n", timestamp(), category(category), message);
    }
    
    private String timestamp() {
        if (!includeTimestamp) return "";
        return "[" + LocalDateTime.now().format(TIME_FORMAT) + "] ";
    }
    
    private String category(String cat) {
        return "[" + cat + "]";
    }
    
    // Convenience factory methods
    public static LogFormatter standard(boolean verbose) {
        return new LogFormatter(verbose, false);
    }
    
    public static LogFormatter timestamped(boolean verbose) {
        return new LogFormatter(verbose, true);
    }
}