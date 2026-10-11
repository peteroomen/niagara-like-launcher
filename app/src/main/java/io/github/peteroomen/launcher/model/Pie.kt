package io.github.peteroomen.launcher.model

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The pie has eight slots, clockwise from the top: N, NE, E, SE, S, SW, W, NW. */
object Pie {
    const val SLOTS = 8

    /**
     * Which slot the finger points at, given its offset from the pie's centre (screen coordinates,
     * y down). Null inside the dead zone in the middle.
     */
    fun slot(dx: Float, dy: Float, deadZone: Float): Int? {
        if (hypot(dx, dy) < deadZone) return null
        val degrees = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())) // 0 = right, 90 = up
        return Math.floorMod(((90 - degrees) / 45).roundToInt(), SLOTS)
    }

    /** Angle in degrees (0 = right, 90 = up) of a slot's centre. */
    fun angleOf(slot: Int): Double = 90.0 - slot * 45.0
}
