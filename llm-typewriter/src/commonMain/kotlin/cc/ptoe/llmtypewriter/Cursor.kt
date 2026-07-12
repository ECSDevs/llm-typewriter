package cc.ptoe.llmtypewriter

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * A factory of cursor composables. Callers can pick one of the built-in shapes or supply their
 * own — anything that's a `@Composable () -> Unit` works.
 */
sealed class TypewriterCursor {

    @Composable abstract fun Render()

    /** A thick block cursor (▮). Default. */
    data object Block : TypewriterCursor() {
        @Composable override fun Render() {
            val alpha = rememberBlinkingAlpha()
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 16.dp)
                    .alpha(alpha)
                    .background(LocalContentColor.current),
            )
        }
    }

    /** A thin vertical line (|). */
    data object Line : TypewriterCursor() {
        @Composable override fun Render() {
            val alpha = rememberBlinkingAlpha()
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .alpha(alpha)
                    .background(LocalContentColor.current),
            )
        }
    }

    /** An underscore (_). */
    data object Underscore : TypewriterCursor() {
        @Composable override fun Render() {
            val alpha = rememberBlinkingAlpha()
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 2.dp)
                    .alpha(alpha)
                    .background(LocalContentColor.current),
            )
        }
    }

    /** A non-blinking, never-shown cursor — useful when the host wants no caret at all. */
    data object None : TypewriterCursor() {
        @Composable override fun Render() { /* no-op */ }
    }

    /** A custom cursor that delegates to any `@Composable` lambda. */
    class Custom(val content: @Composable () -> Unit) : TypewriterCursor() {
        @Composable override fun Render() = content()
    }
}

@Composable
private fun rememberBlinkingAlpha(periodMs: Long = LlmTypewriterDefaults.DefaultCursorBlinkMs): Float {
    val transition = rememberInfiniteTransition(label = "cursor-blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs.toInt() / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )
    return alpha
}

