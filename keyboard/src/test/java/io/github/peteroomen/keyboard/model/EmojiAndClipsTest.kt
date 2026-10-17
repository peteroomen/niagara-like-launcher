package io.github.peteroomen.keyboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiAndClipsTest {

    @Test
    fun bundledListParsesIntoGboardsCategories() {
        val categories = Emoji.parse(File("src/main/assets/emoji.txt").readText())
        assertEquals(
            listOf("Smileys and people", "Animals and nature", "Food and drink", "Travel and places", "Activities", "Objects", "Symbols", "Flags"),
            categories.map { it.name },
        )
        assertEquals("😀", categories.first().emoji.first())
        assertTrue(categories.all { it.emoji.size > 50 })
        // No skin-tone variants
        assertTrue(categories.flatMap { it.emoji }.none { e -> e.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF } })
    }

    @Test
    fun emojiThePhoneCantDrawAreLeftOut() {
        val categories = Emoji.parse("A\t😀 🫨 😃\nB\t🫨", drawable = { it != "🫨" })
        assertEquals(listOf(EmojiCategory("A", listOf("😀", "😃"))), categories)
    }

    @Test
    fun recentsPutTheLastUsedFirst() {
        assertEquals(listOf("😃", "😀"), Emoji.used(listOf("😀"), "😃"))
        assertEquals(listOf("😀", "😃"), Emoji.used(listOf("😃", "😀"), "😀"))
        assertEquals(Emoji.RECENTS, Emoji.used((1..40).map { "$it" }, "x").size)
    }

    @Test
    fun clipsLastAnHourNewestFirst() {
        val hour = ClipHistory.LASTS_MS
        var history = ClipHistory.add(emptyList(), Clip("one", false, 0), now = 0)
        history = ClipHistory.add(history, Clip("two", false, 10), now = 10)
        assertEquals(listOf("two", "one"), history.map { it.text })
        // Copying something again moves it to the front
        history = ClipHistory.add(history, Clip("one", false, 20), now = 20)
        assertEquals(listOf("one", "two"), history.map { it.text })
        // Blank copies are ignored
        assertEquals(2, ClipHistory.add(history, Clip("  ", false, 30), now = 30).size)
        // An hour on, "two" (copied at 10) has gone
        assertEquals(listOf("one"), ClipHistory.current(history, now = hour + 15).map { it.text })
    }

    @Test
    fun chipLabels() {
        assertEquals("a b c", ClipHistory.label(Clip("  a\n b\tc ", false, 0)))
        assertEquals("••••••", ClipHistory.label(Clip("hunter2", true, 0)))
    }
}
