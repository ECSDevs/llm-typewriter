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
