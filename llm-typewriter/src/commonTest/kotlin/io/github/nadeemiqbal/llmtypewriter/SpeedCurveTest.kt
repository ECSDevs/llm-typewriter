package io.github.nadeemiqbal.llmtypewriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure-logic tests for the [SpeedCurve] presets. */
class SpeedCurveTest {

    @Test
    fun linear_alwaysBase() {
        assertEquals(20L, SpeedCurve.Linear.delayForNext(20L, previous = 'a', next = '.'))
        assertEquals(20L, SpeedCurve.Linear.delayForNext(20L, previous = '?', next = ' '))
        assertEquals(20L, SpeedCurve.Linear.delayForNext(20L, previous = null, next = 'x'))
    }

    @Test
    fun easeOut_stretchesWhitespaceAndNewlines() {
        assertEquals(20L, SpeedCurve.EaseOut.delayForNext(20L, previous = 'h', next = 'i'))
        assertEquals((20L * 1.4f).toLong(), SpeedCurve.EaseOut.delayForNext(20L, previous = 'i', next = ' '))
        assertEquals((20L * 1.4f).toLong(), SpeedCurve.EaseOut.delayForNext(20L, previous = '.', next = '\n'))
    }

    @Test
    fun natural_holdsOnSentenceEndPunctuation() {
        assertEquals(120L, SpeedCurve.Natural.delayForNext(20L, previous = 'd', next = '.'))
        assertEquals(120L, SpeedCurve.Natural.delayForNext(20L, previous = 's', next = '!'))
        assertEquals(120L, SpeedCurve.Natural.delayForNext(20L, previous = 'y', next = '?'))
    }

    @Test
    fun natural_holdsOnMidSentencePunctuation() {
        assertEquals(60L, SpeedCurve.Natural.delayForNext(20L, previous = 'a', next = ','))
        assertEquals(60L, SpeedCurve.Natural.delayForNext(20L, previous = 'b', next = ';'))
        assertEquals(60L, SpeedCurve.Natural.delayForNext(20L, previous = 'c', next = ':'))
    }

    @Test
    fun natural_holdsOnNewline() {
        assertEquals(100L, SpeedCurve.Natural.delayForNext(20L, previous = '.', next = '\n'))
    }

    @Test
    fun natural_pausesAtParagraphStart() {
        // Just after a newline, the very next non-newline char is the start of a new paragraph.
        assertEquals((20L * 1.8f).toLong(), SpeedCurve.Natural.delayForNext(20L, previous = '\n', next = 'A'))
    }

    @Test
    fun natural_normalCharacterIsBase() {
        assertEquals(20L, SpeedCurve.Natural.delayForNext(20L, previous = 'a', next = 'b'))
        assertEquals(20L, SpeedCurve.Natural.delayForNext(20L, previous = null, next = 'x'))
    }

    @Test
    fun natural_whitespaceBreatherBetweenWords() {
        assertTrue(SpeedCurve.Natural.delayForNext(20L, previous = 'd', next = ' ') > 20L)
    }
}
