package oneocr.cli.engine;

import oneocr.api.OcrResult;
import oneocr.cli.OcrMetadata;
import oneocr.onnx.ModelPaths;
import oneocr.onnx.OcrOptions;
import oneocr.onnx.OneOcrOnnx;
import oneocr.onnx.Raster;

import java.awt.image.BufferedImage;

/**
 * The OneOCR models over ONNX Runtime, in pure Java, with no {@code oneocr.dll}.
 *
 * <p>This is the only backend that exists off Windows, and it needs the models to have been
 * extracted once by {@code oneocr-modelex} on a Windows machine — {@link ModelPaths#resolve} says so
 * plainly if they are absent, which is the failure a Linux user meets first.
 *
 * <p>Thread safe: one instance serves any number of concurrent pages, and it reads the lines of each
 * page concurrently as well. Both share one process-wide pool, so pages and lines cannot
 * oversubscribe each other.
 */
public final class OnnxEngine implements OcrEngine {
    final OneOcrOnnx engine;
    final OcrOptions options;

    OnnxEngine(int maxLines, OcrOptions options) throws Exception {
        this.options = options == null ? OcrOptions.defaults() : options;
        this.engine = new OneOcrOnnx(ModelPaths.resolve(null)).maxLines(maxLines);
    }

    @Override
    public OcrResult read(BufferedImage image) throws Exception {
        return OnnxResults.toApi(engine.recognize(Raster.of(image), options));
    }

    @Override
    public String engineName() {
        return OcrMetadata.ENGINE_ONEOCR_ONNX;
    }

    @Override
    public boolean threadSafe() {
        return true;
    }

    @Override
    public void close() {
        engine.close();
    }
}
