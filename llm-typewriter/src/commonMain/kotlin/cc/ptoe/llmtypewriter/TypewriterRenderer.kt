package cc.ptoe.llmtypewriter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Paints the revealed text of a [StreamingTypewriterState] in some way.
 *
 * Two implementations ship out of the box:
 *  - [PlainTypewriterRenderer] — paints raw text in the current text style. Zero parsing cost.
 *  - [MarkdownTypewriterRenderer] — re-parses the revealed string every recomposition and paints
 *    headings, bold/italic/code/strikethrough/links/images inline. Fenced code blocks are painted as
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
 *
 * Pass the owning [state] so the renderer can use [StreamingTypewriterState.revealedTokens]
 * (incrementally cached) instead of re-parsing the whole revealed string on every recomposition.
 */
@Composable
fun rememberMarkdownTypewriterRenderer(
    state: StreamingTypewriterState,
    styles: MarkdownStyles = LlmTypewriterDefaults.markdownStyles(),
): TypewriterRenderer = remember(state, styles) { MarkdownTypewriterRenderer(state, styles) }

/** Direct factory of a Markdown renderer for callers that want to pass styles manually. */
fun MarkdownTypewriterRenderer(
    state: StreamingTypewriterState,
    styles: MarkdownStyles,
): TypewriterRenderer = TypewriterRenderer { text, modifier ->
    RenderMarkdownStream(text, state, styles, modifier)
}

/**
 * A block of layout for the markdown renderer to paint sequentially. Pre-built outside the
 * composition so the `RenderMarkdownStream` body can be a clean for-loop of `@Composable` calls
 * (without the "non-composable nested function calls @Composable" error).
 *
 * `internal` (not `private`) so that pure-logic helpers like [buildListBlock] — which produce
 * [ListBlock]s — can be unit-tested without the Compose runtime.
 */
internal sealed class MdBlock {
    data class Inline(val annotated: AnnotatedString) : MdBlock()
    data class InlineWithImages(val tokens: List<MdToken>) : MdBlock()
    /** Inline run that contains math segments interleaved with text — laid out on a single
     *  wrapped line via [FlowRow] so math sits inline with surrounding words (not on its own row). */
    data class InlineRunWithMath(val segments: List<InlineSegment>) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Image(val altText: String, val url: String) : MdBlock()
    data class Code(val token: MdToken.CodeBlock) : MdBlock()
    /** Display math `$$...$$` — block-level, centered, with background. */
    data class DisplayMath(val content: String) : MdBlock()
    /** GFM table — header row + aligned data rows, rendered as a bordered grid. */
    data class Table(val token: MdToken.Table) : MdBlock()
    /**
     * A list (ordered or unordered). Built from a run of [MdToken.ListItem] tokens via
     * [buildListBlock]. The first item's [startNumber] becomes the list's start number for
     * ordered lists (so `3. ...` starts at 3); for unordered lists it's 0.
     *
     * Named `ListBlock` (not `List`) to avoid collision with `kotlin.collections.List` inside
     * this file's many `List<…>` parameter / variable types.
     */
    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<ListItemNode>,
    ) : MdBlock()
}

/**
 * One entry in an [MdBlock.ListBlock]. [inline] is the item's text content (already parsed into
 * tokens by the streaming parser); [sublists] are nested lists rendered indented under this item.
 *
 * `internal` (not `private`) so [buildListBlock] can return it for unit testing.
 */
internal data class ListItemNode(
    val inline: List<MdToken>,
    val sublists: List<MdBlock.ListBlock>,
)

/** A segment within an [MdBlock.InlineRunWithMath] — either styled text or a math fragment.
 *  `internal` (not `private`) because [MdBlock] is internal and this type leaks through
 *  [MdBlock.InlineRunWithMath.segments]. */
internal sealed class InlineSegment {
    data class Text(val annotated: AnnotatedString) : InlineSegment()
    data class Math(val content: String) : InlineSegment()
}

