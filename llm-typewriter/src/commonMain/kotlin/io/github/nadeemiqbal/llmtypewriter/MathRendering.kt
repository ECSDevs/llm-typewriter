package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit

/**
 * Platform-specific LaTeX math renderer.
 *
 * On Android this delegates to [AndroidMath](https://github.com/gregcockroft/AndroidMath)'s
 * `MTMathView`, which uses native Freetype to render LaTeX with proper font metrics (Latin
 * Modern Math / Tex Gyre Termes / XITS Math). The library's own parser handles the full LaTeX
 * grammar — fractions, big operators with stacked limits, roots, etc. — so the local `TexParser`
 * and `MathAst` are no longer used for rendering; they're retained for prefix-stability testing
 * and future programmatic AST use.
 *
 * Called once a math fragment is complete (the closing `$` or `$$` has arrived). Before that, the
 * streaming Markdown parser emits the partial input as plain text — so this function is never
 * called with a half-formed LaTeX string. This matches the user's requirement: "一旦一个 inline/block
 * LaTeX 完成，就丢给 AndroidMath 渲染。在此之前直接显示原文本即可".
 *
 * @param latex The complete LaTeX fragment, e.g. `\frac{a}{b}` (no surrounding `$`).
 * @param displayMode `true` for `$$…$$` (block-level, oversized); `false` for inline `$…$`.
 * @param textColor Color to apply to the rendered equation.
 * @param fontSize Font size for the equation. Display mode is additionally scaled by
 *   [MarkdownStyles.displayScale] by the caller.
 * @param modifier Layout modifier.
 */
@Composable
internal expect fun RenderPlatformMath(
    latex: String,
    displayMode: Boolean,
    textColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
)

/**
 * Measures the rendered size (in pixels) of a LaTeX fragment without displaying it. Used by the
 * inline-math renderer to size the [androidx.compose.foundation.text.InlineTextContent]
 * placeholder to the equation's actual width instead of a fixed estimate, so each inline
 * equation gets its own width and surrounding text reflows correctly.
 *
 * Contractually returns `IntSize.Zero` only when [latex] is empty. Otherwise the platform backend
 * lays out the equation (off-screen) and reports its measured width/height. The result is cached
 * per (latex, displayMode, fontSize) triple via [androidx.compose.runtime.remember].
 *
 * @param latex The complete LaTeX fragment (no surrounding `$`).
 * @param displayMode `true` for display style, `false` for inline text style.
 * @param fontSize Font size in sp.
 * @return Measured size in device pixels.
 */
@Composable
internal expect fun measurePlatformMath(
    latex: String,
    displayMode: Boolean,
    fontSize: TextUnit,
): IntSize
