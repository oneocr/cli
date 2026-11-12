package oneocr.cli.engine;

import oneocr.api.OcrResult;

import java.awt.image.BufferedImage;

/**
 * One page in, one {@link OcrResult} out — whichever backend produced it.
 *
 * <p>Two implementations: {@link DllEngine} over Microsoft's {@code oneocr.dll}, and
 * {@link OnnxEngine} over the same models run through ONNX Runtime in pure Java, which is the only
 * one that exists off Windows. Both answer in {@code oneocr.api} types so every output format in
 * this tool — text, json, svg, xhtml, layout — works over either without knowing which ran.
 *
 * <p>Open them through {@link Engines}, never directly: the two natives must not both be loaded into
 * one JVM, and that rule is enforced there.
 */
public interface OcrEngine extends AutoCloseable {

    /** Reads one page. Safe to call from several threads unless the implementation says otherwise. */
    OcrResult read(BufferedImage image) throws Exception;

    /**
     * What to record in the output metadata as having produced this text, so a quality difference
     * between the two backends can be attributed later instead of guessed at.
     */
    String engineName();

    /** Whether {@link #read} may be called concurrently on this instance. */
    default boolean threadSafe() {
        return false;
    }

    @Override
    void close();
}
