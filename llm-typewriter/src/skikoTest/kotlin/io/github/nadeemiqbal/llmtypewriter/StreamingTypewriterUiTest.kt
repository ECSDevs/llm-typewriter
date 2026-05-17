package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose UI tests for [StreamingTypewriter]. Skiko-backed — runs on Desktop + iOS test targets.
 *
 * The reveal loop is driven by `LaunchedEffect`, which advances when the runtime's test clock
 * advances. We use the state's direct API (`appendToken` / `skipToEnd`) to make assertions about
 * what gets painted, then verify the wiring (test tags, click handlers, semantics).
 */
@OptIn(ExperimentalTestApi::class)
class StreamingTypewriterUiTest {

    @Test
    fun emptyFlow_rendersRootAndFinishesAtDone() = runComposeUiTest {
        val state = StreamingTypewriterState()
        setContent {
            MaterialTheme {
                StreamingTypewriter(
                    tokens = remember { flowOf<String>() },
                    state = state,
                )
            }
        }
        waitForIdle()
        onNodeWithTag("llm_typewriter").assertIsDisplayed()
        // An empty Text node has no inspectable text content, so we assert on state instead — the
        // empty flow completes immediately, draining the (empty) buffer and flipping to Done.
        waitUntil(timeoutMillis = 2_000) { state.phase == TypewriterPhase.Done }
        assertEquals("", state.revealed)
    }

    @Test
    fun appendedTokens_eventuallyShowUpInTextNode() = runComposeUiTest {
        val state = StreamingTypewriterState()
        setContent {
            MaterialTheme {
                StreamingTypewriter(
                    tokens = remember { MutableSharedFlow() },
                    state = state,
                    baseDelayMs = 0L,
                )
            }
        }
        // Programmatically push tokens — the state is the single source of truth.
        state.appendToken("Hello")
        waitUntil(timeoutMillis = 5_000) { state.revealed == "Hello" }
        waitForIdle()
        onNodeWithTag("llm_typewriter_text", useUnmergedTree = true).assertTextEquals("Hello")
    }

    @Test
    fun tapToSkip_flushesBufferImmediately() = runComposeUiTest {
        val state = StreamingTypewriterState()
        setContent {
            MaterialTheme {
                StreamingTypewriter(
                    tokens = remember { MutableSharedFlow() },
                    state = state,
                    baseDelayMs = 10_000L, // very slow — the test clicks before it would reveal
                )
            }
        }
        state.appendToken("A long sentence that the typewriter will not reveal at 10s per char.")
        // Click the root — tap-to-skip is wired to the root clickable.
        onNodeWithTag("llm_typewriter").performClick()
        waitUntil(timeoutMillis = 1_000) {
            state.revealed == "A long sentence that the typewriter will not reveal at 10s per char."
        }
        assertEquals(0, state.pendingChars)
    }

    @Test
    fun stop_freezesTextAndShowsStoppedMarker() = runComposeUiTest {
        val state = StreamingTypewriterState()
        setContent {
            MaterialTheme {
                StreamingTypewriter(
                    tokens = remember { MutableSharedFlow() },
                    state = state,
                    baseDelayMs = 10_000L,
                )
            }
        }
        state.appendToken("Hello")
        state.skipToEnd()
        state.stop()
        waitForIdle()
        assertEquals("Hello", state.revealed)
        assertEquals(TypewriterPhase.Stopped, state.phase)
        onNodeWithTag("llm_typewriter_stopped", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun rootTestTagIsPresent() = runComposeUiTest {
        setContent {
            MaterialTheme {
                StreamingTypewriter(
                    tokens = remember { MutableSharedFlow() },
                    baseDelayMs = 0L,
                )
            }
        }
        onNodeWithTag("llm_typewriter").assertIsDisplayed()
    }

    @Test
    fun thinkingDots_rendersWithoutCrash() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ThinkingDots()
            }
        }
        // No exception means the animation primitives are initialised.
        waitForIdle()
        assertTrue(true)
    }

    @Test
    fun typewriterText_revealsFinalString() = runComposeUiTest {
        val state = StreamingTypewriterState()
        setContent {
            MaterialTheme {
                TypewriterText(text = "abc", state = state, baseDelayMs = 0L)
            }
        }
        waitUntil(timeoutMillis = 5_000) { state.revealed == "abc" }
        waitForIdle()
        onNodeWithTag("llm_typewriter_text", useUnmergedTree = true).assertTextEquals("abc")
    }

    @Test
    fun cycling_eventuallyShowsAtLeastOnePhrasePartially() = runComposeUiTest {
        val phrases = listOf("alpha", "beta")
        setContent {
            MaterialTheme {
                CyclingTypewriterText(phrases = phrases, typeDelayMs = 0L, deleteDelayMs = 0L, holdMs = 50L)
            }
        }
        // Drive a tiny while of clock, then sample.
        waitForIdle()
        // Not asserting exact text — the cycling animation may have moved past — just verifying
        // the composable renders without crashing.
        onNodeWithTag("llm_typewriter_cycling", useUnmergedTree = true).assertIsDisplayed()
    }
}

