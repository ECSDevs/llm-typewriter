package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle

/**
 * Style overrides used by [MarkdownTypewriterRenderer] when painting a markdown stream.
 *
 * Every field is a plain [SpanStyle] / [Color] so the caller can swap in their own design system —
 * the defaults in [LlmTypewriterDefaults.markdownStyles] resolve against the active MaterialTheme.
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
    /** Style for math fragments (`$...$` inline, `$$...$$` display). Defaults to italic. */
    val math: SpanStyle = SpanStyle(),
    /** Color for TeX command names when rendered as fallback text. */
    val texCommand: Color = Color.Unspecified,
    /** Background tint for display-math (`$$...$$`) blocks. */
    val displayMathBackground: Color = Color.Unspecified,
    /** Stroke color for the bar in `\frac` and the vinculum in `\sqrt`. */
    val fractionBarColor: Color = Color.Unspecified,
    /** Scale multiplier applied to the base font size when rendering display math. */
    val displayScale: Float = 1.2f,
)

/**
 * Convenience view onto the math-related fields of [MarkdownStyles]. Passed to [RenderMath] so the
 * renderer doesn't carry the full markdown style record.
 */
@Immutable
data class MathStyles(
    val textColor: Color,
    val fractionBarColor: Color,
    val displayScale: Float,
)

/** Extracts the [MathStyles] view from a [MarkdownStyles]. */
internal fun MarkdownStyles.mathStyles(): MathStyles = MathStyles(
    textColor = math.color.takeIf { it != Color.Unspecified } ?: Color.Unspecified,
    fractionBarColor = fractionBarColor.takeIf { it != Color.Unspecified } ?: Color(0xFF000000),
    displayScale = displayScale,
)
