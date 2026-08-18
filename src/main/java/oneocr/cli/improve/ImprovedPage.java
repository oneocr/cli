package oneocr.cli.improve;

import java.util.List;
import oneocr.api.OcrResult;

public record ImprovedPage(int pageNo, OcrResult result, int bandsFlagged, int bandsReplaced, List<String> langsUsed) {

    public boolean changed() {
        return bandsReplaced > 0;
    }
}
