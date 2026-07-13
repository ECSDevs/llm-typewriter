package cc.ptoe.llmtypewriter

import kotlin.test.Test
import kotlin.test.assertEquals

class CodeBlockRenderingTest {

    @Test
    fun codeBlockLanguageLabel_usesTrimmedLanguageOnlyWhenPresent() {
        assertEquals("kotlin", codeBlockLanguageLabel(" kotlin "))
        assertEquals(null, codeBlockLanguageLabel("   "))
        assertEquals(null, codeBlockLanguageLabel(null))
    }

    @Test
    fun closedCodeBlock_trimsFenceClosingNewlineForDisplay() {
        assertEquals("val answer = 42", codeBlockContentForDisplay("val answer = 42\n", closed = true))
    }

    @Test
    fun closedCodeBlock_preservesIntentionalBlankLineBeforeFence() {
        assertEquals("val answer = 42\n", codeBlockContentForDisplay("val answer = 42\n\n", closed = true))
    }

    @Test
    fun openCodeBlock_keepsTrailingNewlineWhileStreaming() {
        assertEquals("val answer = 42\n", codeBlockContentForDisplay("val answer = 42\n", closed = false))
    }
}
