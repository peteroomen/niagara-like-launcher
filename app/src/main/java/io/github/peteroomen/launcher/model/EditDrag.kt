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
     */
    fun resolve(heights: List<Float>, header: Float, from: Int, offset: Float, canDropInto: (Int) -> Boolean): DropTarget {
        val tops = heights.runningFold(0f) { acc, h -> acc + h }
        val center = tops[from] + heights[from] / 2 + offset
        for (j in heights.indices) {
            if (j == from || !canDropInto(j)) continue
            if (center in (tops[j] + header * 0.3f)..(tops[j] + header * 0.7f)) return DropTarget.Into(j)
        }
        return DropTarget.At(heights.indices.count { it != from && tops[it] + heights[it] / 2 < center })
    }

    /** How far row [index] should slide to make room while row [from] (of height [draggedHeight]) is dragged. */
    fun shift(index: Int, from: Int, target: DropTarget?, draggedHeight: Float): Float {
        if (target !is DropTarget.At || from < 0) return 0f
        val to = target.index
        return when {
            from < to && index in (from + 1)..to -> -draggedHeight
            from > to && index in to until from -> draggedHeight
            else -> 0f
        }
    }
}