private fun planBlocks(tokens: List<MdToken>, styles: MarkdownStyles): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    for (group in groupIntoParagraphs(tokens)) {
        // List items group together — consecutive ListItem tokens (with no intervening paragraph
        // break) form one [MdBlock.ListBlock]. Detected before the single-token branches below so
        // a lone list item doesn't fall into the inline-assembly path.
        val firstTok = group.firstOrNull()
        if (firstTok is MdToken.ListItem) {
            val listItems = group.filterIsInstance<MdToken.ListItem>()
            if (listItems.isNotEmpty()) {
                blocks += buildListBlock(listItems)
                continue
            }
        }
        if (group.size == 1) {
            when (val tok = group[0]) {
                is MdToken.CodeBlock -> { blocks += MdBlock.Code(tok); continue }
                is MdToken.Heading -> { blocks += MdBlock.Heading(tok.level, tok.text); continue }
                is MdToken.Image -> { blocks += MdBlock.Image(tok.altText, tok.url); continue }
                is MdToken.DisplayMath -> { blocks += MdBlock.DisplayMath(tok.content); continue }
                is MdToken.Table -> { blocks += MdBlock.Table(tok); continue }
                else -> Unit
            }
        }
        val hasMath = group.any { it is MdToken.InlineMath }
        val hasImage = group.any { it is MdToken.Image }
        if (hasImage) {
            blocks += MdBlock.InlineWithImages(group)
        } else if (hasMath) {
            blocks += MdBlock.InlineRunWithMath(buildInlineSegments(group, styles))
        } else {
            blocks += MdBlock.Inline(buildAnnotatedFromInline(group, styles))
        }
    }
    return blocks
}

/**
 * Builds an [MdBlock.List] tree from a flat run of [MdToken.ListItem] tokens. Indentation drives
 * nesting — an item with `indent = N + 1` (deeper than the previous `indent = N`) becomes a child
 * of the previous item at indent `N`. Items at the same indent are siblings.
 *
 * The stack holds the active nesting path; each level knows its [ordered] / [startNumber] / [indent]
 * plus a mutable list of items being built. When a new item arrives:
 *   1. Pop levels deeper than the new item's indent.
 *   2. If the top is shallower than the new item's indent, push a new level (as a sublist of the
 *      top's last item). Indent jumps larger than 1 are clamped to `top.indent + 1` so a stray
 *      6-space indent doesn't create 3 phantom nesting levels.
 *   3. Add the item to the top level.
 *
 * Pure and side-effect-free so it's unit-testable without the Compose runtime.
 */
internal fun buildListBlock(tokens: List<MdToken.ListItem>): MdBlock.ListBlock {
    if (tokens.isEmpty()) return MdBlock.ListBlock(ordered = false, startNumber = 0, items = emptyList())
    val root = MutableListLevel(
        ordered = tokens.first().ordered,
        startNumber = tokens.first().number,
        indent = tokens.first().indent,
    )
    val stack = mutableListOf(root)

    for (tok in tokens) {
        // Pop levels deeper than the current item's indent.
        while (stack.size > 1 && stack.last().indent > tok.indent) {
            stack.removeAt(stack.lastIndex)
        }
        val top = stack.last()
        if (top.indent < tok.indent) {
            // Deeper than the current top — push a new sublevel under the top's last item.
            // Clamp indent to top.indent + 1 to avoid phantom nesting on big jumps.
            val newIndent = minOf(tok.indent, top.indent + 1)
            val newLevel = MutableListLevel(
                ordered = tok.ordered,
                startNumber = tok.number,
                indent = newIndent,
            )
            if (top.items.isEmpty()) {
                // No parent item exists yet — create an empty placeholder so the sublist has a
                // parent to attach to. (Shouldn't normally happen for well-formed input.)
                top.items += MutableListItem()
            }
            top.items.last().sublists += newLevel
            stack += newLevel
        }
        stack.last().items += MutableListItem(inline = tok.inline)
    }

    return root.freeze()
}

/** Mutable builder used by [buildListBlock] — frozen into an [MdBlock.ListBlock] at the end. */
private class MutableListLevel(
    val ordered: Boolean,
    val startNumber: Int,
    val indent: Int,
    val items: MutableList<MutableListItem> = mutableListOf(),
)

private class MutableListItem(
    var inline: List<MdToken> = emptyList(),
    val sublists: MutableList<MutableListLevel> = mutableListOf(),
)

