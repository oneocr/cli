package oneocr.cli.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.ResourceCache;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;

/**
 * The one way this tool opens a PDF, built for a long run over many pages.
 *
 * <p>Two things PDFBox does by default keep a book's worth of memory alive, and both are off here:
 * <ul>
 * <li>the stream cache lives on the heap, so the file is read into RAM; here it overflows to a temp file;</li>
 * <li>the document's {@link ResourceCache} holds every font, colour space and image XObject it has met
 * through soft references. A scanned page's decoded image is ~35 MB at 300 DPI, and a soft reference is
 * only cleared when the heap is nearly full, so under a 4 GB default heap cap the cache kept about 65 MB
 * per page, with no end (measured, PRP 28: 3 GB at page 47 of 100). Without a cache a page's image is
 * garbage as soon as the page has been drawn; a font is parsed again per page, which costs milliseconds.</li>
 * </ul>
 */
public final class PdfOpen {
    private PdfOpen() {}

    /** Every ResourceCache method is a getter that returns null or a put that returns nothing. */
    static final ResourceCache NO_CACHE = (ResourceCache) Proxy.newProxyInstance(
        ResourceCache.class.getClassLoader(), new Class<?>[]{ResourceCache.class}, (_, _, _) -> null);

    public static PDDocument open(File pdf) throws IOException {
        var doc = Loader.loadPDF(pdf, IOUtils.createTempFileOnlyStreamCache());
        doc.setResourceCache(NO_CACHE);
        return doc;
    }
}
