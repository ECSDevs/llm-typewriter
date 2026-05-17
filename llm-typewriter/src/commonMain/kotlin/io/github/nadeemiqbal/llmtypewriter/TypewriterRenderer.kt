package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

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
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Code(val token: MdToken.CodeBlock) : MdBlock()
}

private fun planBlocks(tokens: List<MdToken>, styles: MarkdownStyles): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val pending = mutableListOf<MdToken>()
    for (tok in tokens) {
        when (tok) {
            is MdToken.CodeBlock -> {
                if (pending.isNotEmpty()) {
                    blocks += MdBlock.Inline(buildAnnotatedFromInline(pending, styles))
                    pending.clear()
                }
                blocks += MdBlock.Code(tok)
            }
            is MdToken.Heading -> {
                if (pending.isNotEmpty()) {
                    blocks += MdBlock.Inline(buildAnnotatedFromInline(pending, styles))
                    pending.clear()
                }
                blocks += MdBlock.Heading(tok.level, tok.text)
            }
            MdToken.Newline -> pending += MdToken.Plain("\n")
            else -> pending += tok
        }
    }
    if (pending.isNotEmpty()) {
        blocks += MdBlock.Inline(buildAnnotatedFromInline(pending, styles))
    }
    return blocks
}

@Composable
private fun RenderMarkdownStream(
    text: String,
    styles: MarkdownStyles,
    modifier: Modifier,
) {
    val blocks = remember(text, styles) { planBlocks(parseStreamingMarkdown(text), styles) }
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Inline -> {
                    if (block.annotated.length > 0) Text(text = block.annotated)
                }
                is MdBlock.Heading -> {
                    val style = LocalTextStyle.current.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = headingSize(block.level),
                    )
                    Text(text = block.text, style = style)
                }
                is MdBlock.Code -> RenderCodeBlock(block.token, styles)
            }
        }
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
