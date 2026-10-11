package io.github.peteroomen.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EditDragTest {
    // app, folder with two apps, app — rows 60 tall, folder children 50 each
    private val heights = listOf(60f, 160f, 60f)
    private val any = { _: Int -> true }

    @Test fun smallMovesStayPut() {
        assertEquals(DropTarget.At(0), EditDrag.resolve(heights, 60f, 0, 10f, any))
    }

    @Test fun overAFolderHeaderDropsInto() {
        // row 0 centre is 30; folder header middle is 60 + 30 = 90
        assertEquals(DropTarget.Into(1), EditDrag.resolve(heights, 60f, 0, 60f, any))
    }

    @Test fun pastAFolderSlotsInAfterIt() {
        // centre 30 + 200 = 230 is past the folder's middle (140) but before the last row's (250)
        assertEquals(DropTarget.At(1), EditDrag.resolve(heights, 60f, 0, 200f, any))
        assertEquals(DropTarget.At(2), EditDrag.resolve(heights, 60f, 0, 240f, any))
    }

    @Test fun dropIntoCanBeRefused() {
        assertEquals(DropTarget.At(0), EditDrag.resolve(heights, 60f, 0, 60f) { false })
    }

    @Test fun rowsMakeRoom() {
        assertEquals(-60f, EditDrag.shift(1, 0, DropTarget.At(2), 60f))
        assertEquals(0f, EditDrag.shift(1, 0, DropTarget.Into(1), 60f))
        assertEquals(60f, EditDrag.shift(0, 2, DropTarget.At(0), 60f))
    }
}
