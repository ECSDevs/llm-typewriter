package cc.ptoe.llmtypewriter

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-logic tests for [groupIntoParagraphs] — the CommonMark soft-break / paragraph-break
 * handling that the renderer's [planBlocks] relies on.
 */
class ParagraphGroupingTest {

    @Test
    fun emptyInput_emitsNoGroups() {
        assertEquals(emptyList(), groupIntoParagraphs(emptyList()))
    }

    @Test
    fun singleNewline_isSoftBreak_becomesSpace() {
        // "a\nb" → one paragraph with a space joining the halves (CommonMark soft break).
        val tokens = listOf(MdToken.Plain("a"), MdToken.Newline, MdToken.Plain("b"))
        assertEquals(
            listOf(listOf(MdToken.Plain("a"), MdToken.Plain(" "), MdToken.Plain("b"))),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun twoNewlines_areParagraphBreak() {
        val tokens = listOf(
            MdToken.Plain("a"), MdToken.Newline, MdToken.Newline, MdToken.Plain("b"),
        )
        assertEquals(
            listOf(listOf(MdToken.Plain("a")), listOf(MdToken.Plain("b"))),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun manyNewlines_collapseToOneParagraphBreak() {
        // Five newlines should behave the same as two — one paragraph break, no empty groups.
        val tokens = listOf(
            MdToken.Plain("a"),
            MdToken.Newline, MdToken.Newline, MdToken.Newline, MdToken.Newline, MdToken.Newline,
            MdToken.Plain("b"),
        )
        assertEquals(
            listOf(listOf(MdToken.Plain("a")), listOf(MdToken.Plain("b"))),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun singleNewlineAtBlockBoundary_isDropped() {
        // A single newline right after a block-level token (heading) shouldn't seed the next
        // paragraph with a leading space.
        val tokens = listOf(
            MdToken.Heading(1, "Title"),
            MdToken.Newline,
            MdToken.Plain("body"),
        )
        assertEquals(
            listOf(listOf(MdToken.Heading(1, "Title")), listOf(MdToken.Plain("body"))),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun heading_alwaysOwnGroup_andBreaksParagraph() {
        val tokens = listOf(
            MdToken.Plain("intro"),
            MdToken.Newline,
            MdToken.Heading(2, "Section"),
            MdToken.Plain("after"),
        )
        // The single newline before the heading is a block-boundary newline → dropped (no space
        // seeds the heading group). The heading is its own group, then "after" starts a new group.
        assertEquals(
            listOf(
                listOf(MdToken.Plain("intro")),
                listOf(MdToken.Heading(2, "Section")),
                listOf(MdToken.Plain("after")),
            ),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun codeBlock_isOwnGroup() {
        val cb = MdToken.CodeBlock("kotlin", "val x = 1\n", closed = true)
        val tokens = listOf(MdToken.Plain("before"), MdToken.Newline, MdToken.Newline, cb)
        assertEquals(
            listOf(listOf(MdToken.Plain("before")), listOf(cb)),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun displayMath_isOwnGroup() {
        val dm = MdToken.DisplayMath("x^2", closed = true)
        val tokens = listOf(MdToken.Plain("p"), MdToken.Newline, dm, MdToken.Newline, MdToken.Plain("q"))
        assertEquals(
            listOf(
                listOf(MdToken.Plain("p")),
                listOf(dm),
                listOf(MdToken.Plain("q")),
            ),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun inlineMath_staysInParagraphAcrossSoftBreak() {
        // `$a$\n$b$` — single newline is a soft break; both inline math fragments stay in one
        // paragraph joined by a space.
        val tokens = listOf(
            MdToken.InlineMath("a", closed = true),
            MdToken.Newline,
            MdToken.InlineMath("b", closed = true),
        )
        assertEquals(
            listOf(
                listOf(
                    MdToken.InlineMath("a", closed = true),
                    MdToken.Plain(" "),
                    MdToken.InlineMath("b", closed = true),
                ),
            ),
            groupIntoParagraphs(tokens),
        )
    }

    @Test
    fun leadingNewlines_produceNoEmptyGroups() {
        val tokens = listOf(MdToken.Newline, MdToken.Newline, MdToken.Plain("x"))
        assertEquals(listOf(listOf(MdToken.Plain("x"))), groupIntoParagraphs(tokens))
    }

    @Test
    fun trailingNewlines_produceNoEmptyGroups() {
        val tokens = listOf(MdToken.Plain("x"), MdToken.Newline, MdToken.Newline)
        assertEquals(listOf(listOf(MdToken.Plain("x"))), groupIntoParagraphs(tokens))
    }

    @Test
    fun multipleSoftBreaks_eachBecomeSpace() {
        // "a\nb\nc" — two single newlines, both soft breaks → one paragraph "a b c".
        val tokens = listOf(
            MdToken.Plain("a"), MdToken.Newline,
            MdToken.Plain("b"), MdToken.Newline,
            MdToken.Plain("c"),
        )
        assertEquals(
            listOf(listOf(
                MdToken.Plain("a"), MdToken.Plain(" "),
                MdToken.Plain("b"), MdToken.Plain(" "),
                MdToken.Plain("c"),
            )),
            groupIntoParagraphs(tokens),
        )
    }
}
