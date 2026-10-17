package io.github.peteroomen.keyboard.model

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModelTest {

    @Test
    fun everyRowFillsTheKeyboard() {
        for (page in Page.entries) for (field in FieldKind.entries) {
            Layouts.build(page, field).rows.forEachIndexed { i, row ->
                assertEquals("$page $field row $i", Layouts.ROW_WIDTH, row.width, 0.001f)
            }
        }
    }

    @Test
    fun topRowLettersGiveTheirNumberFirst() {
        val top = Layouts.build(Page.LETTERS, FieldKind.TEXT).rows[0].keys.map { it as CharKey }
        assertEquals("1234567890".map { it.toString() }, top.map { it.alternates.first() })
        // Macrons first among the accents, for te reo Māori
        assertEquals("ā", (Layouts.build(Page.LETTERS, FieldKind.TEXT).rows[1].keys[0] as CharKey).alternates.first())
    }

    @Test
    fun emailAndWebFieldsSwapTheCommaKey() {
        fun left(field: FieldKind) = (Layouts.build(Page.LETTERS, field).rows[3].keys[1] as CharKey).text
        assertEquals(",", left(FieldKind.TEXT))
        assertEquals("@", left(FieldKind.EMAIL))
        assertEquals("/", left(FieldKind.URI))
    }

    @Test
    fun numberFieldsOpenOnTheNumberPad() {
        assertEquals(Page.NUMBERS, Layouts.firstPage(FieldKind.NUMBER))
        assertEquals(Page.NUMBERS, Layouts.firstPage(FieldKind.PHONE))
        assertEquals(Page.LETTERS, Layouts.firstPage(FieldKind.EMAIL))
    }

    @Test
    fun shiftTapsOnceTwiceAndOff() {
        assertEquals(Shift.ONCE, ShiftLogic.tap(Shift.OFF, doubleTap = false))
        assertEquals(Shift.OFF, ShiftLogic.tap(Shift.ONCE, doubleTap = false))
        assertEquals(Shift.LOCKED, ShiftLogic.tap(Shift.ONCE, doubleTap = true))
        assertEquals(Shift.OFF, ShiftLogic.tap(Shift.LOCKED, doubleTap = true))
        assertEquals(Shift.OFF, ShiftLogic.afterTyping(Shift.ONCE))
        assertEquals(Shift.LOCKED, ShiftLogic.afterTyping(Shift.LOCKED))
    }

    @Test
    fun enterFollowsTheField() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(EnterAction.SEARCH, Fields.enterAction(EditorInfo.IME_ACTION_SEARCH, text))
        assertEquals(EnterAction.SEND, Fields.enterAction(EditorInfo.IME_ACTION_SEND, text))
        assertEquals(EnterAction.RETURN, Fields.enterAction(EditorInfo.IME_ACTION_SEND, text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertEquals(EnterAction.RETURN, Fields.enterAction(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION, text))
        assertEquals(EnterAction.RETURN, Fields.enterAction(EditorInfo.IME_ACTION_UNSPECIFIED, text))
        assertEquals(EditorInfo.IME_ACTION_NEXT, Fields.editorAction(EnterAction.NEXT))
    }

    @Test
    fun fieldKinds() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(FieldKind.EMAIL, Fields.kind(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(FieldKind.URI, Fields.kind(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(FieldKind.TEXT, Fields.kind(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(FieldKind.NUMBER, Fields.kind(InputType.TYPE_CLASS_NUMBER))
        assertEquals(FieldKind.PHONE, Fields.kind(InputType.TYPE_CLASS_PHONE))
    }

    @Test
    fun touchesGoToTheNearestKey() {
        val layout = Layouts.build(Page.LETTERS, FieldKind.TEXT)
        val keys = Geometry.place(layout, width = 1000f, rowHeight = 100f)
        fun at(x: Float, y: Float) = Geometry.hit(keys, x, y)!!.key
        assertEquals("q", (at(5f, 50f) as CharKey).text)
        assertEquals("p", (at(995f, 50f) as CharKey).text)
        // The middle row's indent belongs to its end keys
        assertEquals("a", (at(10f, 150f) as CharKey).text)
        assertEquals("l", (at(990f, 150f) as CharKey).text)
        // Above or below the keys: the nearest row
        assertEquals("q", (at(5f, -20f) as CharKey).text)
        assertEquals(Action.SPACE, (at(500f, 999f) as ActionKey).action)
        assertEquals(Action.SHIFT, (at(20f, 250f) as ActionKey).action)
    }

    @Test
    fun doubleSpaceAfterAWord() {
        assertTrue(Text.doubleSpacePeriod("word "))
        assertTrue(Text.doubleSpacePeriod("it\" "))
        assertFalse(Text.doubleSpacePeriod("word  "))
        assertFalse(Text.doubleSpacePeriod("word, "))
        assertFalse(Text.doubleSpacePeriod(" "))
    }

    @Test
    fun backspaceTakesAWholeCharacter() {
        assertEquals(1, Text.lastCharacterLength("abc"))
        assertEquals(2, Text.lastCharacterLength("hi 😀"))
        assertEquals(1, Text.lastCharacterLength(""))
    }

    @Test
    fun shiftedText() {
        assertEquals("Ā", Text.shifted("ā"))
        assertEquals("ß", Text.shifted("ß"))
        assertEquals("1", Text.shifted("1"))
    }
}
