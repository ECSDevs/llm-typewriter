package cc.ptoe.llmtypewriter

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle

/**
 * Style overrides used by [MarkdownTypewriterRenderer] when painting a markdown stream.
 *
 * Every field is a plain [SpanStyle] / [Color] so the caller can swap in their own design system —
 * the defaults in [LlmTypewriterDefaults.markdownStyles] resolve against the active MaterialTheme.
 *
 * Math rendering note: LaTeX fragments (`$…$` / `$$…$$`) are delegated to AndroidMath's
 * `MTMathView`, which renders with its own fonts (Latin Modern Math / Tex Gyre Termes / XITS Math)
 * via native Freetype. Of the [math] field, only [SpanStyle.color] is honored — other SpanStyle
 * properties (fontFamily, fontStyle, …) are ignored because AndroidMath owns the typography.
 */
@Immutable
data class MarkdownStyles(
    val bold: SpanStyle,
    val italic: SpanStyle,
    val code: SpanStyle,
    val link: SpanStyle,
    val heading: SpanStyle,
    val strikethrough: SpanStyle,
    val codeBlockBackground: Color,
    val codeBlockText: Color,
    val codeBlockKeyword: Color,
    val codeBlockString: Color,
    val codeBlockComment: Color,
    val codeBlockNumber: Color,
    /** Color for math fragments (`$...$` inline, `$$...$$` display). Only [SpanStyle.color] is
     *  honored — see class kdoc. */
    val math: SpanStyle = SpanStyle(),
    /** Background tint for display-math (`$$...$$`) blocks. */
    val displayMathBackground: Color = Color.Unspecified,
    /** Scale multiplier applied to the base font size when rendering display math. */
    val displayScale: Float = 1.2f,
)
