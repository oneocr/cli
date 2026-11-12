package oneocr.cli.engine;

import oneocr.onnx.OcrOptions;

/**
 * Opens the OCR backend, and is the only place allowed to.
 *
 * <p><b>The rule this class exists to enforce:</b> {@code oneocr.dll} loads Microsoft's own
 * {@code onnxruntime.dll} beside it, and the ONNX Runtime Java artifact loads its own bundled native
 * of the same name. Two builds of the same library in one process is undefined at best. So once one
 * backend has been opened in this JVM, the other is refused with an explanation rather than allowed
 * to fail in some unrepeatable way later. Nothing in this tool opens an engine any other way.
 *
 * <p>The default is deliberately conservative: the DLL on Windows, because that is what every
 * existing output was produced with and what everything here has been tuned against, and ONNX
 * everywhere else, because there is no alternative there. Choosing a backend is therefore always
 * either the platform's decision or an explicit one.
 */
public final class Engines {
    private static final Object LOCK = new Object();
    private static EngineChoice opened;

    private Engines() {}

    /** Whichever backend {@link EngineChoice#AUTO} means on this machine. */
    public static EngineChoice resolve(EngineChoice requested) {
        if (requested != null && requested != EngineChoice.AUTO) return requested;
        return windows() ? EngineChoice.DLL : EngineChoice.ONNX;
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /**
     * Opens an engine, or explains why it cannot be opened.
     *
     * @param requested which backend, or {@link EngineChoice#AUTO} to let the platform decide
     * @param maxLines  cap on lines per page, as both backends accept
     * @param options   how much of the ONNX pipeline to skip; ignored by the DLL backend, which has
     *                  no equivalent controls
     */
    public static OcrEngine open(EngineChoice requested, int maxLines, OcrOptions options) throws Exception {
        var choice = resolve(requested);
        claim(choice);
        return switch (choice) {
            case DLL -> new DllEngine(maxLines);
            case ONNX -> new OnnxEngine(maxLines, options);
            case AUTO -> throw new IllegalStateException("AUTO should already have been resolved");
        };
    }

    /**
     * Records that this backend's native is now in the process, refusing the other one.
     *
     * <p>Closing an engine does not release the claim, and that is not an oversight: neither native
     * can be unloaded from a JVM once loaded, so the claim describes the process for its lifetime.
     */
    static void claim(EngineChoice choice) {
        synchronized (LOCK) {
            if (opened == null) {
                opened = choice;
                return;
            }
            if (opened != choice)
                throw new IllegalStateException("this process already loaded the " + opened.label()
                        + " backend, and the " + choice.label() + " one cannot be loaded beside it: both"
                        + " bring their own build of onnxruntime's native library. Run them as separate"
                        + " processes, or pick one with --engine.");
        }
    }

    /** For tests: forgets the claim. Does not, and cannot, unload anything. */
    static void forgetClaim() {
        synchronized (LOCK) {
            opened = null;
        }
    }
}
