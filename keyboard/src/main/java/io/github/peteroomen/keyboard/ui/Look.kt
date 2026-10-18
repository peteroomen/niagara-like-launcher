package io.github.peteroomen.keyboard.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The keyboard's glass. The system blurs whatever app is behind the keyboard's window (Android
 * calls it cross-window blur); this is the tint over that blur, and the keys drawn on it.
 * Without the blur (Battery Saver turns it off) the tint gets much heavier, so keys stay readable.
 */
class Look(val dark: Boolean, val blur: Boolean, val accent: Color) {
    /** Over the blurred app, and under the navigation bar too (it's the window's background). */
    val tint: Color = if (dark) {
        Color(0xFF0E1216).copy(alpha = if (blur) 0.42f else 0.93f)
    } else {
        Color(0xFFEEF1F5).copy(alpha = if (blur) 0.5f else 0.95f)
    }

    val text: Color = if (dark) Color.White.copy(alpha = 0.95f) else Color(0xFF16191D)
    val hint: Color = text.copy(alpha = 0.45f)

    // Keys are flat, as the launcher draws things sitting on its glass sheet (its search box): a
    // translucent fill with a hairline edge, no glow or lit rim, since glass isn't stacked on glass.
    // Dark keys are kept gentle, as Gboard's are: over a black app the glass is nearly black too,
    // and brighter keys and rims stood out harshly from it.
    val keyFill: Color = if (dark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.7f)
    val functionFill: Color = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.4f)
    val keyEdge: Color = if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    /** Laid over a key while it's held */
    val pressed: Color = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)

    /** Enter and caps lock: the accent (the app's or the system's), with a glyph that reads on it */
    val onAccent: Color = if (accent.luminance() > 0.4f) Color(0xFF0E1216) else Color.White

    /** Just under the keyboard's top edge */
    val glow: Color = Color.White.copy(alpha = if (dark) 0.06f else 0.35f)

    /** The keyboard's own top edge: a hairline catching the light */
    val edge: Color = Color.White.copy(alpha = if (dark) 0.16f else 0.9f)

    /** The bubble over a held key and the accents row: nearly solid, since it sits over other keys */
    val bubble: Color = if (dark) Color(0xFF262B31).copy(alpha = 0.97f) else Color.White.copy(alpha = 0.98f)
}
