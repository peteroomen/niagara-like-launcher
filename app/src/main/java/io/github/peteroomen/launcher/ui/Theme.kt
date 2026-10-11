package io.github.peteroomen.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import io.github.peteroomen.launcher.data.IconCache

/** Colours for drawing straight onto the wallpaper. */
data class Palette(
    val darkText: Boolean,
    val text: Color,
    val muted: Color,
    val accent: Color,
    /** Themed icon tile and glyph */
    val tileBg: Color,
    val tileFg: Color,
    /** Backing for floating bits like the folder row */
    val pill: Color,
    /** Ring around notification dots */
    val ring: Color,
    /** Dims the wallpaper behind the A–Z list */
    val scrim: Color,
) {
    /** A soft shadow keeps light text readable on busy wallpapers. */
    val shadow: Shadow? get() = if (darkText) null else Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 10f)
}

val LocalPalette = staticCompositionLocalOf<Palette> { error("No palette") }

interface IconLoader {
    fun cached(key: String, sizePx: Int): IconCache.Icons?
    suspend fun load(key: String, sizePx: Int): IconCache.Icons?
}

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("No icon loader") }
