package io.github.peteroomen.keyboard.model

/**
 * The words before the cursor, as the engine's n-gram context: up to three, most recent first,
 * and whether a sentence starts just before them (which the engine predicts from too).
 */
data class Context(val previous: List<String>, val sentenceStart: Boolean)

/** A key the engine needs to know about: its character and where it is, in keyboard pixels. */
data class EngineKey(val code: Int, val box: Box)

object Prediction {
    const val MAX_PREVIOUS = 3
    const val GRID_WIDTH = 32
    const val GRID_HEIGHT = 16
    const val MAX_PROXIMITY_CHARS = 16
    const val NOT_A_CODE = -1

    /** AOSP's "modest" autocorrect: a fix whose normalised score is at least this goes in. */
    const val AUTOCORRECT_THRESHOLD = 0.185f

    private const val SENTENCE_ENDS = ".!?"

    /** Reads the words before the cursor from [before] (the text up to it, not the word being typed). */
    fun context(before: CharSequence): Context {
        val previous = ArrayList<String>()
        var i = before.length
        while (previous.size < MAX_PREVIOUS) {
            // Skip spaces and punctuation back to a word; a sentence end on the way starts a sentence
            while (i > 0 && !Words.isWordChar(before[i - 1], inWord = false)) {
                if (before[i - 1] in SENTENCE_ENDS || before[i - 1] == '\n') return Context(previous, sentenceStart = true)
                i--
            }
            if (i == 0) return Context(previous, sentenceStart = true)
            val end = i
            while (i > 0 && Words.isWordChar(before[i - 1], inWord = true)) i--
            // Text cut off mid-word at the start of what we were given: not a whole word, stop
            if (i == 0 && before.length >= 48) return Context(previous, sentenceStart = false)
            previous += before.substring(i, end)
        }
        return Context(previous, sentenceStart = false)
    }

    /**
     * For each cell of the engine's grid over the keyboard, the keys a touch in it might have
     * meant: those within 1.2 key widths of its centre, as AOSP's ProximityInfo works it out.
     */
    fun proximityGrid(keys: List<EngineKey>, width: Int, height: Int, keyWidth: Int): IntArray {
        val cellWidth = (width + GRID_WIDTH - 1) / GRID_WIDTH
        val cellHeight = (height + GRID_HEIGHT - 1) / GRID_HEIGHT
        val threshold = keyWidth * 1.2f
        val grid = IntArray(GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS) { NOT_A_CODE }
        for (gy in 0 until GRID_HEIGHT) for (gx in 0 until GRID_WIDTH) {
            val cx = gx * cellWidth + cellWidth / 2f
            val cy = gy * cellHeight + cellHeight / 2f
            var at = (gy * GRID_WIDTH + gx) * MAX_PROXIMITY_CHARS
            val end = at + MAX_PROXIMITY_CHARS
            for (key in keys) {
                if (at == end) break
                if (squaredDistanceToEdge(key.box, cx, cy) < threshold * threshold) grid[at++] = key.code
            }
        }
        return grid
    }

    private fun squaredDistanceToEdge(box: Box, x: Float, y: Float): Float {
        val dx = when {
            x < box.left -> box.left - x
            x > box.right -> x - box.right
            else -> 0f
        }
        val dy = when {
            y < box.top -> box.top - y
            y > box.bottom -> y - box.bottom
            else -> 0f
        }
        return dx * dx + dy * dy
    }

    /**
     * Whether [typed] may be autocorrected at all, as AOSP's Suggest decides: more than one
     * letter, no digits, not deliberately in capitals, and not a word the dictionaries know.
     */
    fun mayAutocorrect(typed: String, typedIsKnown: Boolean): Boolean =
        typed.length > 1 && typed.none { it.isDigit() } && typed.count { it.isUpperCase() } <= 1 && !typedIsKnown
}
