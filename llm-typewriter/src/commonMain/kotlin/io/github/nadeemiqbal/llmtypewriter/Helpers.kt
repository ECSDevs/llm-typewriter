package io.github.nadeemiqbal.llmtypewriter

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Build a flow that emits each character of [text] as a one-character token at fixed cadence
 * [perCharDelayMs]. Useful for testing the typewriter against a known input, and for the
 * "synthetic LLM" demo in the sample app — no actual model required.
 */
fun staticFlowOf(text: String, perCharDelayMs: Long = 0L): Flow<String> = flow {
    for (ch in text) {
        if (perCharDelayMs > 0) delay(perCharDelayMs)
        emit(ch.toString())
    }
}

/**
 * A flow that emits each word of [text] as a token, separated by spaces (a closer approximation
 * of how real LLMs stream — one token ≈ one word). Each emission includes the trailing space.
 */
fun wordTokenFlowOf(text: String, perTokenDelayMs: Long = 80L): Flow<String> = flow {
    val parts = text.split(" ").filter { it.isNotEmpty() }
    for ((index, word) in parts.withIndex()) {
        if (perTokenDelayMs > 0) delay(perTokenDelayMs)
        emit(if (index == parts.lastIndex) word else "$word ")
    }
}

/**
 * Used by [CyclingTypewriterText] — drops the last revealed character without touching the buffer.
 * This is a hack: the state's revealed/buffer relationship is normally append-only, so to support
 * the cycling delete animation we shrink both at once.
 */
internal fun StreamingTypewriterState.deleteLastRevealed() {
    // The simplest way to delete-and-resync is to reset and re-append the prefix. This is fine
    // for the small phrase strings used in CyclingTypewriterText (no GC pressure).
    val cur = revealed
    if (cur.isEmpty()) return
    val truncated = cur.substring(0, cur.length - 1)
    reset()
    appendToken(truncated)
    // We don't want a streaming reveal of the truncated prefix — flush immediately.
    skipToEnd()
}
