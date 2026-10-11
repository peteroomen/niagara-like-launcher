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

class PieFolderTest {
    private val folderAt2 = { slot: Int -> if (slot == 2) 3 else 0 } // three apps in the right-hand slot

    private fun at(degrees: Double, distance: Float): Pair<Float, Float> {
        val r = Math.toRadians(degrees)
        return (distance * Math.cos(r)).toFloat() to (-distance * Math.sin(r)).toFloat()
    }

    @Test fun onTheFolderItselfIsJustTheSlot() {
        val (dx, dy) = at(0.0, 80f)
        org.junit.Assert.assertEquals(PieHit(2), Pie.hit(dx, dy, 30f, 120f, folderAt2, null))
    }

    @Test fun flickingStraightFromTheFolderToTheArcEndStaysInTheFolder() {
        // +30° is in the NE sector, but the finger was just on the folder
        val (dx, dy) = at(30.0, 150f)
        org.junit.Assert.assertEquals(PieHit(2, 0), Pie.hit(dx, dy, 30f, 120f, folderAt2, previous = PieHit(2)))
    }

    @Test fun pushingPastAFolderPicksFromItsArc() {
        // arc for 3 apps around 0°: +30°, 0°, -30° (clockwise)
        val (dx, dy) = at(20.0, 150f)
        org.junit.Assert.assertEquals(PieHit(2, 0), Pie.hit(dx, dy, 30f, 120f, folderAt2, null))
        val (dx2, dy2) = at(-20.0, 150f)
        org.junit.Assert.assertEquals(PieHit(2, 2), Pie.hit(dx2, dy2, 30f, 120f, folderAt2, null))
    }

    @Test fun lockedArcHoldsWhenSweepingIntoTheNextSector() {
        // 50° is in the NE slot's sector, but we're already in the right-hand folder's arc
        val (dx, dy) = at(50.0, 150f)
        org.junit.Assert.assertEquals(PieHit(2, 0), Pie.hit(dx, dy, 30f, 120f, folderAt2, previous = PieHit(2, 0)))
        org.junit.Assert.assertEquals(PieHit(1), Pie.hit(dx, dy, 30f, 120f, folderAt2, previous = null))
    }

    @Test fun pushingPastAnAppIsStillTheApp() {
        val (dx, dy) = at(90.0, 200f)
        org.junit.Assert.assertEquals(PieHit(0), Pie.hit(dx, dy, 30f, 120f, folderAt2, null))
    }
}
