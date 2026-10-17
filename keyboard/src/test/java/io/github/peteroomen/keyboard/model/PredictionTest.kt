package io.github.peteroomen.keyboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionTest {

    @Test
    fun previousWordsMostRecentFirst() {
        assertEquals(Context(listOf("at", "meet", "we"), sentenceStart = false), Prediction.context("Shall we meet at "))
        assertEquals(Context(listOf("are", "how"), sentenceStart = true), Prediction.context("Hi. how are "))
        assertEquals(Context(emptyList(), sentenceStart = true), Prediction.context(""))
        assertEquals(Context(emptyList(), sentenceStart = true), Prediction.context("Done! "))
        assertEquals(Context(listOf("don't"), sentenceStart = true), Prediction.context("don't "))
        assertEquals(Context(listOf("kia"), sentenceStart = true), Prediction.context("line\nkia "))
    }

    @Test
    fun aWordCutOffByTheReadLimitIsNotUsed() {
        val before = "x".repeat(46) + " to "
        assertEquals(Context(listOf("to"), sentenceStart = false), Prediction.context(before))
    }

    @Test
    fun theGridListsKeysNearEachCell() {
        // Three keys, 100px wide, in one 100px row across 300px
        val keys = listOf(
            EngineKey('a'.code, Box(0f, 0f, 100f, 100f)),
            EngineKey('b'.code, Box(100f, 0f, 200f, 100f)),
            EngineKey('c'.code, Box(200f, 0f, 300f, 100f)),
        )
        val grid = Prediction.proximityGrid(keys, width = 300, height = 100, keyWidth = 100)
        fun cell(gx: Int, gy: Int) = grid.copyOfRange((gy * Prediction.GRID_WIDTH + gx) * 16, (gy * Prediction.GRID_WIDTH + gx) * 16 + 16)
            .filter { it != Prediction.NOT_A_CODE }.map { it.toChar() }
        // The left edge is within 1.2 key widths of a and b, not c
        assertEquals(listOf('a', 'b'), cell(0, 0))
        // The middle is near all three
        assertEquals(listOf('a', 'b', 'c'), cell(16, 8))
    }

    @Test
    fun autocorrectRules() {
        assertTrue(Prediction.mayAutocorrect("teh", typedIsKnown = false))
        assertFalse(Prediction.mayAutocorrect("the", typedIsKnown = true))
        assertFalse(Prediction.mayAutocorrect("a", typedIsKnown = false))
        assertFalse(Prediction.mayAutocorrect("b4", typedIsKnown = false))
        assertFalse(Prediction.mayAutocorrect("NZQA", typedIsKnown = false))
        assertTrue(Prediction.mayAutocorrect("Teh", typedIsKnown = false))
    }
}
