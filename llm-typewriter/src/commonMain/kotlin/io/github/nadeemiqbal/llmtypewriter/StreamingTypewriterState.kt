package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Lifecycle phases of a [StreamingTypewriterState] from the reveal loop's perspective. */
enum class TypewriterPhase {
    /** Nothing scheduled, nothing revealing. Initial state and the state after [StreamingTypewriterState.reset]. */
    Idle,

    /** The reveal loop is actively pulling characters off the buffer and adding them to [StreamingTypewriterState.revealed]. */
    Revealing,

    /** Source flow has not completed, but the reveal loop has caught up to the buffer's tail. */
    Waiting,

    /** Source flow completed and every buffered character has been revealed. */
    Done,

    /** The user (or a stop control) interrupted the reveal — buffered characters stay buffered and stay unrevealed. */
    Stopped,
}

/**
 * Owns the reveal buffer + revealed text + lifecycle phase for [StreamingTypewriter]. Constructed
 * via [rememberStreamingTypewriterState] (the standard path) or directly for tests.
 *
 * The state is the single source of truth — the composable doesn't keep a parallel cache. The
 * `tokens` flow only pushes characters into [appendToken]; the reveal loop pulls them out at the
 * pace dictated by the active [SpeedCurve]. This separation is what lets the typewriter survive
 * recompositions and supports the headless API (no composition required for tests).
 */
