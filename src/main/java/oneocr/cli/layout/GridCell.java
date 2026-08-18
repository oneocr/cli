package oneocr.cli.layout;

import java.util.List;
import java.util.stream.Collectors;

public record GridCell(List<TextBox> boxes) {

    public boolean empty() { return boxes.isEmpty(); }

    public String text() { return boxes.stream().map(TextBox::text).collect(Collectors.joining(" ")); }

    public Rect rect() { return Rect.unionOf(boxes.stream().map(TextBox::rect).toList()); }
}
