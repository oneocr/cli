package oneocr.cli.layout;

import java.util.List;

/**
 * A page decomposed by recursive X-Y cutting. {@link ColumnsNode} children sit side by side,
 * {@link StackNode} children sit one above the other, {@link LinesNode} is a run of OCR lines
 * that could not be cut any further.
 */
public sealed interface LayoutNode permits LinesNode, ColumnsNode, StackNode {

    List<TextBox> boxes();

    default Rect bounds() { return Rect.unionOf(boxes().stream().map(TextBox::rect).toList()); }

    default boolean isEmpty() { return boxes().isEmpty(); }
}
