package oneocr.cli.layout;

import java.util.List;

/** Children that sit one above the other, ordered top to bottom. */
public record StackNode(List<LayoutNode> children) implements LayoutNode {

    @Override public List<TextBox> boxes() {
        return children.stream().flatMap(c -> c.boxes().stream()).toList();
    }
}
