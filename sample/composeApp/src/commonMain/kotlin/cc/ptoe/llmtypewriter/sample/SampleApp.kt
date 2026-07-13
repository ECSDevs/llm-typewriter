package cc.ptoe.llmtypewriter.sample

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
import cc.ptoe.llmtypewriter.LlmTypewriterDefaults
import cc.ptoe.llmtypewriter.SpeedCurve
import cc.ptoe.llmtypewriter.StreamingTypewriter
import cc.ptoe.llmtypewriter.ThinkingDots
import cc.ptoe.llmtypewriter.TypewriterPhase
import cc.ptoe.llmtypewriter.isStreaming
import cc.ptoe.llmtypewriter.rememberMarkdownTypewriterRenderer
import cc.ptoe.llmtypewriter.rememberStreamingTypewriterState
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

private data class DemoStream(val name: String, val content: String)

private val DemoStreams = listOf(
    DemoStream(
        name = "Mixed showcase",
        content = """
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
            - Block quotes now render as quoted callouts:
              > Streaming markdown stays prefix-stable while the quote grows.
              > Multiple `>` lines at the same level share one box.
              >> Nested quotes render as a nested box inside.
            - Split lines render as full-width dividers:

            ---

            - Images are handed to the configurable image renderer: ![Random sample image](https://picsum.photos/200)

            Tap me to skip to the end.
        """.trimIndent(),
    ),
    DemoStream(
        name = "Markdown",
        content = """
            ## Inline Markdown

            Markdown supports **bold**, _italic_, **_bold italic_**, ~~strikethrough~~, and
            `inline code`. It also supports [links to the docs](https://kotlinlang.org).

            Block quotes become styled callouts:
            > Streaming Markdown stays prefix-stable while the quote grows.
            > A second `>` line joins the same box.

            Nested quotes render inside the parent:
            > Outer quote.
            >> Inner quote.

            Split lines render as full-width dividers:

            ---
        """.trimIndent(),
    ),
    DemoStream(
        name = "Images",
        content = """
            ## Markdown images

            Images are parsed from Markdown and handed to the configurable image renderer:

            ![Random sample image](https://picsum.photos/200)

            Applications can replace the default Coil-backed loader with their own renderer.
        """.trimIndent(),
    ),
    DemoStream(
        name = "Code blocks",
        content = """
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
    ),
    DemoStream(
        name = "Plain text",
        content = """
            Plain text response — no markdown, just streaming words with a Natural speed curve so
            the pauses on punctuation feel like a real typist. Notice the slight hesitation here.
            And here. And here!
        """.trimIndent(),
    ),
    DemoStream(
        name = "Math",
        content = """
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
    ),
    DemoStream(
        name = "Lists",
        content = """
            ## Lists

            Here's an **unordered** list with inline formatting:

            - First item with `inline code`
            - Second item with _italic text_
            - Third item with an inline equation: ${'$'}E = mc^2${'$'}
            - A nested list demonstrates indentation:
              - Sub-item at depth one
              - Another sub-item
                - And one more level deeper
            - Back to the top level

            And an **ordered** list (note how numbers are preserved):

            3. Starting from three, not one
            4. The next item follows sequentially
            5. Each item supports formatting too — **bold**, _italic_, `code`

            A **task** list (GFM checkbox syntax — `- [ ]` / `- [x]`):

            - [x] Stream tokens as a live typewriter
            - [x] Render Markdown with syntax-highlighted code
            - [ ] Support nested task items:
              - [x] Parse `- [ ]` / `- [x]` markers
              - [ ] Tappable checkboxes (display-only for now)
            - [ ] Ship the next release

            Lists can also interrupt paragraphs without a blank line,
            - like this
            - and this
        """.trimIndent(),
    ),
    DemoStream(
        name = "Tables",
        content = """
            ## Tables

            GFM tables render with aligned columns and a bordered grid:

            | Feature | Status | Notes |
            | :--- | :---: | ---: |
            | Headings | Yes | `#`–`######` |
            | Bold/italic | Yes | `**bold**` |
            | Code blocks | Yes | fenced ``` |
            | Math | Yes | ${'$'}x^2${'$'} inline |
            | **Tables** | **Yes** | this one! |

            Cells support inline formatting — **bold**, _italic_, `code`, and links like
            [Kotlin](https://kotlinlang.org) all work inside cells.

            | Command | Description |
            | --- | --- |
            | `git status` | Show working tree status |
            | `git add .` | Stage all changes |
            | `git commit` | Record changes to the repo |

            Tables build up row-by-row as the stream arrives — watch each row appear as it's
            typed.
        """.trimIndent(),
    ),
    DemoStream(
        name = "Footnotes",
        content = """
            ## Footnotes

            Streaming Markdown can cite sources[^source] or add a second note[^detail] without
            interrupting the paragraph.

            The references become superscripts, while definitions render below the response.

            [^source]: This definition is parsed as it arrives and supports **bold** text.
            [^detail]: Footnotes are prefix-stable, just like the rest of the typewriter stream.
        """.trimIndent(),
    ),
)

@Composable
private fun DemoScreen() {
    val state = rememberStreamingTypewriterState()
    val markdownRenderer = rememberMarkdownTypewriterRenderer(state)

    val tokenChannel = remember { MutableSharedFlow<String>(extraBufferCapacity = 64) }
    val scope = rememberCoroutineScope()

    var currentStreamIndex by remember { mutableStateOf(0) }
    var playCount by remember { mutableStateOf(0) }
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

        // Compact, horizontally-scrollable button bars — narrow padding + small text so the rows
        // don't dominate the screen on a phone and every button stays reachable.
        val compactPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        val labelStyle = MaterialTheme.typography.labelSmall

        // Stream selector — one chip per demo stream. Tapping a chip resets the typewriter and
        // plays that stream; the currently-loaded stream is shown as a filled button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DemoStreams.forEachIndexed { index, stream ->
                val isSelected = index == currentStreamIndex
                val onClick: () -> Unit = {
                    state.reset()
                    currentStreamIndex = index
                    playCount++
                    scope.launch { streamFakeResponse(stream.content, tokenChannel) }
                }
                if (isSelected) {
                    Button(onClick = onClick, contentPadding = compactPadding) {
                        Text(stream.name, style = labelStyle)
                    }
                } else {
                    OutlinedButton(onClick = onClick, contentPadding = compactPadding) {
                        Text(stream.name, style = labelStyle)
                    }
                }
            }
        }

        // Playback controls.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
        LaunchedEffect(playCount) {
            if (playCount > 0) {
                history.add(DemoStreams[currentStreamIndex].name)
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
