package cc.ptoe.llmtypewriter

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

    // --- Math delimiters ---

    @Test
    fun inlineMath_closed() {
        val tokens = parseStreamingMarkdown("a \$x^2\$ b")
        assertEquals(
            listOf(
                MdToken.Plain("a "),
                MdToken.InlineMath("x^2", closed = true),
                MdToken.Plain(" b"),
            ),
            tokens,
        )
    }

    @Test
    fun displayMath_closed() {
        val tokens = parseStreamingMarkdown("\$\$\\sum_{i=1}^n i^2\$\$")
        assertEquals(
            listOf(MdToken.DisplayMath("\\sum_{i=1}^n i^2", closed = true)),
            tokens,
        )
    }

    @Test
    fun displayMath_priorityOverInline() {
        // `$$x$$` should be display math, not inline math `$` + `$x$` + `$`.
        val tokens = parseStreamingMarkdown("\$\$x\$\$")
        assertEquals(listOf(MdToken.DisplayMath("x", closed = true)), tokens)
    }

    @Test
    fun inlineMath_rejectsLeadingSpace() {
        // `$ word $` — the opening `$` followed by whitespace shouldn't start a math span.
        val tokens = parseStreamingMarkdown("\$ word \$")
        // The `$` should be plain text. The closing `$` would be rejected too because of the
        // preceding whitespace.
        assertTrue(tokens.all { it is MdToken.Plain })
    }

    @Test
    fun inlineMath_rejectsTrailingSpaceBeforeClose() {
        // `$word $` — closing `$` preceded by whitespace is rejected, so no math span.
        val tokens = parseStreamingMarkdown("\$word \$")
        assertTrue(tokens.none { it is MdToken.InlineMath })
    }

    @Test
    fun currency_notTreatedAsMath() {
        // `$5` is currency, not a math opener — but our rule is "no space after $", and `5` is
        // not whitespace, so this DOES open a math span. The close is the issue: there's no second
        // `$`. Without a close, the parser emits the `$` as plain text.
        val tokens = parseStreamingMarkdown("The price is \$5.")
        assertTrue(tokens.none { it is MdToken.InlineMath })
    }

    @Test
    fun unclosedInlineMath_emitsPlain() {
        val tokens = parseStreamingMarkdown("a \$x")
        // The unclosed `$` should degrade to plain text.
        assertTrue(tokens.none { it is MdToken.InlineMath })
        assertTrue(tokens.any { it is MdToken.Plain && it.text.contains("\$") })
    }

    @Test
    fun unclosedDisplayMath_emitsPlain() {
        val tokens = parseStreamingMarkdown("\$\$\\frac{a}{b}")
        // The unclosed `$$` should degrade to plain text (mergeAdjacentPlain may fold it into a
        // following plain run, so we check containment rather than equality).
        assertTrue(tokens.none { it is MdToken.DisplayMath })
        assertTrue(tokens.any { it is MdToken.Plain && it.text.contains("\$\$") })
    }

    @Test
    fun mathSurroundedByText_parsesCleanly() {
        val tokens = parseStreamingMarkdown("The formula \$a^2 + b^2 = c^2\$ is Pythagoras.")
        assertEquals(
            listOf(
                MdToken.Plain("The formula "),
                MdToken.InlineMath("a^2 + b^2 = c^2", closed = true),
                MdToken.Plain(" is Pythagoras."),
            ),
            tokens,
        )
    }

    @Test
    fun displayMath_followedByText() {
        val tokens = parseStreamingMarkdown("\$\$x = 1\$\$\nThen x is one.")
        assertEquals(
            listOf(
                MdToken.DisplayMath("x = 1", closed = true),
                MdToken.Newline,
                MdToken.Plain("Then x is one."),
            ),
            tokens,
        )
    }

    @Test
    fun multipleInlineMath_inOneLine() {
        val tokens = parseStreamingMarkdown("\$a\$ and \$b\$")
        assertEquals(
            listOf(
                MdToken.InlineMath("a", closed = true),
                MdToken.Plain(" and "),
                MdToken.InlineMath("b", closed = true),
            ),
            tokens,
        )
    }

    @Test
    fun prefixStability_growingInlineMath() {
        val full = "\$x^2\$"
        // While the close hasn't arrived, the `$` is plain text. Once the close lands, the whole
        // thing becomes an InlineMath token. The tokens before the `$` (none in this case) stay
        // stable.
        listOf(1, 2, 3, 4).forEach { len ->
            val toks = parseStreamingMarkdown(full.substring(0, len))
            // No exception, no math token until the close.
            if (len < full.length) {
                assertTrue(toks.none { it is MdToken.InlineMath }, "len=\$len")
            }
        }
        // Closed form.
        assertEquals(listOf(MdToken.InlineMath("x^2", closed = true)), parseStreamingMarkdown(full))
    }
}
