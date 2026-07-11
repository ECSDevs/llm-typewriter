package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paints the revealed text of a [StreamingTypewriterState] in some way.
 *
 * Two implementations ship out of the box:
 *  - [PlainTypewriterRenderer] — paints raw text in the current text style. Zero parsing cost.
 *  - [MarkdownTypewriterRenderer] — re-parses the revealed string every recomposition and paints
 *    headings, bold/italic/code/strikethrough/links inline. Fenced code blocks are painted as
 *    full-width blocks with syntax highlighting that builds up as the fence grows.
 */
fun interface TypewriterRenderer {

    @Composable
    fun Render(text: String, modifier: Modifier)
}

/** Renderer that paints the revealed text in the ambient text style with no parsing. */
val PlainTypewriterRenderer: TypewriterRenderer = TypewriterRenderer { text, modifier ->
    Text(text = text, modifier = modifier)
}

/**
 * Returns a renderer that lays out the revealed text as progressive Markdown — bold spans render
 * as bold the moment the closing `**` arrives, fenced code blocks build up line-by-line with
 * inline syntax highlighting, etc.
 *
 * The renderer never reflows already-painted content because [parseStreamingMarkdown] is
 * prefix-stable: the same prefix of input always yields the same prefix of tokens.
 */
@Composable
fun rememberMarkdownTypewriterRenderer(
    styles: MarkdownStyles = LlmTypewriterDefaults.markdownStyles(),
): TypewriterRenderer = remember(styles) { MarkdownTypewriterRenderer(styles) }

/** Direct factory of a Markdown renderer for callers that want to pass styles manually. */
fun MarkdownTypewriterRenderer(styles: MarkdownStyles): TypewriterRenderer =
    TypewriterRenderer { text, modifier -> RenderMarkdownStream(text, styles, modifier) }

/**
 * A block of layout for the markdown renderer to paint sequentially. Pre-built outside the
 * composition so the `RenderMarkdownStream` body can be a clean for-loop of `@Composable` calls
 * (without the "non-composable nested function calls @Composable" error).
 */
private sealed class MdBlock {
    data class Inline(val annotated: AnnotatedString) : MdBlock()
    /** Inline run that contains math segments interleaved with text — laid out on a single
     *  wrapped line via [FlowRow] so math sits inline with surrounding words (not on its own row). */
    data class InlineRunWithMath(val segments: List<InlineSegment>) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Code(val token: MdToken.CodeBlock) : MdBlock()
    /** Display math `$$...$$` — block-level, centered, with background. */
    data class DisplayMath(val content: String) : MdBlock()
}

/** A segment within an [MdBlock.InlineRunWithMath] — either styled text or a math fragment. */
private sealed class InlineSegment {
    data class Text(val annotated: AnnotatedString) : InlineSegment()
    data class Math(val content: String) : InlineSegment()
}

private fun planBlocks(tokens: List<MdToken>, styles: MarkdownStyles): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val pending = mutableListOf<MdToken>()

    fun flushPending() {
        if (pending.isEmpty()) return
        val hasMath = pending.any { it is MdToken.InlineMath }
        if (hasMath) {
            blocks += MdBlock.InlineRunWithMath(buildInlineSegments(pending, styles))
        } else {
            blocks += MdBlock.Inline(buildAnnotatedFromInline(pending, styles))
        }
        pending.clear()
    }

    for (tok in tokens) {
        when (tok) {
            is MdToken.CodeBlock -> {
                flushPending()
                blocks += MdBlock.Code(tok)
            }
            is MdToken.Heading -> {
                flushPending()
                blocks += MdBlock.Heading(tok.level, tok.text)
            }
            is MdToken.InlineMath -> {
                // Don't flush — keep in pending so it renders inline with surrounding text on the
                // same row, instead of being split onto its own line.
                pending += tok
            }
            is MdToken.DisplayMath -> {
                flushPending()
                blocks += MdBlock.DisplayMath(tok.content)
            }
            MdToken.Newline -> pending += MdToken.Plain("\n")
            else -> pending += tok
        }
    }
    flushPending()
    return blocks
}

/**
 * Splits a run of inline tokens (which may contain [MdToken.InlineMath] entries) into an ordered
 * list of [InlineSegment]s. Text between math tokens is kept as a single [InlineSegment.Text] —
 * the [Text] composable handles line wrapping itself, so we don't need word-level pre-splitting.
 */
