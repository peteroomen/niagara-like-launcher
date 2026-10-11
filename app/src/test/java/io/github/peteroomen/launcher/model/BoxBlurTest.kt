package io.github.peteroomen.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {

    @Test fun flatColourStaysTheSame() {
        val colour = 0xFF336699.toInt()
        val pixels = IntArray(20 * 10) { colour }
        BoxBlur.blur(pixels, 20, 10, radius = 3)
        assertTrue(pixels.all { it == colour })
    }

    @Test fun aBrightDotSpreadsOutAndFades() {
        val w = 21
        val pixels = IntArray(w * w) { 0xFF000000.toInt() }
        pixels[10 * w + 10] = 0xFFFFFFFF.toInt()
        BoxBlur.blur(pixels, w, w, radius = 2)
        val centre = pixels[10 * w + 10] and 0xFF
        val near = pixels[10 * w + 12] and 0xFF
        val far = pixels[0] and 0xFF
        assertTrue("centre $centre should be dimmer than the original dot", centre in 1..254)
        assertTrue("light reaches nearby pixels", near > 0)
        assertEquals("corners stay dark", 0, far)
        assertEquals("alpha is kept", 0xFF, pixels[0] ushr 24)
    }
}
