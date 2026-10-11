package io.github.peteroomen.launcher.model

/**
 * Blurs ARGB pixels in place. Three passes of a box blur look close to a Gaussian blur and run in
 * time proportional to the pixel count, whatever the radius.
 */
object BoxBlur {

    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        if (radius < 1 || width < 1 || height < 1) return
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            pass(pixels, scratch, width, height, radius, horizontal = true)
            pass(scratch, pixels, width, height, radius, horizontal = false)
        }
    }

    /** One box blur pass along rows or columns, clamping at the edges. */
    private fun pass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, length - 1)
                return if (horizontal) src[line * width + c] else src[c * width + line]
            }
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = at(i)
                a += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until length) {
                val out = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                if (horizontal) dst[line * width + i] = out else dst[i * width + line] = out
                val leaving = at(i - radius)
                val entering = at(i + radius + 1)
                a += (entering ushr 24) - (leaving ushr 24)
                r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }
}
