package oneocr.cli.layout;

import java.util.List;

/** Children that sit side by side, ordered left to right. */
public record ColumnsNode(List<LayoutNode> children) implements LayoutNode {

    @Override public List<TextBox> boxes() {
        return children.stream().flatMap(c -> c.boxes().stream()).toList();
    }
}
