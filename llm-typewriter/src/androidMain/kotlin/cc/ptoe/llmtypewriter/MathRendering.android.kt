package cc.ptoe.llmtypewriter

import android.util.LruCache
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
import kotlin.math.roundToInt

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
            // Only write back when a value actually changes — `setLatex` re-parses the whole
            // expression (the dominant cost in MTMathView), and `setTextColor`/`setFontSize`
            // trigger a re-render. Skipping redundant writes avoids that cost on every
            // recomposition (which happens on every revealed character during streaming).
            if (view.latex != latex) view.latex = latex
            if (view.labelMode != labelMode) view.labelMode = labelMode
            if (view.textColor != textColorArgb) view.textColor = textColorArgb
            if (view.fontSize != fontSizePx) view.fontSize = fontSizePx
        },
    )
}

/**
 * Process-wide LRU cache of measured math sizes. Keyed on a (latex, displayMode, fontSizePx)
 * triple. A given equation is only laid out once for the lifetime of the app — across
 * recompositions, across Composable instances, across different chat bubbles — instead of once
 * per recomposition (the old `remember`-only cache was scoped to a single Composable call site
 * and was lost whenever the inline-run block was rebuilt).
 *
 * 256 entries is plenty for a chat session; each entry is a few hundred bytes.
 */
private val mathMeasureCache = LruCache<MathMeasureKey, IntSize>(256)

/** Key for [mathMeasureCache]. [fontSizePx] is rounded so floating-point drift doesn't miss. */
private data class MathMeasureKey(
    val latex: String,
    val displayMode: Boolean,
    val fontSizePx: Int,
)

/**
 * Android actual: creates a detached [MTMathView], configures it identically to [RenderPlatformMath]
 * (same [latex], [labelMode][MTMathView.labelMode], [fontSize][MTMathView.fontSize]), and runs a
 * synthetic [View.measure] pass with `UNSPECIFIED` constraints so the view reports its intrinsic
 * content size. The measured width/height (in px) are returned.
 *
 * The view is never attached to a window — `measure()` is sufficient for `MTMathView` to lay out
 * its equation because it computes its own dimensions from the parsed LaTeX and font metrics.
 *
 * Results are cached in a process-wide [LruCache] ([mathMeasureCache]) keyed on
 * (latex, displayMode, fontSizePx), so the same equation is measured at most once for the app's
 * lifetime. This matters because the inline-run renderer re-measures every math segment on every
 * recomposition — without the cross-recomposition cache, an N-character stream with M inline
 * equations paid M·N measure calls.
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
    val key = MathMeasureKey(latex, displayMode, fontSizePx.roundToInt())

    mathMeasureCache.get(key)?.let { return it }

    val labelMode = if (displayMode) {
        MTMathView.MTMathViewMode.KMTMathViewModeDisplay
    } else {
        MTMathView.MTMathViewMode.KMTMathViewModeText
    }
    val view = MTMathView(context)
    view.latex = latex
    view.labelMode = labelMode
    view.fontSize = fontSizePx
    view.textAlignment = MTMathView.MTTextAlignment.KMTTextAlignmentLeft
    val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    view.measure(unspecified, unspecified)
    val size = IntSize(view.measuredWidth, view.measuredHeight)
    mathMeasureCache.put(key, size)
    return size
}
