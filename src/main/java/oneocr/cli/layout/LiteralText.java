package oneocr.cli.layout;

import luvx.Text_I;
import luvx.composable.HasTextContent;
import luvx.rendering_behavior.InlineMarkupRendering;

/**
 * An inline text node that keeps its whitespace exactly. luvml's ordinary text node collapses runs
 * of whitespace to a single space, which would erase the tab that separates the parts of a split
 * line - the one character in that markup a text extractor can key on.
 */
public final class LiteralText implements Text_I<LiteralText>, HasTextContent<LiteralText> {

    private final String content;

    public LiteralText(String content) { this.content = content; }

    public static LiteralText tab() { return new LiteralText("\t"); }

    @Override public String wholeText()   { return content; }
    @Override public String text()        { return content; }
    @Override public String textContent() { return content; }

    @Override public InlineMarkupRendering markupRenderingBehavior() { return InlineMarkupRendering.I; }

    @Override public LiteralText self() { return this; }
}