private fun MutableListLevel.freeze(): MdBlock.ListBlock = MdBlock.ListBlock(
    ordered = ordered,
    startNumber = startNumber,
    items = items.map { it.freeze() },
)

private fun MutableListItem.freeze(): ListItemNode = ListItemNode(
    inline = inline,
    sublists = sublists.map { it.freeze() },
)

/**
 * Splits a flat token stream into paragraph groups following CommonMark soft-break rules:
 *  - A run of **two or more** consecutive [MdToken.Newline]s ends a paragraph (blank-line
 *    separator). Any number of newlines ≥ 2 collapses to a single paragraph break.
 *  - A **single** [MdToken.Newline] is a soft break — rendered as a space so wrapped text joins
 *    across the line instead of breaking.
 *  - Block-level tokens ([MdToken.Heading], [MdToken.CodeBlock], [MdToken.DisplayMath],
 *    [MdToken.Image], [MdToken.ListItem]) always start their own group and act as implicit
 *    paragraph breaks.
 *
 * [MdToken.ListItem] is special-cased: consecutive items with no intervening blank line go into
 * the **same** group (so the renderer can build one list tree from them). A single newline
 * between two list items is dropped (no space inserted) — list items are visual blocks, not
 * soft-wrapped text. A non-ListItem token following a list group flushes it (so a paragraph
 * immediately after a list isn't glued onto the last item).
 *
 * A single newline at a block boundary (when the current group is empty) is dropped so it doesn't
 * introduce a leading space in the next paragraph.
 *
 * Pure and side-effect-free so it's unit-testable without the Compose runtime.
 */
