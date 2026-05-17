package io.github.nadeemiqbal.llmtypewriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-logic tests for [parseStreamingMarkdown] — the prefix-stability property is the killer
 * feature, so we test it explicitly with a string that grows character-by-character.
 */
class MarkdownStreamParserTest {

    @Test
    fun emptyInput_emitsNothing() {
        assertEquals(emptyList(), parseStreamingMarkdown(""))
    }

    @Test
    fun plainText_emitsSinglePlain() {
        assertEquals(listOf(MdToken.Plain("hello world")), parseStreamingMarkdown("hello world"))
    }

    @Test
    fun bold_doubleStar() {
        val tokens = parseStreamingMarkdown("a **b** c")
        assertEquals(
            listOf(MdToken.Plain("a "), MdToken.Bold("b"), MdToken.Plain(" c")),
            tokens,
        )
    }

    @Test
    fun bold_doubleUnderscore() {
        val tokens = parseStreamingMarkdown("__yes__")
        assertEquals(listOf(MdToken.Bold("yes")), tokens)
    }

    @Test
    fun italic_singleStar() {
        val tokens = parseStreamingMarkdown("hello *world*")
        assertEquals(
            listOf(MdToken.Plain("hello "), MdToken.Italic("world")),
            tokens,
        )
    }

    @Test
    fun italic_singleUnderscore() {
        val tokens = parseStreamingMarkdown("_yes_")
        assertEquals(listOf(MdToken.Italic("yes")), tokens)
    }

    @Test
    fun boldItalic_tripleStar() {
        val tokens = parseStreamingMarkdown("***both***")
        assertEquals(listOf(MdToken.BoldItalic("both")), tokens)
    }

    @Test
    fun inlineCode_backticks() {
        val tokens = parseStreamingMarkdown("call `foo()` here")
        assertEquals(
            listOf(MdToken.Plain("call "), MdToken.InlineCode("foo()"), MdToken.Plain(" here")),
            tokens,
        )
    }

    @Test
    fun strikethrough_doubleTilde() {
        val tokens = parseStreamingMarkdown("~~gone~~")
        assertEquals(listOf(MdToken.Strikethrough("gone")), tokens)
    }

    @Test
    fun link_simple() {
        val tokens = parseStreamingMarkdown("see [docs](https://example.com)")
        assertEquals(
            listOf(
                MdToken.Plain("see "),
                MdToken.Link("docs", "https://example.com"),
            ),
            tokens,
        )
    }

    @Test
    fun heading_levelOneTwoSix() {
        assertEquals(
            listOf(MdToken.Heading(1, "Title")),
            parseStreamingMarkdown("# Title"),
        )
        assertEquals(
            listOf(MdToken.Heading(2, "Sub")),
            parseStreamingMarkdown("## Sub"),
        )
        assertEquals(
            listOf(MdToken.Heading(6, "Tiny")),
            parseStreamingMarkdown("###### Tiny"),
        )
    }

    @Test
    fun heading_requiresSpaceAfterHashes() {
        // `#NoSpace` should NOT be parsed as a heading — emit as plain.
        val tokens = parseStreamingMarkdown("#nope")
        assertTrue(tokens.first() is MdToken.Plain)
    }

    @Test
    fun fencedCodeBlock_closed() {
        val tokens = parseStreamingMarkdown("```kotlin\nval x = 1\n```")
        val cb = tokens.first() as MdToken.CodeBlock
        assertEquals("kotlin", cb.language)
        assertEquals("val x = 1\n", cb.content)
        assertEquals(true, cb.closed)
    }

    @Test
    fun fencedCodeBlock_open() {
        val tokens = parseStreamingMarkdown("```kotlin\nval x = 1")
        val cb = tokens.first() as MdToken.CodeBlock
        assertEquals("kotlin", cb.language)
        assertEquals("val x = 1", cb.content)
        assertEquals(false, cb.closed)
    }

    @Test
    fun newline_emitsNewlineToken() {
        val tokens = parseStreamingMarkdown("a\nb")
        assertEquals(
            listOf(MdToken.Plain("a"), MdToken.Newline, MdToken.Plain("b")),
            tokens,
        )
    }

    @Test
    fun prefixStability_growingBoldDoesntScrambleEarlierTokens() {
        // Prefix stability: while the `**` is unclosed the parser emits the trailing delimiters as
        // part of a single Plain token, but the literal "Hello, " is always the very start of that
        // token. Once the closing `**` arrives, the parser breaks the bold out cleanly and the
        // first token becomes `Plain("Hello, ")` again.
        val full = "Hello, **world**!"
        listOf(8, 9, 10, 12, 15).forEach { len ->
            val toks = parseStreamingMarkdown(full.substring(0, len))
            val first = toks.first()
            assertTrue(first is MdToken.Plain, "len=$len: expected leading Plain, got $first")
            assertTrue(
                first.text.startsWith("Hello, "),
                "len=$len: expected leading 'Hello, ', got '${first.text}'",
            )
        }
        // Final closed state has the clean token sequence.
        assertEquals(
            listOf(MdToken.Plain("Hello, "), MdToken.Bold("world"), MdToken.Plain("!")),
            parseStreamingMarkdown(full),
        )
    }

    @Test
    fun prefixStability_growingCodeBlock_keepsFenceTokenStable() {
        val full = "```kt\nval x = 1\n```\nafter"
        // After the opening fence + newline, the parser emits an unclosed code block. Every
        // subsequent prefix should also have a single CodeBlock token at index 0.
        for (len in 8..full.length) {
            val toks = parseStreamingMarkdown(full.substring(0, len))
            val first = toks.first()
            assertTrue(first is MdToken.CodeBlock, "len=$len, first=$first")
        }
    }

    @Test
    fun unclosedItalic_emitsPlain() {
        val tokens = parseStreamingMarkdown("hello *world")
        // The unclosed single-star should render as plain so users don't see weird whitespace.
        assertTrue(tokens.any { it is MdToken.Plain && (it.text.contains("*world")) })
    }

    @Test
    fun adjacentPlainRunsAreMerged() {
        // Multiple plain emits should collapse into a single Plain token.
        val tokens = parseStreamingMarkdown("abc")
        assertEquals(1, tokens.size)
        assertEquals(MdToken.Plain("abc"), tokens.first())
    }

    @Test
    fun codeBlockWithoutLanguage() {
        val tokens = parseStreamingMarkdown("```\nplain\n```")
        val cb = tokens.first() as MdToken.CodeBlock
        assertEquals(null, cb.language)
        assertEquals("plain\n", cb.content)
    }
}
