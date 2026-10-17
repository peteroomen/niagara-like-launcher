package io.github.peteroomen.keyboard.model

import android.text.InputType

/**
 * The suggestion strip, Gboard's three slots left to right (a slot can be empty), and the word a
 * space would put in place of the typed one, if the spell checker is sure it's a typo.
 */
data class Strip(val typed: String, val slots: List<String?>, val autocorrect: String?)

/**
 * Words being typed: the keyboard keeps the word under way as composing text (provisional, as
 * Gboard does) and only commits it when it's finished, so the app doesn't re-check and redraw
 * the text around it on every letter.
 */
object Words {

    /** Letters and digits make a word; an apostrophe too, once a word has started (don't, it's). */
    fun isWordChar(c: Char, inWord: Boolean) = c.isLetterOrDigit() || (inWord && (c == '\'' || c == '’'))

    /** Typing these after a word autocorrects it, as a space does. */
    fun endsSentenceOrClause(c: Char) = c in ".,!?;:"

    /** Fields where words are composed: plain text, not passwords, addresses or numbers. */
    fun composes(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> false
            else -> true
        }
    }

    /** Fields that ask for no suggestions (and so no autocorrect either). */
    fun suggests(inputType: Int) = composes(inputType) && inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0

    /** [suggestion] in the typed word's case: "Teh" → "The", "TEH" → "THE". */
    fun matchCase(typed: String, suggestion: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> suggestion.uppercase()
        typed.firstOrNull()?.isUpperCase() == true -> suggestion.replaceFirstChar { it.uppercaseChar() }
        else -> suggestion
    }

    /**
     * Lays out the spell checker's answer for [typed]. Sure it's a typo with a recommended fix: the
     * fix goes in the middle (and on space), the typed word to its left. Otherwise the typed word
     * is in the middle with suggestions either side.
     */
    fun strip(typed: String, found: List<String>, looksLikeTypo: Boolean, recommended: Boolean): Strip {
        val others = found.map { matchCase(typed, it) }.filter { !it.equals(typed, ignoreCase = false) }.distinct()
        val fix = others.firstOrNull()?.takeIf { looksLikeTypo && recommended }
        return if (fix != null) {
            Strip(typed, listOf(typed, fix, others.getOrNull(1)), autocorrect = fix)
        } else {
            Strip(typed, listOf(others.getOrNull(0), typed, others.getOrNull(1)), autocorrect = null)
        }
    }

    /** Next-word predictions, best first, laid out as Gboard does: the best in the middle. */
    fun predictions(next: List<String>) = Strip("", listOf(next.getOrNull(1), next.getOrNull(0), next.getOrNull(2)), autocorrect = null)

    /**
     * The strip the moment [typed] changes, before the spell checker answers: the typed word in the
     * middle, the last suggestions either side (so the strip doesn't blink), and no autocorrect yet.
     */
    fun typing(last: Strip?, typed: String): Strip {
        // Predictions (nothing typed) aren't suggestions for this word: start afresh
        val last = last?.takeIf { it.typed.isNotEmpty() }
        val left = last?.slots?.getOrNull(0)?.takeIf { it != last.typed }
        val right = last?.slots?.getOrNull(2)
        return Strip(typed, listOf(left, typed, right), autocorrect = null)
    }
}
