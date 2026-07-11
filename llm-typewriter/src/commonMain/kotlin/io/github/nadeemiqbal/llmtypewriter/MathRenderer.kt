package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mini LaTeX math renderer. Lays out a [MathNode] tree built from a TeX fragment.
 *
 * Layout invariants the user explicitly called out — all enforced here:
 *  - **上下标同列** ([MathNode.SubSup]): sub and sup are painted in the **same column**
 *    (one above the other, horizontally aligned), not side-by-side.
 *  - **大算子的字体大小** ([MathNode.BigOperator]): the operator glyph is rendered larger than
 *    the surrounding text, especially in display mode.
 *  - **块级 LaTeX 的缩放**: when [displayMode] is true, the whole fragment is rendered at
 *    [MathStyles.displayScale] × the base font size, matching LaTeX's display-vs-inline size bump.
 *  - **大算子的上下标位置** ([MathNode.BigOperator] limits in display mode): `lower` is painted
 *    below the glyph, `upper` is painted above — both horizontally centered on the glyph.
 */
@Composable
internal fun RenderMath(
    ast: MathNode,
    displayMode: Boolean,
    styles: MathStyles,
    modifier: Modifier = Modifier,
) {
    val base = LocalTextStyle.current
    val scaledFontSize = if (displayMode) base.fontSize * styles.displayScale else base.fontSize
    // Tight style (lineHeight = fontSize, Trim.Both, no font padding) keeps math compact so lines
    // with inline math don't end up taller than surrounding text ("行内 LaTeX 行距太远").
    // Serif font matches LaTeX tradition / 思源宋体 ("LaTeX 用思源宋体").
    val mathTextStyle = tightStyle(base, scaledFontSize).copy(
        color = styles.textColor,
        fontFamily = FontFamily.Serif,
    )
    // In display mode, center-align children vertically so a tall BigOperator (stacked Column with
    // limits) sits centered with the rest of the formula. Without this, Bottom alignment makes the
    // operator's lower limit the baseline — "大算子的主体和公式其余部分不在同一行".
    val rowAlignment = if (displayMode) Alignment.CenterVertically else Alignment.Bottom
    CompositionLocalProvider(LocalTextStyle provides mathTextStyle) {
        Row(modifier = modifier, verticalAlignment = rowAlignment) {
            RenderMathNode(ast, displayMode = displayMode, styles = styles)
        }
    }
}

/** Renders a single [MathNode]. Recurses for compound nodes. */
@Composable
private fun RenderMathNode(node: MathNode, displayMode: Boolean, styles: MathStyles) {
    when (node) {
        is MathNode.Text -> Text(text = node.text)
        is MathNode.Identifier -> Text(text = node.char)
        is MathNode.Number -> Text(text = node.text)
        is MathNode.Symbol -> Text(text = node.glyph)
        is MathNode.Command -> Text(text = "\\${node.name}")
        is MathNode.Group -> {
            val groupAlign = if (displayMode) Alignment.CenterVertically else Alignment.Bottom
            Row(verticalAlignment = groupAlign) {
                for (child in node.nodes) RenderMathNode(child, displayMode, styles)
            }
        }
        is MathNode.Space -> Spacer(modifier = spaceWidth(node.width))
        is MathNode.Superscript -> RenderScript(
            base = node.base, sub = null, sup = node.target, displayMode = displayMode, styles = styles,
        )
        is MathNode.Subscript -> RenderScript(
            base = node.base, sub = node.target, sup = null, displayMode = displayMode, styles = styles,
        )
        is MathNode.SubSup -> RenderScript(
            base = node.base, sub = node.sub, sup = node.sup, displayMode = displayMode, styles = styles,
        )
        is MathNode.Fraction -> RenderFraction(node, displayMode, styles)
        is MathNode.Sqrt -> RenderSqrt(node, displayMode, styles)
        is MathNode.Binom -> RenderBinom(node, displayMode, styles)
        is MathNode.BigOperator -> RenderBigOperator(node, displayMode, styles)
        is MathNode.Delimiter -> RenderDelimiter(node, displayMode, styles)
    }
}

/**
 * Lays out `^`/`_` scripts. When both [sub] and [sup] are present, they are placed in the **same
 * column** (one above the other, horizontally aligned) — the user's "上下标（同时上下标在同一列内）"
 * requirement.
 *
 * Positioning uses [Modifier.offset] instead of a fixed-height [Box]: sup is raised by
 * ~[SupRaiseScale]×fontSize above the baseline, sub is dropped by ~[SubDropScale]×fontSize below.
 * This avoids the "上标现在都快成下标了" issue caused by Box wrapping — with offset, the Row height
 * stays equal to the base text height so lines with inline math don't end up taller than
 * surrounding text ("行内 LaTeX 行距太远").
 */
