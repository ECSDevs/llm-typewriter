package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Three pulsing dots — the classic "the assistant is thinking" indicator. Show this while
 * `state.phase == TypewriterPhase.Waiting || state.phase == TypewriterPhase.Idle` and the user
 * hasn't yet seen the first token.
 */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier, dotSize: androidx.compose.ui.unit.Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val a by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha-a",
    )
    val b by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 700, delayMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha-b",
    )
    val c by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 700, delayMillis = 360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha-c",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Dot(dotSize, a)
        Dot(dotSize, b)
        Dot(dotSize, c)
    }
}

@Composable
private fun Dot(dotSize: androidx.compose.ui.unit.Dp, alpha: Float) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(dotSize)
            .clip(CircleShape)
            .alpha(alpha)
            .background(LocalContentColor.current),
    )
}
