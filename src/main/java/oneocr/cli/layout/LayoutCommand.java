package oneocr.cli.layout;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import oneocr.cli.CompactJsonSerializer;
import oneocr.cli.OcrXHtmlReader;
import static oneocr.cli.PagedOcrData.PagedOcrResult;

/**
 * Re-renders a document that has already been through OCR. The saved semantic XHTML (or compact
 * JSON) carries every box, so the layout can be recovered - and its thresholds tuned - without
 * paying for the OCR pass again.
 */
@Command(name = "layout",
    description = "Recover page layout from an existing *.oneocr.xhtml or *.oneocr.json and write "
        + "*.layout.txt and *.layout.html")
public class LayoutCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "An existing .oneocr.xhtml or .oneocr.json file")
    private File input;

    @Option(names = {"-o", "--output"}, description = "Output base name (default: alongside the input)")
    private File output;

    @Option(names = {"-v", "--verbose"}, description = "Report what the cut found")
    private boolean verbose;

    @Mixin
    private LayoutCliOptions opts = new LayoutCliOptions();

    @Override
    public Integer call() throws Exception {
        if (!input.exists()) {
            System.err.println("Error: no such file: " + input);
            return 1;
        }
        var pages = load();
        if (pages.isEmpty()) {
            System.err.println("Error: no OCR pages found in " + input.getName());
            return 1;
        }

        var target = output != null ? output : input;
        var txt = LayoutExport.sibling(target.getAbsoluteFile(), "txt").toPath();
        var html = opts.noLayoutHtml ? null : LayoutExport.sibling(target.getAbsoluteFile(), "html").toPath();

        var analysed = LayoutExport.analyze(pages, opts.options());
        LayoutExport.write(analysed, input.getName(), txt, html, opts.mode());

        if (verbose) for (var page : analysed) System.err.println(LayoutReport.of(page));
        System.out.println("Layout text:  " + txt.getFileName());
        if (html != null) System.out.println("Layout HTML:  " + html.getFileName());
        return 0;
    }

    private List<PagedOcrResult> load() throws Exception {
        var content = Files.readString(input.toPath());
        if (input.getName().toLowerCase().endsWith(".json")) {
            var file = CompactJsonSerializer.fromJson(content);
            return List.of(new PagedOcrResult(1, file.data(), file.metadata().file(),
                file.metadata().width(), file.metadata().height()));
        }
        return OcrXHtmlReader.read(content);
    }
}
