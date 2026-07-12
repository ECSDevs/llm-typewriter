package io.github.nadeemiqbal.llmtypewriter.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nadeemiqbal.llmtypewriter.LlmTypewriterDefaults
import io.github.nadeemiqbal.llmtypewriter.SpeedCurve
import io.github.nadeemiqbal.llmtypewriter.StreamingTypewriter
import io.github.nadeemiqbal.llmtypewriter.ThinkingDots
import io.github.nadeemiqbal.llmtypewriter.TypewriterCursor
import io.github.nadeemiqbal.llmtypewriter.TypewriterPhase
import io.github.nadeemiqbal.llmtypewriter.isStreaming
import io.github.nadeemiqbal.llmtypewriter.rememberMarkdownTypewriterRenderer
import io.github.nadeemiqbal.llmtypewriter.rememberStreamingTypewriterState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Sample app that demos a fake LLM streaming response into the typewriter, with markdown + code. */
@Composable
fun SampleApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DemoScreen()
        }
    }
}

private val FakeResponses = listOf(
    """
        # Hello there!

        I'm a **fake assistant** that streams text one token at a time.

        Here's some _Kotlin_ code:

        ```kotlin
        fun greet(name: String): String {
            // Greet the user — simple as that.
            return "Hello, ${'$'}name!"
        }
        ```

        Notice how the code block builds up *progressively*, with **syntax highlighting** that
        appears as the tokens stream in. Pretty, right?

        - Headings render once the line completes
        - Bold/italic flip the moment the closing delimiter arrives
        - Inline `code` works too

        Tap me to skip to the end.
    """.trimIndent(),

    """
        Sure — here's a quick `Python` example:

        ```python
        def fib(n):
            # Classic recursive Fibonacci
            if n < 2:
                return n
            return fib(n - 1) + fib(n - 2)
        ```

        And a [link to the docs](https://kotlinlang.org).
    """.trimIndent(),

    """
        Plain text response — no markdown, just streaming words with a `Natural` speed curve so
        the pauses on punctuation feel like a real typist. Notice the slight hesitation here.
        And here. And here!
    """.trimIndent(),

    """
        ## Math rendering

        Inline math works mid-sentence: the Pythagorean theorem ${'$'}a^2 + b^2 = c^2${'$'} is
        rendered inline, while display math gets its own block:

        ${'$'}${'$'}\sum_{i=1}^{n} i^2 = \frac{n(n+1)(2n+1)}{6}${'$'}${'$'}

        Sub and superscript line up in the **same column** — ${'$'}x_a^b${'$'} — and big
        operators stack their limits above and below the glyph in display mode:

        ${'$'}${'$'}\int_0^{\infty} e^{-x^2} dx = \frac{\sqrt{\pi}}{2}${'$'}${'$'}

        Greek letters (${'$'}\alpha${'$'}, ${'$'}\beta${'$'}, ${'$'}\pi${'$'}), roots
        ${'$'}\sqrt{x^2 + y^2}${'$'}, and fractions all render as they stream in.
    """.trimIndent(),
)

@Composable
private fun DemoScreen() {
    val state = rememberStreamingTypewriterState()
    val markdownRenderer = rememberMarkdownTypewriterRenderer(state)

    val tokenChannel = remember { MutableSharedFlow<String>(extraBufferCapacity = 64) }
    val scope = rememberCoroutineScope()

    var responseIndex by remember { mutableStateOf(0) }
    var currentCurve by remember { mutableStateOf(SpeedCurve.Natural) }
    var currentCurveName by remember { mutableStateOf("Natural") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "LlmTypewriter — Compose",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Streams a Flow<String> of fake LLM tokens with live markdown + syntax-highlighted " +
                "code. Tap the rendered area to skip to end.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Compact, horizontally-scrollable button bar — narrow padding + small text so the row
        // doesn't dominate the screen on a phone and the 4th button stays reachable.
        val compactPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        val labelStyle = MaterialTheme.typography.labelSmall
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = {
                    state.reset()
                    scope.launch {
                        streamFakeResponse(FakeResponses[responseIndex % FakeResponses.size], tokenChannel)
                    }
                    responseIndex++
                },
                contentPadding = compactPadding,
            ) { Text("New stream", style = labelStyle) }
            OutlinedButton(
                onClick = { state.stop() },
                enabled = state.isStreaming,
                contentPadding = compactPadding,
            ) { Text("Stop", style = labelStyle) }
            OutlinedButton(
                onClick = { state.resume() },
                enabled = state.phase == TypewriterPhase.Stopped,
                contentPadding = compactPadding,
            ) { Text("Resume", style = labelStyle) }
            // Cycles SpeedCurve.Natural → Linear → EaseOut. "Natural" pauses on punctuation
            // (`.!?,;:`) and newlines to simulate human typing rhythm; the other two are
            // constant / whitespace-stretched cadences for comparison.
            OutlinedButton(
                onClick = {
                    currentCurve = nextSpeedCurve(currentCurve)
                    currentCurveName = nextSpeedCurveName(currentCurveName)
                },
                contentPadding = compactPadding,
            ) {
                Text("Speed: $currentCurveName", style = labelStyle)
            }
        }

        val scrollState = rememberScrollState()

        // Auto-scroll to the bottom while the typewriter reveals new tokens.
        LaunchedEffect(state.revealed.length) {
            if (state.isStreaming) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                )
                .verticalScroll(scrollState)
                .padding(16.dp)
                .heightIn(min = 120.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (state.revealed.isEmpty() && state.phase != TypewriterPhase.Done) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThinkingDots()
                    Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StreamingTypewriter(
                tokens = tokenChannel,
                state = state,
                renderer = markdownRenderer,
                cursor = TypewriterCursor.Line,
                baseDelayMs = LlmTypewriterDefaults.DefaultBaseDelayMs,
                speedCurve = currentCurve,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Phase: ${state.phase} · pending chars: ${state.pendingChars}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // List of past prompts — purely cosmetic.
        val history = remember { mutableStateListOf("New stream demo") }
        val listState = rememberLazyListState()
        LaunchedEffect(responseIndex) {
            if (responseIndex > 0) {
                history.add("Response #$responseIndex")
                listState.animateScrollToItem(history.lastIndex)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(history) { item ->
                Text(
                    "· $item",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Push the given response string into the shared flow as word-sized tokens at ~80ms cadence. */
private suspend fun streamFakeResponse(response: String, channel: MutableSharedFlow<String>) {
    val pieces = mutableListOf<String>()
    val builder = StringBuilder()
    for (ch in response) {
        builder.append(ch)
        if (ch == ' ' || ch == '\n') {
            pieces.add(builder.toString())
            builder.clear()
        }
    }
    if (builder.isNotEmpty()) pieces.add(builder.toString())

    for (piece in pieces) {
        delay(80L)
        channel.emit(piece)
    }
}

private fun nextSpeedCurve(curve: SpeedCurve): SpeedCurve = when (curve) {
    SpeedCurve.Linear -> SpeedCurve.EaseOut
    SpeedCurve.EaseOut -> SpeedCurve.Natural
    else -> SpeedCurve.Linear
}

private fun nextSpeedCurveName(name: String): String = when (name) {
    "Linear" -> "EaseOut"
    "EaseOut" -> "Natural"
    else -> "Linear"
}

@Suppress("unused")
private val _retainSharedFlow: SharedFlow<String>? = null
