package oneocr.cli.engine;

import oneocr.api.OcrResult;
import oneocr.api.OneOcrApi;
import oneocr.cli.OcrMetadata;
import oneocr.cli.OcrTool;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Microsoft's {@code oneocr.dll}, through the FFM binding in {@code oneocr-api}.
 *
 * <p>This is what {@code 1ocr} has always used, and the pipeline and options are created once and
 * held for the life of the engine rather than per page, which is what the previous per-call code did.
 *
 * <p>Not thread safe. The DLL's pipeline handle is not documented to be, and this project's own
 * {@code ThreadInitializationTest} exists because of the question; one engine per worker thread.
 */
public final class DllEngine implements OcrEngine {
    final OneOcrApi api;
    final oneocr.api.OcrInitOptions initOptions;
    final oneocr.api.OcrPipeline pipeline;
    final oneocr.api.OcrProcessOptions processOptions;

    DllEngine(int maxLines) throws java.io.IOException {
        this.api = new OneOcrApi();
        this.initOptions = api.createInitOptions();
        this.pipeline = api.createPipeline(initOptions);
        this.processOptions = api.createProcessOptions(maxLines);
    }

    @Override
    public OcrResult read(BufferedImage image) {
        var w = image.getWidth();
        var h = image.getHeight();
        // PRP 28: a page rendered as TYPE_INT_RGB already holds its pixels as ints, so hand them over as they
        // are instead of building a second 4-bytes-per-pixel copy on the heap.
        if (image.getType() == BufferedImage.TYPE_INT_RGB
                && image.getRaster().getDataBuffer() instanceof DataBufferInt d
                && d.getOffset() == 0 && d.getData().length == w * h
                && image.getRaster().getSampleModelTranslateX() == 0 && image.getRaster().getSampleModelTranslateY() == 0)
            return api.recognizeImage(pipeline, processOptions, w, h, d.getData());
        return api.recognizeImage(pipeline, processOptions, w, h, OcrTool.convertToBGRA(image));
    }

    @Override
    public String engineName() {
        return OcrMetadata.ENGINE_ONEOCR;
    }

    @Override
    public void close() {
        processOptions.close();
        pipeline.close();
        initOptions.close();
        api.close();
    }
}
