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
)