class StreamingTypewriterState(
    initialBufferSoftCap: Int = LlmTypewriterDefaults.DefaultBufferSoftCap,
) {

    private val buffer = StringBuilder()

    private var revealedState by mutableStateOf("")

    /** Text that has been revealed so far. Renderers consume this. */
    val revealed: String get() = revealedState

    private var phaseState by mutableStateOf(TypewriterPhase.Idle)

    /** Lifecycle phase of the reveal loop. */
    val phase: TypewriterPhase get() = phaseState

    private var sourceCompleteState by mutableStateOf(false)

    /** `true` once the source token flow has signalled completion. */
    val sourceComplete: Boolean get() = sourceCompleteState

    private var bufferSizeState by mutableStateOf(0)
    private var pendingCharsState by mutableStateOf(0)

    /** Number of characters that have arrived but have not yet been revealed. */
    val pendingChars: Int get() = pendingCharsState

    /**
     * `true` when the source has completed and the reveal loop has caught up — i.e. the user is
     * looking at the final text and the typewriter is finished.
     */
    @Suppress("unused")
    val isFinished: Boolean get() = phaseState == TypewriterPhase.Done

    /** Soft cap on the buffer — purely a memory-pressure guard. */
    var bufferSoftCap: Int = initialBufferSoftCap

    // ---- caller-driven inputs ----------------------------------------------------------------

    /**
     * Append a token (a string of zero or more characters) to the reveal buffer. Empty strings
     * are no-ops. Called by the source-collecting coroutine each time the upstream `Flow<String>`
     * emits.
     */
    fun appendToken(token: String) {
        if (token.isEmpty()) return
        buffer.append(token)
        bufferSizeState = buffer.length
        pendingCharsState = bufferSizeState - revealedState.length
        if (phaseState == TypewriterPhase.Idle || phaseState == TypewriterPhase.Waiting) {
            phaseState = TypewriterPhase.Revealing
        }
    }

    /**
     * Signal that the source flow has completed — any remaining buffered text will still be
     * revealed at the configured cadence, then the phase moves to [TypewriterPhase.Done].
     */
    fun completeSource() {
        sourceCompleteState = true
        // If the reveal loop already caught up, flip immediately to Done.
        if (revealedState.length >= buffer.length) phaseState = TypewriterPhase.Done
    }

    // ---- reveal-loop inputs ------------------------------------------------------------------

    /**
     * Reveal one more character from the buffer. Returns the character revealed, or `null` if the
     * buffer is empty (in which case the phase becomes [TypewriterPhase.Waiting] or
     * [TypewriterPhase.Done] depending on whether the source has completed).
     */
    internal fun revealNext(): Char? {
        val len = revealedState.length
        if (len >= buffer.length) {
            phaseState = if (sourceCompleteState) TypewriterPhase.Done else TypewriterPhase.Waiting
            pendingCharsState = 0
            return null
        }
        val ch = buffer[len]
        revealedState = buffer.substring(0, len + 1)
        pendingCharsState = buffer.length - revealedState.length
        if (revealedState.length >= buffer.length) {
            phaseState = if (sourceCompleteState) TypewriterPhase.Done else TypewriterPhase.Waiting
        } else {
            phaseState = TypewriterPhase.Revealing
        }
        return ch
    }

    /** The character that was most recently revealed (used by [SpeedCurve]). */
    internal val lastRevealed: Char? get() = revealedState.lastOrNull()

    // ---- control surface ---------------------------------------------------------------------

    /**
     * Reveal everything currently in the buffer without animation. Used by the tap-to-skip
     * gesture and by `controller.skipToEnd()`. If the source has completed, the phase becomes
     * [TypewriterPhase.Done]; otherwise [TypewriterPhase.Waiting].
     */
    fun skipToEnd() {
        if (revealedState.length < buffer.length) {
            revealedState = buffer.toString()
            pendingCharsState = 0
        }
        phaseState = if (sourceCompleteState) TypewriterPhase.Done else TypewriterPhase.Waiting
    }

    /**
     * Interrupt the reveal loop. Already-revealed text stays visible; pending buffered chars stay
     * pending. The reveal loop honors [TypewriterPhase.Stopped] by suspending, and consumers can
     * observe the "stopped" state to render a ghost indicator.
     */
    fun stop() {
        phaseState = TypewriterPhase.Stopped
    }

    /** Resume after a [stop] — picks up exactly where we left off. */
    fun resume() {
        if (phaseState != TypewriterPhase.Stopped) return
        phaseState = when {
            revealedState.length < buffer.length -> TypewriterPhase.Revealing
            sourceCompleteState -> TypewriterPhase.Done
            else -> TypewriterPhase.Waiting
        }
    }

    /**
     * Erase everything — revealed text, buffer, and completion state. Useful when the host wants
     * to start a fresh streaming response on the same state object (e.g. after the user hits
     * "Regenerate").
     */
    fun reset() {
        buffer.clear()
        revealedState = ""
        sourceCompleteState = false
        bufferSizeState = 0
        pendingCharsState = 0
        phaseState = TypewriterPhase.Idle
    }
}

/**
 * Creates and remembers a [StreamingTypewriterState].
 *
 * @param bufferSoftCap soft cap on the unrevealed buffer length — purely advisory; nothing is
 *   dropped, this is just a hint exposed via [StreamingTypewriterState.bufferSoftCap] that you
 *   can use to render a "model getting ahead of typewriter" indicator.
 */
@Composable
fun rememberStreamingTypewriterState(
    bufferSoftCap: Int = LlmTypewriterDefaults.DefaultBufferSoftCap,
): StreamingTypewriterState = remember { StreamingTypewriterState(bufferSoftCap) }

/** True if [phase] is one of the active reveal phases ([TypewriterPhase.Revealing] or [TypewriterPhase.Waiting]). */
val StreamingTypewriterState.isStreaming: Boolean
    get() = phase == TypewriterPhase.Revealing || phase == TypewriterPhase.Waiting

/**
 * Convenience: a read-only state derivation that flips `true` once at least one char is revealed
 * — useful for hiding a "thinking…" indicator the moment real text arrives.
 */
@Composable
fun StreamingTypewriterState.rememberHasRevealedText(): androidx.compose.runtime.State<Boolean> {
    return remember(this) { derivedStateOf { revealed.isNotEmpty() } }
}
