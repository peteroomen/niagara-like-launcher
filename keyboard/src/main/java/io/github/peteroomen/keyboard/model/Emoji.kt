package io.github.peteroomen.keyboard.model

data class EmojiCategory(val name: String, val emoji: List<String>)

object Emoji {
    /** How many recently typed emoji are kept, for the first tab. */
    const val RECENTS = 32

    /** Reads the bundled list (see scripts/build_emoji.py), keeping only emoji [drawable] says the phone can draw. */
    fun parse(text: String, drawable: (String) -> Boolean = { true }): List<EmojiCategory> =
        text.lineSequence().filter { '\t' in it }.map { line ->
            val (name, list) = line.split('\t', limit = 2)
            EmojiCategory(name, list.split(' ').filter { it.isNotEmpty() && drawable(it) })
        }.filter { it.emoji.isNotEmpty() }.toList()

    /** [recents] with [emoji] moved (or added) to the front. */
    fun used(recents: List<String>, emoji: String): List<String> =
        (listOf(emoji) + recents.filter { it != emoji }).take(RECENTS)
}

/** Something copied: its text, whether the app marked it sensitive (a password), and when. */
data class Clip(val text: String, val sensitive: Boolean, val at: Long)

/**
 * Recent copies, newest first, for the toolbar. Like Gboard's clipboard they only last an hour,
 * and only a few are kept; copying the same text again moves it to the front.
 */
object ClipHistory {
    const val KEEP = 10
    const val LASTS_MS = 60 * 60 * 1000L
    /** Copied this recently: offered first and highlighted, to paste straight away. */
    const val FRESH_MS = 60 * 1000L

    fun add(history: List<Clip>, clip: Clip, now: Long): List<Clip> {
        if (clip.text.isBlank()) return current(history, now)
        return current(listOf(clip) + history.filter { it.text != clip.text }, now)
    }

    fun current(history: List<Clip>, now: Long): List<Clip> =
        history.filter { now - it.at in 0..LASTS_MS }.take(KEEP)

    /** What a chip shows: one line of the text, or dots for something sensitive. */
    fun label(clip: Clip): String =
        if (clip.sensitive) "••••••" else clip.text.trim().replace(Regex("\\s+"), " ").take(60)
}
