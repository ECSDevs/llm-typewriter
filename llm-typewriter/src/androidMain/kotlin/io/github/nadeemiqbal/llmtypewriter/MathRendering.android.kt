package io.github.nadeemiqbal.llmtypewriter

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import com.agog.mathdisplay.MTMathView

/**
 * Android actual: delegates to [AndroidMath](https://github.com/gregcockroft/AndroidMath)'s
 * [MTMathView]. The view wraps native Freetype rendering with Latin Modern Math / Tex Gyre
 * Termes / XITS Math fonts, so fractions, big-operator stacked limits, roots, and the rest of the
 * LaTeX grammar render with correct metrics without us having to re-implement them.
 *
 * `labelMode` is set to:
 *  - [MTMathView.MTMathViewMode.KMTMathViewModeDisplay] for block-level `$$…$$` — renders
 *    fractions/limits in display style.
 *  - [MTMathView.MTMathViewMode.KMTMathViewModeText] for inline `$…$` — renders in text style
 *    so the equation sits inline with surrounding text without dominating the line height.
 *
 * The [fontSize] is converted from sp to device pixels (MTMathView expects px).
 */
@Composable
actual fun RenderPlatformMath(
    latex: String,
    displayMode: Boolean,
    textColor: Color,
    fontSize: TextUnit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    // MTMathView.fontSize is in device pixels; convert from the incoming sp value.
    val fontSizePx = with(density) { fontSize.toPx() }
    val labelMode = if (displayMode) {
        MTMathView.MTMathViewMode.KMTMathViewModeDisplay
    } else {
        MTMathView.MTMathViewMode.KMTMathViewModeText
    }
    val textColorArgb = textColor.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MTMathView(ctx).apply {
                this.latex = latex
                this.labelMode = labelMode
                this.textColor = textColorArgb
                this.fontSize = fontSizePx
                this.textAlignment = MTMathView.MTTextAlignment.KMTTextAlignmentLeft
            }
        },
        update = { view ->
            view.latex = latex
            view.labelMode = labelMode
            view.textColor = textColorArgb
            view.fontSize = fontSizePx
        },
    )
}

/**
 * Android actual: creates a detached [MTMathView], configures it identically to [RenderPlatformMath]
 * (same [latex], [labelMode][MTMathView.labelMode], [fontSize][MTMathView.fontSize]), and runs a
 * synthetic [View.measure] pass with `UNSPECIFIED` constraints so the view reports its intrinsic
 * content size. The measured width/height (in px) are returned.
 *
 * The view is never attached to a window — `measure()` is sufficient for `MTMathView` to lay out
 * its equation because it computes its own dimensions from the parsed LaTeX and font metrics.
 * The result is cached via [remember] keyed on (latex, displayMode, fontSizePx) so a given
 * equation is only laid out once across recompositions.
 */
@Composable
actual fun measurePlatformMath(
    latex: String,
    displayMode: Boolean,
    fontSize: TextUnit,
): IntSize {
    if (latex.isEmpty()) return IntSize.Zero
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.toPx() }
    val labelMode = if (displayMode) {
        MTMathView.MTMathViewMode.KMTMathViewModeDisplay
    } else {
        MTMathView.MTMathViewMode.KMTMathViewModeText
    }
    return remember(latex, displayMode, fontSizePx) {
        val view = MTMathView(context)
        view.latex = latex
        view.labelMode = labelMode
        view.fontSize = fontSizePx
        view.textAlignment = MTMathView.MTTextAlignment.KMTTextAlignmentLeft
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        IntSize(view.measuredWidth, view.measuredHeight)
    }
}