private fun buildInlineSegments(tokens: List<MdToken>, styles: MarkdownStyles): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    val textBuffer = mutableListOf<MdToken>()

    fun flushText() {
        if (textBuffer.isEmpty()) return
        val annotated = buildAnnotatedFromInline(textBuffer, styles)
        if (annotated.length > 0) {
            segments += InlineSegment.Text(annotated)
        }
        textBuffer.clear()
    }

    for (tok in tokens) {
        if (tok is MdToken.InlineMath) {
            flushText()
            segments += InlineSegment.Math(tok.content)
        } else {
            textBuffer += tok
        }
    }
    flushText()
    return segments
}

@Composable
private fun RenderMarkdownStream(
    text: String,
    styles: MarkdownStyles,
    modifier: Modifier,
) {
    val blocks = remember(text, styles) { planBlocks(parseStreamingMarkdown(text), styles) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Inline -> {
                    if (block.annotated.length > 0) Text(text = block.annotated)
                }
                is MdBlock.InlineRunWithMath -> RenderInlineRunWithMath(block.segments, styles)
                is MdBlock.Heading -> {
                    val style = LocalTextStyle.current.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = headingSize(block.level),
                    )
                    Text(text = block.text, style = style)
                }
                is MdBlock.Code -> RenderCodeBlock(block.token, styles)
                is MdBlock.DisplayMath -> RenderDisplayMath(block.content, styles)
            }
        }
    }
}

/**
 * Renders an inline run containing math segments interleaved with text. Uses [Text] with
 * [InlineTextContent] instead of [FlowRow] so that:
 *  - Line height is controlled entirely by [TextStyle.lineHeight] (no FlowRow row-height
 *    inflation). This fixes "行距 2-3 行那么高" caused by FlowRow's row height being max(cell
 *    heights), which was larger than a normal text line because AndroidView's internal measure
 *    reported a larger height than the text line height.
 *  - Text wrapping is handled natively by [Text] — long sentences with embedded math wrap at
 *    word boundaries automatically.
 *  - No layout-type switching between [MdBlock.Inline] and [MdBlock.InlineRunWithMath] mid-stream
 *    — both use [Text], so when the parser promotes a block to InlineRunWithMath mid-stream (on
 *    the closing `$`), the surrounding layout doesn't jump ("上面那行直接一个闪烁往上移了一行").
 *
 * Each math segment becomes an `appendInlineContent` marker in the [AnnotatedString]; the
 * [InlineTextContent] map renders each marker via [RenderInlineMath]. The placeholder width is
 * a rough estimate — the actual math view may overflow visually, but this is acceptable because
 * the math view's height is constrained (see [RenderInlineMath]) and the text reflows around the
 * placeholder, not the visual overflow.
 */
@Composable
private fun RenderInlineRunWithMath(segments: List<InlineSegment>, styles: MarkdownStyles) {
    val baseStyle = LocalTextStyle.current
    val resolvedFontSize = baseStyle.fontSize.let { fs ->
        if (fs.value > 0f) fs else 16.sp
    }
    val lineHeight = baseStyle.lineHeight.let { lh ->
        if (lh.value > 0f) lh else resolvedFontSize * 1.2f
    }

    // Collect math segments for inline content; build the annotated string with markers.
    val mathContents = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var mathIndex = 0
        for (segment in segments) {
            when (segment) {
                is InlineSegment.Text -> append(segment.annotated)
                is InlineSegment.Math -> {
                    val id = "math_${mathIndex++}"
                    appendInlineContent(id, segment.content)
                    mathContents[id] = InlineTextContent(
                        placeholder = Placeholder(
                            width = resolvedFontSize * 4f, // rough estimate; actual view may overflow
                            height = lineHeight,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        RenderInlineMath(segment.content, styles)
                    }
                }
            }
        }
    }

    Text(text = annotated, inlineContent = mathContents)
}