internal fun groupIntoParagraphs(tokens: List<MdToken>): List<List<MdToken>> {
    val groups = mutableListOf<MutableList<MdToken>>()
    var current = mutableListOf<MdToken>()
    fun flush() {
        if (current.isNotEmpty()) {
            groups += current
            current = mutableListOf()
        }
    }
    var i = 0
    while (i < tokens.size) {
        val tok = tokens[i]
        when (tok) {
            is MdToken.ListItem -> {
                // If the current group isn't already a list, flush it — list items never mix
                // with regular inline text in one group.
                val currentIsList = current.firstOrNull() is MdToken.ListItem
                if (!currentIsList) flush()
                current += tok
            }
            is MdToken.Heading, is MdToken.CodeBlock, is MdToken.DisplayMath, is MdToken.Image,
            is MdToken.Table -> {
                flush()
                groups += mutableListOf(tok)
            }
            MdToken.Newline -> {
                // Count the run of consecutive newlines.
                var run = 1
                while (i + run < tokens.size && tokens[i + run] == MdToken.Newline) run++
                val next = tokens.getOrNull(i + run)
                val nextIsBlock =
                    next is MdToken.Heading || next is MdToken.CodeBlock ||
                        next is MdToken.DisplayMath || next is MdToken.Image || next is MdToken.Table
                val currentIsList = current.firstOrNull() is MdToken.ListItem
                val nextIsListItem = next is MdToken.ListItem
                when {
                    // Blank line (2+ newlines) → paragraph break.
                    run >= 2 -> flush()
                    // Single newline immediately before a block-level token, or before a list
                    // item when we're NOT already inside a list group → paragraph break. Don't
                    // seed a trailing space; the block / list token flushes its own group.
                    nextIsBlock || (nextIsListItem && !currentIsList) -> flush()
                    // Inside a list and the next non-newline token is another list item → drop
                    // the newline entirely. List items are visual blocks, not soft-wrapped text.
                    currentIsList && nextIsListItem -> { /* drop newline */ }
                    // List group ended (next is inline text, not a list item) → flush so the
                    // trailing paragraph isn't glued onto the last item as a soft-break space.
                    currentIsList && next != null -> flush()
                    // Single newline between two inline runs → CommonMark soft break (space).
                    current.isNotEmpty() && next != null -> current += MdToken.Plain(" ")
                    // else: single newline at block boundary or end of input → drop.
                }
                i += run
                continue
            }
            else -> {
                // Non-list inline token (Plain, Bold, …) — if we were in a list group, flush it
                // so the new paragraph starts fresh.
                if (current.firstOrNull() is MdToken.ListItem) flush()
                current += tok
            }
        }
        i++
    }
    flush()
    return groups
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
    state: StreamingTypewriterState,
    styles: MarkdownStyles,
    modifier: Modifier,
) {
    // Use the state's incrementally-cached tokens rather than re-parsing `text` from scratch.
    // Keying on the tokens list *reference* (not `text`) means non-streaming recompositions
    // (scroll, theme switch, cursor blink) hit the cache and skip planBlocks entirely —
    // revealedTokens() returns the same List instance when the revealed length is unchanged.
    // `text` still drives recomposition from the caller (state.revealed read) so new chars
    // still invalidate; it's just no longer substring-compared each frame.
    val tokens = state.revealedTokens()
    val blocks = remember(tokens, styles) { planBlocks(tokens, styles) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Inline -> {
                    if (block.annotated.length > 0) Text(text = block.annotated)
                }
                is MdBlock.InlineWithImages -> RenderInlineWithImages(block.tokens, styles)
                is MdBlock.InlineRunWithMath -> RenderInlineRunWithMath(block.segments, styles)
                is MdBlock.Heading -> {
                    val base = LocalTextStyle.current
                    val baseSize = base.fontSize.let { fs ->
                        if (fs.value > 0f) fs else 16.sp
                    }
                    val style = base.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = baseSize * headingScale(block.level),
                    )
                    Text(text = block.text, style = style)
                }
                is MdBlock.Image -> styles.imageRenderer.Render(block.url, block.altText)
                is MdBlock.Code -> RenderCodeBlock(block.token, styles)
                is MdBlock.DisplayMath -> RenderDisplayMath(block.content, styles)
                is MdBlock.Table -> RenderTable(block.token, styles)
                is MdBlock.ListBlock -> RenderList(block, styles, depth = 0)
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
 * the equation's **actual measured width** (via [measurePlatformMath]) so each inline equation
 * gets its own width and surrounding text reflows correctly — previously every equation got a
 * fixed `fontSize * 4` slot regardless of content. The placeholder height stays at the text line
 * height so the math view cannot inflate the line; any vertical overflow is clipped (see
 * [RenderInlineMath]).
 */
@Composable
private fun RenderInlineRunWithMath(segments: List<InlineSegment>, styles: MarkdownStyles) {
    val baseStyle = LocalTextStyle.current
    val density = LocalDensity.current
    val resolvedFontSize = baseStyle.fontSize.let { fs ->
        if (fs.value > 0f) fs else 16.sp
    }
    val lineHeight = baseStyle.lineHeight.let { lh ->
        if (lh.value > 0f) lh else resolvedFontSize * 1.2f
    }

    // Pre-measure each math fragment so the placeholder width matches the equation's actual
    // rendered width (instead of a fixed estimate). A manual loop avoids calling a @Composable
    // from an inline lambda such as List.map, which the Compose compiler rejects.
    val mathWidths = mutableListOf<TextUnit>()
    for (segment in segments) {
        if (segment is InlineSegment.Math) {
            val measured = measurePlatformMath(segment.content, displayMode = false, resolvedFontSize)
            mathWidths += with(density) { measured.width.toSp() }
        } else {
            mathWidths += TextUnit.Unspecified
        }
    }

    // Collect math segments for inline content; build the annotated string with markers.
    val mathContents = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var mathIndex = 0
        var widthIndex = 0
        for (segment in segments) {
            when (segment) {
                is InlineSegment.Text -> append(segment.annotated)
                is InlineSegment.Math -> {
                    val id = "math_${mathIndex++}"
                    appendInlineContent(id, segment.content)
                    val width = mathWidths[widthIndex]
                    mathContents[id] = InlineTextContent(
                        placeholder = Placeholder(
                            width = width,
                            height = lineHeight,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        RenderInlineMath(segment.content, styles)
                    }
                }
            }
            widthIndex++
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

/**
 * Renders a GFM table following Material 3 data-table conventions:
 *  - Header row tinted with [MarkdownStyles.tableHeaderBackground] (default `surfaceContainerHigh`)
 *    and medium-weight text.
 *  - 1dp [MarkdownStyles.tableBorder] (default `outlineVariant`) cell dividers.
 *  - 16dp horizontal / 12dp vertical cell padding (M3 spec).
 *  - 12dp corner radius on the whole table (cells stay square; the outer clip rounds the grid).
 *
 * Each column gets equal width via [RowScope.weight]. Cell text is inline-parsed (so bold /
 * italic / inline code / links work inside cells) and aligned per the separator row's colons.
 * Rows with fewer cells than the header are padded with empty cells; extra cells are truncated.
 */
@Composable
private fun RenderTable(token: MdToken.Table, styles: MarkdownStyles) {
    val baseStyle = LocalTextStyle.current
    val borderColor = styles.tableBorder.takeIf { it != Color.Unspecified }
        ?: LocalContentColor.current.copy(alpha = 0.3f)
    val headerBackground = styles.tableHeaderBackground.takeIf { it != Color.Unspecified }
        ?: Color.Transparent
    val tableBackground = styles.tableBackground.takeIf { it != Color.Unspecified }
        ?: Color.Transparent
    val columnCount = token.headers.size
    val tableShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tableBackground, tableShape)
            .clip(tableShape)
            .border(1.dp, borderColor, tableShape),
    ) {
        // Header row. height(IntrinsicSize.Min) + fillMaxHeight() on each cell ensures all cells
        // in a row stretch to the tallest cell's height (so borders align when one cell wraps).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(headerBackground)
                .drawBehind { drawLine(borderColor, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1.dp.toPx()) },
            verticalAlignment = Alignment.Top,
        ) {
            token.headers.forEachIndexed { index, header ->
                val align = token.aligns.getOrNull(index) ?: TableAlign.DEFAULT
                TableCell(
                    text = header,
                    align = align,
                    borderColor = borderColor,
                    isLastColumn = index == columnCount - 1,
                    style = baseStyle.copy(fontWeight = FontWeight.Medium),
                    styles = styles,
                )
            }
        }
        // Data rows.
        token.rows.forEach { row ->
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .drawBehind { drawLine(borderColor, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1.dp.toPx()) },
                verticalAlignment = Alignment.Top,
            ) {
                for (i in 0 until columnCount) {
                    val cellText = row.getOrNull(i) ?: ""
                    val align = token.aligns.getOrNull(i) ?: TableAlign.DEFAULT
                    TableCell(
                        text = cellText,
                        align = align,
                        borderColor = borderColor,
                        isLastColumn = i == columnCount - 1,
                        style = baseStyle,
                        styles = styles,
                    )
                }
            }
        }
    }
}

