package oneocr.cli.engine;

/** Which OCR backend to use. */
public enum EngineChoice {
    /** The DLL on Windows, ONNX everywhere else. The default, and almost always the right answer. */
    AUTO,

    /** Microsoft's {@code oneocr.dll} through the FFM binding. Windows only. */
    DLL,

    /** The same models over ONNX Runtime in pure Java. Works everywhere; the only option off Windows. */
    ONNX;

    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