/**
 * Inline math — delegates to [RenderPlatformMath] (AndroidMath's MTMathView on Android) with
 * `displayMode = false` so the equation renders in text style and sits inline with the
 * surrounding text. The [content] is the complete LaTeX fragment (the closing `$` has arrived);
 * before that, the parser emits the partial input as plain text, so this is never called with a
 * half-formed string.
 *
 * The math view is wrapped in a [Box] whose height is forced to the text line height via
 * [Modifier.requiredHeight] (stronger than [Modifier.height] — ignores parent constraints) plus
 * [Modifier.clipToBounds] so the (taller) MTMathView cannot inflate the FlowRow row height. The
 * math view's visual overflow is clipped within the box. Center-aligned so the math sits at the
 * text's visual midline (fixes "行内TeX整体偏高/偏低" — BottomStart pushed it too low, plain
 * Bottom let it sit too high).
 */
@Composable
private fun RenderInlineMath(content: String, styles: MarkdownStyles) {
    val baseStyle = LocalTextStyle.current
    val density = LocalDensity.current
    val resolvedFontSize = baseStyle.fontSize.let { fs ->
        if (fs.value > 0f) fs else 16.sp
    }
    val lineHeight = baseStyle.lineHeight.let { lh ->
        if (lh.value > 0f) lh else resolvedFontSize * 1.2f
    }
    val lineHeightDp = with(density) { lineHeight.toDp() }
    val textColor = styles.math.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    Box(
        modifier = Modifier
            .requiredHeight(lineHeightDp)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        RenderPlatformMath(
            latex = content,
            displayMode = false,
            textColor = textColor,
            fontSize = resolvedFontSize,
        )
    }
}

/**
 * Display math — block-level, centered, with a background tint. `displayMode = true` bumps the
 * font size by [MarkdownStyles.displayScale] (the "块级 LaTeX 的缩放" requirement) and renders in
 * display style so big-operator limits stack above/below the glyph.
 */
@Composable
private fun RenderDisplayMath(content: String, styles: MarkdownStyles) {
    val baseStyle = LocalTextStyle.current
    val resolvedFontSize = baseStyle.fontSize.let { fs ->
        if (fs.value > 0f) fs else 16.sp
    }
    val displayFontSize = resolvedFontSize * styles.displayScale
    val textColor = styles.math.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(styles.displayMathBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        RenderPlatformMath(
            latex = content,
            displayMode = true,
            textColor = textColor,
            fontSize = displayFontSize,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun headingSize(@Suppress("UNUSED_PARAMETER") level: Int): androidx.compose.ui.unit.TextUnit =
    androidx.compose.ui.unit.TextUnit.Unspecified

internal fun buildAnnotatedFromInline(tokens: List<MdToken>, styles: MarkdownStyles): AnnotatedString {
    return buildAnnotatedString {
        for (tok in tokens) {
            when (tok) {
                is MdToken.Plain -> append(tok.text)
                is MdToken.Bold -> withStyle(styles.bold) { append(tok.text) }
                is MdToken.Italic -> withStyle(styles.italic) { append(tok.text) }
                is MdToken.BoldItalic -> withStyle(styles.bold) {
                    withStyle(styles.italic) { append(tok.text) }
                }
                is MdToken.InlineCode -> withStyle(styles.code) { append(tok.text) }
                is MdToken.Strikethrough -> withStyle(styles.strikethrough) { append(tok.text) }
                is MdToken.Link -> withLink(
                    LinkAnnotation.Url(
                        url = tok.url,
                        styles = TextLinkStyles(style = styles.link),
                    ),
                ) { append(tok.label) }
                is MdToken.Heading -> withStyle(styles.heading) { append(tok.text) }
                is MdToken.CodeBlock, MdToken.Newline -> { /* block-level — handled by the caller */ }
                // Math tokens are rendered as dedicated blocks by [planBlocks]. They should never
                // reach this inline-assembly path; if they do, emit raw content so no characters
                // are silently dropped.
                is MdToken.InlineMath -> append(tok.content)
                is MdToken.DisplayMath -> append(tok.content)
            }
        }
    }
}

@Composable
private fun RenderCodeBlock(token: MdToken.CodeBlock, styles: MarkdownStyles) {
    val lang = remember(token.language) { CodeLanguage.parse(token.language) }
    val annotated = remember(token.content, lang, styles) {
        highlightedAnnotated(
            source = token.content,
            language = lang,
            keywordColor = styles.codeBlockKeyword,
            stringColor = styles.codeBlockString,
            numberColor = styles.codeBlockNumber,
            commentColor = styles.codeBlockComment,
            textColor = styles.codeBlockText,
        )
    }
    Text(
        text = annotated,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(styles.codeBlockBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = LocalTextStyle.current.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
    )
}