/**
 * One cell in a table row. Uses [RowScope.weight] so columns share the row width equally.
 * [fillMaxHeight] (paired with [IntrinsicSize.Min] on the parent Row) stretches each cell to the
 * tallest cell's height so borders align even when one cell's text wraps to multiple lines.
 * Cell padding follows the M3 data-table spec: 16dp horizontal / 12dp vertical. The cell text is
 * inline-parsed via [parseStreamingMarkdown] so inline formatting (bold, code, links, …) works
 * inside cells. [TextAlign] is derived from the column's [TableAlign].
 */
@Composable
private fun RowScope.TableCell(
    text: String,
    align: TableAlign,
    borderColor: Color,
    isLastColumn: Boolean,
    style: TextStyle,
    styles: MarkdownStyles,
) {
    val textAlign = when (align) {
        TableAlign.LEFT, TableAlign.DEFAULT -> TextAlign.Start
        TableAlign.CENTER -> TextAlign.Center
        TableAlign.RIGHT -> TextAlign.End
    }
    val annotated = remember(text, styles) {
        buildAnnotatedFromInline(parseStreamingMarkdown(text), styles)
    }
    Text(
        text = annotated,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .drawBehind {
                if (!isLastColumn) {
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(size.width - 0.5f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width - 0.5f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = style.copy(textAlign = textAlign),
    )
}

/**
 * Renders a list (ordered or unordered). Each item is laid out as a [Row]: a fixed-width marker
 * column on the left (e.g. `•`, `1.`, `10.`) and the item's inline content on the right.
 *
 * Nested lists (sublists on an item) are rendered indented under that item. Indentation per
 * level is `12.dp` — enough to visually distinguish nesting without consuming too much
 * horizontal space on a phone screen.
 *
 * For ordered lists, the marker number is `startNumber + index` so a list that starts with `3.`
 * continues `3.`, `4.`, `5.`, … (matching the leading number, not the position).
 *
 * Marker column width: `20.dp` for unordered (single `•`), and `28.dp` for ordered to fit up to
 * two-digit numbers (`99.`). Larger numbers (three+ digits) clip; that's an acceptable edge case.
 *
 * The item's inline content may itself contain math (`$…$`) — handled via [RenderInlineRunWithMath]
 * when present, falling back to a plain [Text] otherwise. This mirrors how [planBlocks] renders
 * top-level paragraphs, so list items get the same inline capabilities as body text.
 *
 * @param depth current nesting depth — 0 for a top-level list, increments by 1 per sublist. Used
 *   only to drive the left-padding of the whole list block.
 */
@Composable
private fun RenderList(block: MdBlock.ListBlock, styles: MarkdownStyles, depth: Int) {
    val baseStyle = LocalTextStyle.current
    val markerWidth = if (block.ordered) 28.dp else 20.dp
    val indentPerLevel = 12.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPerLevel * depth),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            val markerText = if (block.ordered) {
                "${block.startNumber + index}."
            } else {
                "•"
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = markerText,
                    modifier = Modifier.width(markerWidth),
                    style = baseStyle,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RenderItemInline(item.inline, styles)
                    for (sublist in item.sublists) {
                        RenderList(sublist, styles, depth = depth + 1)
                    }
                }
            }
        }
    }
}

