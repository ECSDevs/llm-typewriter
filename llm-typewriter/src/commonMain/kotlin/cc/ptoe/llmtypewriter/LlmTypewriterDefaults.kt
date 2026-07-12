package cc.ptoe.llmtypewriter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Default tuning values used by [StreamingTypewriter] when the caller doesn't override them. */
object LlmTypewriterDefaults {

    /** Default reveal cadence: ~50 chars/second, a comfortable read for chat UIs. */
    const val DefaultBaseDelayMs: Long = 18L

    /** Default cursor blink period (full on→off→on cycle). */
    const val DefaultCursorBlinkMs: Long = 900L

    /**
     * Maximum buffered (unrevealed) characters before the typewriter starts collapsing — when the
     * model streams much faster than the reveal can keep up, this caps memory growth. The reveal
     * loop reads from the head of the buffer; bursts larger than this just get flushed
     * progressively.
     */
    const val DefaultBufferSoftCap: Int = 4000

    /** Default markdown styling — wired only when the caller uses [MarkdownTypewriterRenderer]. */
    @Composable
    @ReadOnlyComposable
    fun markdownStyles(): MarkdownStyles = MarkdownStyles(
        bold = SpanStyle(fontWeight = FontWeight.SemiBold),
        italic = SpanStyle(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Default).copy(
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        ),
        code = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        link = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
        heading = SpanStyle(fontWeight = FontWeight.Bold),
        strikethrough = SpanStyle(textDecoration = TextDecoration.LineThrough),
        codeBlockBackground = MaterialTheme.colorScheme.surfaceVariant,
        codeBlockText = MaterialTheme.colorScheme.onSurfaceVariant,
        codeBlockKeyword = MaterialTheme.colorScheme.primary,
        codeBlockString = MaterialTheme.colorScheme.tertiary,
        codeBlockComment = MaterialTheme.colorScheme.outline,
        codeBlockNumber = MaterialTheme.colorScheme.secondary,
        math = SpanStyle(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        displayMathBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        displayScale = 1.2f,
    )

    /** The same as [markdownStyles] but takes an explicit color scheme for testing. */
    fun markdownStyles(
        primary: Color,
        secondary: Color,
        tertiary: Color,
        surfaceVariant: Color,
        onSurfaceVariant: Color,
        outline: Color,
    ): MarkdownStyles = MarkdownStyles(
        bold = SpanStyle(fontWeight = FontWeight.SemiBold),
        italic = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        code = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = surfaceVariant,
            color = onSurfaceVariant,
        ),
        link = SpanStyle(color = primary, textDecoration = TextDecoration.Underline),
        heading = SpanStyle(fontWeight = FontWeight.Bold),
        strikethrough = SpanStyle(textDecoration = TextDecoration.LineThrough),
        codeBlockBackground = surfaceVariant,
        codeBlockText = onSurfaceVariant,
        codeBlockKeyword = primary,
        codeBlockString = tertiary,
        codeBlockComment = outline,
        codeBlockNumber = secondary,
        math = SpanStyle(),
        displayMathBackground = surfaceVariant.copy(alpha = 0.4f),
        displayScale = 1.2f,
    )
}
