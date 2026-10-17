package io.github.peteroomen.keyboard.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The keyboard's glass. The system blurs whatever app is behind the keyboard's window (Android
 * calls it cross-window blur); this is the tint over that blur, and the keys drawn on it: frosted
 * caps with a lit rim and a soft glow along their tops, after the launcher's frosted glass.
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

    val keyFill: Color = if (dark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.55f)
    val functionFill: Color = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.24f)
    /** Laid over a key while it's held */
    val pressed: Color = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.08f)

    /** Enter and caps lock: the system accent, with a glyph that reads on it */
    val onAccent: Color = if (dark) Color(0xFF0E1216) else Color.White

    /** A rim lit from the top left, with a fainter catch at the bottom right, like the launcher's glass. */
    val rim: Brush = if (dark) {
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.5f),
            0.35f to Color.White.copy(alpha = 0.12f),
            0.7f to Color.White.copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = 0.3f),
        )
    } else {
        Brush.linearGradient(
            0f to Color.White,
            0.5f to Color.White.copy(alpha = 0.6f),
            1f to Color.Black.copy(alpha = 0.08f),
        )
    }
    val glow: Color = Color.White.copy(alpha = if (dark) 0.1f else 0.35f)

    /** The keyboard's own top edge: a hairline catching the light */
    val edge: Color = Color.White.copy(alpha = if (dark) 0.28f else 0.9f)

    /** The bubble over a held key and the accents row: nearly solid, since it sits over other keys */
    val bubble: Color = if (dark) Color(0xFF262B31).copy(alpha = 0.97f) else Color.White.copy(alpha = 0.98f)
}
