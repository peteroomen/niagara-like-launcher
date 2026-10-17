package io.github.peteroomen.keyboard.model

import kotlin.math.abs

/** A rectangle in pixels (kept free of Android types so it's testable on the JVM). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    operator fun contains(p: Pair<Float, Float>) = p.first in left..right && p.second in top..bottom
}

/** A key and the cell it owns: the whole cell, gaps included, so no touch lands between keys. */
data class Placed(val key: Key, val row: Int, val cell: Box)

object Geometry {

    /** Lays [layout] out across [width] pixels, each row [rowHeight] tall. */
    fun place(layout: Layout, width: Float, rowHeight: Float): List<Placed> {
        val unit = width / Layouts.ROW_WIDTH
        return layout.rows.flatMapIndexed { r, row ->
            // A row narrower than the keyboard is centred, its spare width split between the ends
            var x = (width - row.width * unit) / 2 + row.indent * unit
            row.keys.map { key ->
                val w = key.width * unit
                Placed(key, r, Box(x, r * rowHeight, x + w, (r + 1) * rowHeight)).also { x += w }
            }
        }
    }

    /**
     * The key for a touch at [x], [y]: the row it's in (the nearest, if it's above or below the
     * keys), then the nearest key in that row, so a touch in an indent goes to the end key.
     */
    fun hit(keys: List<Placed>, x: Float, y: Float): Placed? {
        if (keys.isEmpty()) return null
        val rows = keys.maxOf { it.row } + 1
        val rowHeight = keys.first().cell.height
        val row = (y / rowHeight).toInt().coerceIn(0, rows - 1)
        return keys.filter { it.row == row }.minByOrNull { p ->
            when {
                x < p.cell.left -> p.cell.left - x
                x > p.cell.right -> x - p.cell.right
                else -> 0f
            } + abs(x - p.cell.centerX) * 0.0001f // tie-break on a shared edge
        }
    }
}
