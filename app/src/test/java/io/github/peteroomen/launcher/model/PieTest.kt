package io.github.peteroomen.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PieTest {

    @Test fun slotsGoClockwiseFromTheTop() {
        assertEquals(0, Pie.slot(0f, -100f, 10f)) // up
        assertEquals(1, Pie.slot(70f, -70f, 10f)) // up-right
        assertEquals(2, Pie.slot(100f, 0f, 10f)) // right
        assertEquals(3, Pie.slot(70f, 70f, 10f)) // down-right
        assertEquals(4, Pie.slot(0f, 100f, 10f)) // down
        assertEquals(5, Pie.slot(-70f, 70f, 10f)) // down-left
        assertEquals(6, Pie.slot(-100f, 0f, 10f)) // left
        assertEquals(7, Pie.slot(-70f, -70f, 10f)) // up-left
    }

    @Test fun eachSlotCoversFortyFiveDegrees() {
        // 20° either side of straight up is still the top slot
        assertEquals(0, Pie.slot(36f, -100f, 10f))
        assertEquals(0, Pie.slot(-36f, -100f, 10f))
    }

    @Test fun middleIsNoSlot() {
        assertNull(Pie.slot(3f, 4f, 10f))
    }

    @Test fun slotAnglesMatchSlotLookup() {
        for (slot in 0 until Pie.SLOTS) {
            val a = Math.toRadians(Pie.angleOf(slot))
            assertEquals(slot, Pie.slot((100 * Math.cos(a)).toFloat(), (-100 * Math.sin(a)).toFloat(), 10f))
        }
    }
}
