package io.github.peteroomen.launcher.system

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import io.github.peteroomen.launcher.ui.Palette
import kotlin.math.abs

/** Text, accent and themed-icon colours derived from the current wallpaper. */
object PaletteSource {

    fun read(context: Context): Palette =
        from(runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull())

    fun from(colors: WallpaperColors?): Palette {
        val darkText = colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        val hsl = FloatArray(3)
        // The most vivid of the wallpaper's main colours becomes the accent hue
        val vivid = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
            .map { it.toArgb() }
            .maxByOrNull { c -> ColorUtils.colorToHSL(c, hsl); hsl[1] * (1 - abs(hsl[2] - 0.5f) * 1.6f) }
            ?: 0xFF8899AA.toInt()
        ColorUtils.colorToHSL(vivid, hsl)
        val h = hsl[0]
        val s = hsl[1].coerceIn(0.35f, 0.85f)
        fun hsl(hue: Float, sat: Float, light: Float, alpha: Float = 1f) =
            Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, light))).copy(alpha = alpha)

        return if (darkText) Palette(
            darkText = true,
            text = Color(0xFF141414),
            muted = Color(0xAD141414),
            accent = hsl(h, s, 0.30f),
            tileBg = hsl(h, 0.5f, 0.84f, 0.92f),
            tileFg = hsl(h, 0.6f, 0.24f),
            pill = Color.White.copy(alpha = 0.6f),
            ring = Color.White.copy(alpha = 0.9f),
            scrim = Color.White.copy(alpha = 0.6f),
        ) else Palette(
            darkText = false,
            text = Color(0xFFFAFAFA),
            muted = Color(0xB3FAFAFA),
            accent = hsl(h, s, 0.72f),
            tileBg = hsl(h, 0.35f, 0.14f, 0.55f),
            tileFg = hsl(h, 0.85f, 0.84f),
            pill = Color.Black.copy(alpha = 0.4f),
            ring = Color.Black.copy(alpha = 0.45f),
            scrim = Color.Black.copy(alpha = 0.45f),
        )
    }
}
