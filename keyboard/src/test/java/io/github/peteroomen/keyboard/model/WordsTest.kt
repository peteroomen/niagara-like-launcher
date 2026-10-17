package io.github.peteroomen.keyboard.model

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsTest {

    @Test
    fun wordCharacters() {
        assertTrue(Words.isWordChar('a', inWord = false))
        assertTrue(Words.isWordChar('ā', inWord = false))
        assertTrue(Words.isWordChar('7', inWord = false))
        assertTrue(Words.isWordChar('\'', inWord = true))
        assertFalse(Words.isWordChar('\'', inWord = false))
        assertFalse(Words.isWordChar('-', inWord = true))
        assertFalse(Words.isWordChar(' ', inWord = true))
    }

    @Test
    fun whichFieldsCompose() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(Words.composes(text))
        assertTrue(Words.composes(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(Words.composes(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(Words.composes(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(Words.composes(InputType.TYPE_CLASS_NUMBER))
        assertFalse(Words.composes(InputType.TYPE_NULL))
        assertTrue(Words.suggests(text))
        assertFalse(Words.suggests(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
    }

    @Test
    fun suggestionsTakeTheTypedCase() {
        assertEquals("The", Words.matchCase("Teh", "the"))
        assertEquals("THE", Words.matchCase("TEH", "the"))
        assertEquals("the", Words.matchCase("teh", "the"))
        assertEquals("I", Words.matchCase("I", "i"))
    }

    @Test
    fun aSureTypoIsFixedInTheMiddle() {
        val strip = Words.strip("teh", listOf("the", "ten", "tea"), looksLikeTypo = true, recommended = true)
        assertEquals("the", strip.autocorrect)
        assertEquals(listOf("teh", "the", "ten"), strip.slots)
    }

    @Test
    fun anUnsureOneOnlySuggests() {
        val strip = Words.strip("teh", listOf("the", "ten"), looksLikeTypo = true, recommended = false)
        assertNull(strip.autocorrect)
        assertEquals(listOf("the", "teh", "ten"), strip.slots)
    }

    @Test
    fun aRealWordStaysInTheMiddle() {
        val strip = Words.strip("kia", emptyList(), looksLikeTypo = false, recommended = false)
        assertNull(strip.autocorrect)
        assertEquals(listOf(null, "kia", null), strip.slots)
    }

    @Test
    fun whileTypingTheStripKeepsItsSidesButDropsTheFix() {
        val last = Words.strip("teh", listOf("the", "ten"), looksLikeTypo = true, recommended = true)
        val now = Words.typing(last, "tehr")
        assertEquals(listOf(null, "tehr", "ten"), now.slots)
        assertNull(now.autocorrect)
        assertEquals(listOf(null, "a", null), Words.typing(null, "a").slots)
    }

    @Test
    fun predictionsPutTheBestInTheMiddle() {
        assertEquals(listOf("you", "the", "a"), Words.predictions(listOf("the", "you", "a")).slots)
        assertEquals(listOf(null, "the", null), Words.predictions(listOf("the")).slots)
        // Typing after a prediction starts the strip afresh
        assertEquals(listOf(null, "w", null), Words.typing(Words.predictions(listOf("the", "you", "a")), "w").slots)
    }
}
