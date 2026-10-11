package io.github.peteroomen.launcher.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What the finger is on: a slot, and for a folder slot pushed past, one of the folder's apps. */
data class PieHit(val slot: Int?, val folderIndex: Int? = null)

/**
 * The pie has eight slots, clockwise from the top: N, NE, E, SE, S, SW, W, NW. A folder's apps fan
 * out in an arc beyond the folder; push past the folder and sweep along the arc to pick one.
 */
object Pie {
    const val SLOTS = 8

    /**
     * Which slot the finger points at, given its offset from the pie's centre (screen coordinates,
     * y down). Null inside the dead zone in the middle.
     */
    fun slot(dx: Float, dy: Float, deadZone: Float): Int? {
        if (hypot(dx, dy) < deadZone) return null
        return Math.floorMod(((90 - angle(dx, dy)) / 45).roundToInt(), SLOTS)
    }

    /** Angle in degrees (0 = right, 90 = up) of a slot's centre. */
    fun angleOf(slot: Int): Double = 90.0 - slot * 45.0

    /** Angles of a folder's [count] apps, centred on the folder's slot, in clockwise order. */
    fun arcAngles(slot: Int, count: Int): List<Double> {
        val spacing = if (count <= 1) 0.0 else minOf(30.0, 240.0 / (count - 1))
        return (0 until count).map { angleOf(slot) - (it - (count - 1) / 2.0) * spacing }
    }

    /**
     * Full hit test. [folderSize] is the number of apps in the folder at a slot (0 for apps and empty
     * slots). Beyond [folderRing] over a folder, the finger picks from its arc. [previous] is the
     * last hit: a folder the finger was just on keeps its arc in play, so sweeping along a wide arc
     * (or flicking straight out to its end) doesn't jump to the neighbouring slot. The arc lets go
     * once the finger comes back well inside the ring.
     */
    fun hit(dx: Float, dy: Float, deadZone: Float, folderRing: Float, folderSize: (Int) -> Int, previous: PieHit?): PieHit {
        val distance = hypot(dx, dy)
        val inner = slot(dx, dy, deadZone) ?: return PieHit(null)
        val wasOnFolder = previous?.slot?.takeIf { folderSize(it) > 0 }
        val folder = when {
            wasOnFolder != null && previous.folderIndex != null && distance > folderRing * 0.75f -> wasOnFolder
            wasOnFolder != null && distance > folderRing -> wasOnFolder
            distance > folderRing && folderSize(inner) > 0 -> inner
            else -> null
        } ?: return PieHit(inner)
        val a = angle(dx, dy)
        val arc = arcAngles(folder, folderSize(folder))
        return PieHit(folder, arc.indices.minBy { difference(arc[it], a) })
    }

    private fun angle(dx: Float, dy: Float) = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))

    private fun difference(a: Double, b: Double) = abs(((a - b) % 360 + 540) % 360 - 180)
}