@Composable
private fun RenderScript(
    base: MathNode,
    sub: MathNode?,
    sup: MathNode?,
    displayMode: Boolean,
    styles: MathStyles,
) {
    val baseStyle = LocalTextStyle.current
    val scriptStyle = tightStyle(baseStyle, baseStyle.fontSize * ScriptScale)
    val density = LocalDensity.current
    val resolvedFontSize = baseStyle.fontSize.let { fs ->
        if (fs.value > 0f) fs else 16.sp
    }
    val supRaise = with(density) { (resolvedFontSize * SupRaiseScale).toDp() }
    val subDrop = with(density) { (resolvedFontSize * SubDropScale).toDp() }

    Row(verticalAlignment = Alignment.Bottom) {
        RenderMathNode(base, displayMode, styles)
        if (sup != null && sub == null) {
            CompositionLocalProvider(LocalTextStyle provides scriptStyle) {
                Box(modifier = Modifier.offset(y = -supRaise)) {
                    RenderMathNode(sup, displayMode, styles)
                }
            }
        } else if (sub != null && sup == null) {
            CompositionLocalProvider(LocalTextStyle provides scriptStyle) {
                Box(modifier = Modifier.offset(y = subDrop)) {
                    RenderMathNode(sub, displayMode, styles)
                }
            }
        } else if (sup != null && sub != null) {
            // Both scripts in the same column (same x) — sup stacked above sub.
            CompositionLocalProvider(LocalTextStyle provides scriptStyle) {
                Box {
                    Box(modifier = Modifier.align(Alignment.TopStart).offset(y = -supRaise)) {
                        RenderMathNode(sup, displayMode, styles)
                    }
                    Box(modifier = Modifier.align(Alignment.BottomStart).offset(y = subDrop)) {
                        RenderMathNode(sub, displayMode, styles)
                    }
                }
            }
        }
    }
}

/**
 * Vertical fraction with a horizontal bar. Numerator and denominator are centered over each
 * other; the bar spans the column width (which is the wider of the two). Rendered at a slightly
 * reduced font size so the fraction as a whole visually matches the surrounding text height.
 */
@Composable
private fun RenderFraction(node: MathNode.Fraction, displayMode: Boolean, styles: MathStyles) {
    val outer = LocalTextStyle.current
    val inner = tightStyle(outer, outer.fontSize * FracScale)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CompositionLocalProvider(LocalTextStyle provides inner) {
            RenderMathNode(node.numerator, displayMode, styles)
        }
        Spacer(modifier = Modifier.height(1.dp))
        // Bar — thin line spanning the column width.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((outer.fontSize.value / 18f).coerceAtLeast(0.5f).dp)
                .background(styles.fractionBarColor),
        )
        Spacer(modifier = Modifier.height(1.dp))
        CompositionLocalProvider(LocalTextStyle provides inner) {
            RenderMathNode(node.denominator, displayMode, styles)
        }
    }
}

/**
 * Square-root sign with a base. We use the Unicode `√` glyph scaled to roughly the base height,
 * with a thin overline drawn on top of the base for the vinculum. The optional root index sits
 * small and to the lower-left of the radical sign for `\sqrt[n]{x}`.
 */
@Composable
private fun RenderSqrt(node: MathNode.Sqrt, displayMode: Boolean, styles: MathStyles) {
    val outer = LocalTextStyle.current
    Row(verticalAlignment = Alignment.Bottom) {
        if (node.index != null) {
            val indexStyle = tightStyle(outer, outer.fontSize * 0.6f)
            CompositionLocalProvider(LocalTextStyle provides indexStyle) {
                RenderMathNode(node.index, displayMode, styles)
            }
            Spacer(modifier = Modifier.width(1.dp))
        }
        Text(
            text = "√",
            style = tightStyle(outer, outer.fontSize * 1.6f),
        )
        Box {
            RenderMathNode(node.base, displayMode, styles)
            // Vinculum — thin line over the base width.
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .height((outer.fontSize.value / 16f).coerceAtLeast(0.5f).dp)
                    .background(styles.fractionBarColor),
            )
        }
    }
}

