package io.github.peteroomen.keyboard.model

import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.text.BreakIterator

enum class Shift { OFF, ONCE, LOCKED }

object ShiftLogic {
    /** Tapping shift: once for one capital, twice quickly ([doubleTap]) for caps lock, again to let go. */
    fun tap(current: Shift, doubleTap: Boolean): Shift = when (current) {
        Shift.OFF -> Shift.ONCE
        Shift.ONCE -> if (doubleTap) Shift.LOCKED else Shift.OFF
        Shift.LOCKED -> Shift.OFF
    }

    /** After a character is typed: a one-off capital is used up, caps lock stays. */
    fun afterTyping(current: Shift): Shift = if (current == Shift.ONCE) Shift.OFF else current
}

/** What the Enter key does, and so what it shows. */
enum class EnterAction { RETURN, SEARCH, SEND, GO, NEXT, PREVIOUS, DONE }

object Fields {

    fun enterAction(imeOptions: Int, inputType: Int): EnterAction {
        val multiLine = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (multiLine || imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.RETURN
        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            else -> EnterAction.RETURN
        }
    }

    /** The editor action Enter performs, for actions other than [EnterAction.RETURN]. */
    fun editorAction(action: EnterAction): Int = when (action) {
        EnterAction.SEARCH -> EditorInfo.IME_ACTION_SEARCH
        EnterAction.SEND -> EditorInfo.IME_ACTION_SEND
        EnterAction.GO -> EditorInfo.IME_ACTION_GO
        EnterAction.NEXT -> EditorInfo.IME_ACTION_NEXT
        EnterAction.PREVIOUS -> EditorInfo.IME_ACTION_PREVIOUS
        EnterAction.DONE -> EditorInfo.IME_ACTION_DONE
        EnterAction.RETURN -> EditorInfo.IME_ACTION_UNSPECIFIED
    }

    fun kind(inputType: Int): FieldKind {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> FieldKind.NUMBER
            InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URI
                else -> FieldKind.TEXT
            }
            else -> FieldKind.TEXT
        }
    }
}

object Text {
    /**
     * A second quick space after a word becomes ". ": true when [before] (the text before the
     * cursor) ends in a letter, digit or closing mark followed by one space.
     */
    fun doubleSpacePeriod(before: CharSequence): Boolean {
        if (before.length < 2 || before.last() != ' ') return false
        val c = before[before.length - 2]
        return c.isLetterOrDigit() || c in "\"')]}’”"
    }

    /** How many chars backspace removes: the last whole character, so an emoji goes in one go. */
    fun lastCharacterLength(before: CharSequence): Int {
        if (before.isEmpty()) return 1
        val text = before.toString()
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        val start = it.preceding(text.length)
        return if (start == BreakIterator.DONE) 1 else (text.length - start).coerceAtLeast(1)
    }

    /** [text] as typed with shift: uppercase, unless that changes its length (ß stays ß). */
    fun shifted(text: String): String = text.uppercase().takeIf { it.length == text.length } ?: text
}
