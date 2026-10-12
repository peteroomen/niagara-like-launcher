package io.github.peteroomen.launcher.model

/** Where a dragged row in Edit favourites would land. */
sealed interface DropTarget {
    /** Dropped onto another entry: into a folder, or onto an app to make a folder. */
    data class Into(val index: Int) : DropTarget
    /** Moved to this position in the list (as for [FavOps.move]). */
    data class At(val index: Int) : DropTarget
}

/** Drag maths for a list of rows of different heights (folders are taller, they list their apps). */
object EditDrag {

    /**
     * [heights] are the rows' heights; each row starts with a header of height [header]. The dragged
     * row's centre over the middle of another row's header means [DropTarget.Into], if allowed;
     * anywhere else it slots in between rows.
     *
     * [gap] is where the other rows have currently made room (the last [DropTarget.At]). Headers are
     * hit where they are on screen, slid aside for the gap, not where they'd be without it: otherwise
     * a tall folder that slid down out of the way can't be dropped into from below.
     */
    fun resolve(heights: List<Float>, header: Float, from: Int, offset: Float, gap: Int = from, canDropInto: (Int) -> Boolean): DropTarget {
        val tops = heights.runningFold(0f) { acc, h -> acc + h }
        val center = tops[from] + heights[from] / 2 + offset
        for (j in heights.indices) {
            if (j == from || !canDropInto(j)) continue
            val top = tops[j] + shift(j, from, gap, heights[from])
            if (center in (top + header * 0.3f)..(top + header * 0.7f)) return DropTarget.Into(j)
        }
        return DropTarget.At(heights.indices.count { it != from && tops[it] + heights[it] / 2 < center })
    }

    /**
     * How far row [index] should slide to make room at [gap] while row [from] (of height
     * [draggedHeight]) is dragged. Rows stay where they are while hovering to drop into one.
     */
    fun shift(index: Int, from: Int, gap: Int, draggedHeight: Float): Float {
        if (from < 0) return 0f
        val to = gap
        return when {
            from < to && index in (from + 1)..to -> -draggedHeight
            from > to && index in to until from -> draggedHeight
            else -> 0f
        }
    }
}
