package cc.ptoe.llmtypewriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodeHighlighterTest {

    @Test
    fun languageParse_aliases() {
        assertEquals(CodeLanguage.Kotlin, CodeLanguage.parse("kt"))
        assertEquals(CodeLanguage.Kotlin, CodeLanguage.parse("Kotlin"))
        assertEquals(CodeLanguage.JavaScript, CodeLanguage.parse("javascript"))
        assertEquals(CodeLanguage.JavaScript, CodeLanguage.parse("ts"))
        assertEquals(CodeLanguage.Python, CodeLanguage.parse("py"))
        assertEquals(CodeLanguage.Plain, CodeLanguage.parse("unknown"))
        assertEquals(CodeLanguage.Plain, CodeLanguage.parse(null))
    }

    @Test
    fun kotlin_keywordsAreHighlighted() {
        val src = "fun greet(name: String): String = \"hi, \" + name"
        val spans = highlightCode(src, CodeLanguage.Kotlin)
        val funSpan = spans.firstOrNull { it.kind == CodeSpanKind.Keyword && src.substring(it.start, it.end) == "fun" }
        assertNotNull(funSpan, "expected `fun` to be a keyword span")
    }

    @Test
    fun kotlin_stringLiteralHighlighted() {
        val src = "val msg = \"hello\""
        val spans = highlightCode(src, CodeLanguage.Kotlin)
        val stringSpan = spans.firstOrNull {
            it.kind == CodeSpanKind.StringLit && src.substring(it.start, it.end) == "\"hello\""
        }
        assertNotNull(stringSpan, "expected double-quoted string to be a StringLit span")
    }

    @Test
    fun kotlin_numberLiteralHighlighted() {
        val src = "val x = 42"
        val spans = highlightCode(src, CodeLanguage.Kotlin)
        val numberSpan = spans.firstOrNull {
            it.kind == CodeSpanKind.Number && src.substring(it.start, it.end) == "42"
        }
        assertNotNull(numberSpan)
    }

    @Test
    fun kotlin_lineCommentHighlighted() {
        val src = "val x = 1 // explanation"
        val spans = highlightCode(src, CodeLanguage.Kotlin)
        val commentSpan = spans.firstOrNull { it.kind == CodeSpanKind.Comment }
        assertNotNull(commentSpan)
        assertTrue(src.substring(commentSpan.start, commentSpan.end).startsWith("//"))
    }

    @Test
    fun python_hashCommentIsHighlighted_kotlinIsNot() {
        val pyComment = highlightCode("x = 1 # comment", CodeLanguage.Python).any { it.kind == CodeSpanKind.Comment }
        val ktNoComment = highlightCode("x = 1 # not a comment", CodeLanguage.Kotlin).none { it.kind == CodeSpanKind.Comment }
        assertTrue(pyComment, "python's # should be a comment")
        assertTrue(ktNoComment, "kotlin's # should not be a comment")
    }

    @Test
    fun blockComment_spansMultipleLines() {
        val src = "/* hello\nworld */ next"
        val spans = highlightCode(src, CodeLanguage.JavaScript)
        val comment = spans.firstOrNull { it.kind == CodeSpanKind.Comment }
        assertNotNull(comment)
        assertEquals(0, comment.start)
        assertTrue(comment.end >= "/* hello\nworld */".length)
    }

    @Test
    fun stringWithEscapedQuote_isFullyConsumed() {
        val src = "\"a\\\"b\""
        val spans = highlightCode(src, CodeLanguage.JavaScript)
        val s = spans.firstOrNull { it.kind == CodeSpanKind.StringLit }
        assertNotNull(s)
        assertEquals(0, s.start)
        assertEquals(src.length, s.end)
    }

    @Test
    fun plainLanguage_noKeywordsRecognized() {
        val src = "fun greet"
        val spans = highlightCode(src, CodeLanguage.Plain)
        val keyword = spans.firstOrNull { it.kind == CodeSpanKind.Keyword }
        assertEquals(null, keyword, "Plain language has no keywords")
    }

    @Test
    fun emptyInput_isEmpty() {
        assertEquals(emptyList(), highlightCode("", CodeLanguage.Kotlin))
    }
}