/** `\binom{n}{k}` — like a fraction but with no bar and paren delimiters. */
@Composable
private fun RenderBinom(node: MathNode.Binom, displayMode: Boolean, styles: MathStyles) {
    val outer = LocalTextStyle.current
    val inner = tightStyle(outer, outer.fontSize * FracScale)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "(", style = outer)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CompositionLocalProvider(LocalTextStyle provides inner) {
                RenderMathNode(node.n, displayMode, styles)
            }
            CompositionLocalProvider(LocalTextStyle provides inner) {
                RenderMathNode(node.k, displayMode, styles)
            }
        }
        Text(text = ")", style = outer)
    }
}

/**
 * Large operator with optional limits (`\sum_{i=0}^{n}`, `\int_a^b`, …). Two layout modes:
 *  - **Display mode**: the glyph is rendered oversized; `lower` is centered below, `upper`
 *    centered above. This is the "大算子的上下标位置" requirement.
 *  - **Inline mode**: glyph at slightly larger font; scripts render to the side as a column.
 *
 * In display mode, tight [TextStyle]s (with [LineHeightStyle.Trim.Both] and
 * [PlatformTextStyle.includeFontPadding] = false) are applied to the glyph and limits so they sit
 * close together — without this, the default line-height padding pushes the limits far from the
 * glyph ("大算子的上下标离主体太远").
 */
@Composable
private fun RenderBigOperator(node: MathNode.BigOperator, displayMode: Boolean, styles: MathStyles) {
    val outer = LocalTextStyle.current
    val glyphSize = if (displayMode) outer.fontSize * DisplayOperatorScale else outer.fontSize * InlineOperatorScale
    val hasLimits = node.lower != null || node.upper != null

    if (displayMode && hasLimits) {
        // Stacked: upper above glyph, glyph in middle, lower below — all horizontally centered.
        // This is the explicit "大算子的上下标位置" invariant: limits go directly above/below the
        // operator in display mode, not to the side.
        val tightLimitStyle = tightStyle(outer, outer.fontSize * ScriptScale)
        val tightGlyphStyle = tightStyle(outer, glyphSize)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (node.upper != null) {
                CompositionLocalProvider(LocalTextStyle provides tightLimitStyle) {
                    RenderMathNode(node.upper, displayMode, styles)
                }
            }
            Text(text = node.glyph, style = tightGlyphStyle)
            if (node.lower != null) {
                CompositionLocalProvider(LocalTextStyle provides tightLimitStyle) {
                    RenderMathNode(node.lower, displayMode, styles)
                }
            }
        }
    } else {
        // Inline mode (or no limits): glyph + side scripts column.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = node.glyph, style = tightStyle(outer, glyphSize))
            if (node.upper != null || node.lower != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (node.upper != null) {
                        CompositionLocalProvider(LocalTextStyle provides tightStyle(outer, outer.fontSize * ScriptScale)) {
                            RenderMathNode(node.upper, displayMode, styles)
                        }
                    }
                    if (node.lower != null) {
                        CompositionLocalProvider(LocalTextStyle provides tightStyle(outer, outer.fontSize * ScriptScale)) {
                            RenderMathNode(node.lower, displayMode, styles)
                        }
                    }
                }
            }
        }
    }
}

/** `\left( ... \right)` — content between delimiter glyphs. */
@Composable
private fun RenderDelimiter(node: MathNode.Delimiter, displayMode: Boolean, styles: MathStyles) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (node.left.isNotEmpty()) {
            Text(text = node.left)
        }
        RenderMathNode(node.content, displayMode, styles)
        if (node.right.isNotEmpty()) {
            Text(text = node.right)
        }
    }
}

private fun spaceWidth(width: MathNode.SpaceWidth): Modifier = when (width) {
    MathNode.SpaceWidth.Thin -> Modifier.width(2.dp)
    MathNode.SpaceWidth.Medium -> Modifier.width(4.dp)
    MathNode.SpaceWidth.Thick -> Modifier.width(6.dp)
    MathNode.SpaceWidth.Quad -> Modifier.width(12.dp)
}

/** Visual tuning constants. Kept here so they're easy to find; not part of the public API. */
private const val ScriptScale = 0.7f
private const val SupRaiseScale = 0.5f
private const val SubDropScale = 0.2f
private const val FracScale = 0.85f
private const val DisplayOperatorScale = 2.0f
private const val InlineOperatorScale = 1.2f

/**
 * Builds a [TextStyle] with [lineHeight] equal to [fontSize] and [LineHeightStyle.Trim.Both] so the
 * text has no extra top/bottom padding. Used for big-operator limits in display mode so the upper,
 * glyph, and lower sit close together instead of being pushed apart by default line-height padding.
 */
private fun tightStyle(base: TextStyle, fontSize: TextUnit): TextStyle = base.copy(
    fontSize = fontSize,
    lineHeight = fontSize,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