/**
 * Renders an item's inline content — same logic [planBlocks] uses for a top-level paragraph: if
 * any inline math token is present, route through [RenderInlineRunWithMath] so equations sit
 * inline with the surrounding words; otherwise emit a plain [Text] with the assembled
 * [AnnotatedString]. Empty inline (e.g. an empty `-` line) renders nothing.
 */
@Composable
private fun RenderItemInline(tokens: List<MdToken>, styles: MarkdownStyles) {
    if (tokens.any { it is MdToken.Image }) {
        RenderInlineWithImages(tokens, styles)
        return
    }
    val hasMath = tokens.any { it is MdToken.InlineMath }
    if (hasMath) {
        RenderInlineRunWithMath(buildInlineSegments(tokens, styles), styles)
    } else {
        val annotated = buildAnnotatedFromInline(tokens, styles)
        if (annotated.length > 0) Text(text = annotated)
    }
}

@Composable
private fun RenderInlineWithImages(tokens: List<MdToken>, styles: MarkdownStyles) {
    val textTokens = mutableListOf<MdToken>()
    @Composable
    fun flushText() {
        if (textTokens.isEmpty()) return
        val annotated = buildAnnotatedFromInline(textTokens, styles)
        if (annotated.isNotEmpty()) Text(text = annotated)
        textTokens.clear()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (token in tokens) {
            if (token is MdToken.Image) {
                flushText()
                styles.imageRenderer.Render(token.url, token.altText)
            } else {
                textTokens += token
            }
        }
        flushText()
    }
}

/**
 * Font-size multiplier for a heading level relative to the ambient body font size. Mirrors the
 * common Markdown/HTML convention: `#` is largest, `######` is below body size.
 */
private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.8f
    2 -> 1.5f
    3 -> 1.3f
    4 -> 1.1f
    5 -> 1.0f
    6 -> 0.9f
    else -> 1.0f
}

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
                is MdToken.Image -> append(tok.altText)
                is MdToken.Heading -> withStyle(styles.heading) { append(tok.text) }
                is MdToken.CodeBlock, MdToken.Newline -> { /* block-level — handled by the caller */ }
                // List items are block-level — routed to [buildListBlock] by [planBlocks]. Should
                // never reach this inline-assembly path; emit nothing so they're not rendered as
                // raw `toString()` text.
                is MdToken.ListItem -> { /* block-level — handled by [RenderList] */ }
                // Tables are block-level — routed to [RenderTable] by [planBlocks].
                is MdToken.Table -> { /* block-level — handled by [RenderTable] */ }
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
