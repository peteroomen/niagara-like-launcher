package io.github.peteroomen.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EditDragTest {
    // app, folder with two apps, app — rows 60 tall, folder children 50 each
    private val heights = listOf(60f, 160f, 60f)
    private val any = { _: Int -> true }

    @Test fun smallMovesStayPut() {
        assertEquals(DropTarget.At(0), EditDrag.resolve(heights, 60f, 0, 10f, canDropInto = any))
    }

    @Test fun overAFolderHeaderDropsInto() {
        // row 0 centre is 30; folder header middle is 60 + 30 = 90
        assertEquals(DropTarget.Into(1), EditDrag.resolve(heights, 60f, 0, 60f, canDropInto = any))
    }

    @Test fun pastAFolderSlotsInAfterIt() {
        // centre 30 + 200 = 230 is past the folder's middle (140) but before the last row's (250)
        assertEquals(DropTarget.At(1), EditDrag.resolve(heights, 60f, 0, 200f, canDropInto = any))
        assertEquals(DropTarget.At(2), EditDrag.resolve(heights, 60f, 0, 240f, canDropInto = any))
    }

    @Test fun dropIntoCanBeRefused() {
        assertEquals(DropTarget.At(0), EditDrag.resolve(heights, 60f, 0, 60f) { false })
    }

    @Test fun intoATallFolderFromBelow() {
        // app, folder with three apps (210), app. Dragging the last row (centre 300) up past the
        // folder's middle makes the folder slide down 60 to make room: its header is now 120..180
        val tall = listOf(60f, 210f, 60f)
        assertEquals(DropTarget.At(1), EditDrag.resolve(tall, 60f, 2, -140f, gap = 2, canDropInto = any))
        // Over the header where it is on screen, it drops in
        assertEquals(DropTarget.Into(1), EditDrag.resolve(tall, 60f, 2, -150f, gap = 1, canDropInto = any))
    }

    @Test fun rowsMakeRoom() {
        assertEquals(-60f, EditDrag.shift(1, 0, 2, 60f))
        assertEquals(0f, EditDrag.shift(1, 0, 0, 60f))
        assertEquals(60f, EditDrag.shift(0, 2, 0, 60f))
    }
}
