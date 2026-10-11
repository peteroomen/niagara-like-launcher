package io.github.peteroomen.launcher.system

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.core.graphics.ColorUtils
import io.github.peteroomen.launcher.data.Accent
import io.github.peteroomen.launcher.data.Appearance
import io.github.peteroomen.launcher.data.IconShape
import io.github.peteroomen.launcher.data.LauncherFont
import io.github.peteroomen.launcher.data.TextShadow
import io.github.peteroomen.launcher.data.TextTone
import io.github.peteroomen.launcher.ui.Palette
import kotlin.math.abs

/** Turns the wallpaper's colours and the appearance settings into a [Palette]. */
object PaletteSource {

    fun wallpaperColors(context: Context): WallpaperColors? =
        runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()

    fun resolve(colors: WallpaperColors?, a: Appearance): Palette {
        val wallpaperWantsDarkText = colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        val darkText = when (a.textTone) {
            TextTone.AUTO -> wallpaperWantsDarkText
            TextTone.LIGHT -> false
            TextTone.DARK -> true
        }

        val hsl = FloatArray(3)
        val (h, s) = when {
            a.accent == Accent.MONO -> 0f to 0f
            a.accent.hue != null -> a.accent.hue to 0.7f
            else -> {
                // The most vivid of the wallpaper's main colours becomes the accent hue
                val vivid = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
                    .map { it.toArgb() }
                    .maxByOrNull { c -> ColorUtils.colorToHSL(c, hsl); hsl[1] * (1 - abs(hsl[2] - 0.5f) * 1.6f) }
                    ?: 0xFF8899AA.toInt()
                ColorUtils.colorToHSL(vivid, hsl)
                hsl[0] to hsl[1].coerceIn(0.35f, 0.85f)
            }
        }
        // Mono accent means no hue at all: greys only
        fun hsl(sat: Float, light: Float, alpha: Float = 1f) =
            Color(ColorUtils.HSLToColor(floatArrayOf(h, if (s == 0f) 0f else sat, light))).copy(alpha = alpha)

        val ink = if (darkText) Color(0xFF141414) else Color(0xFFFAFAFA)
        val opposite = if (darkText) Color.White else Color.Black
        val accent = when {
            a.accent == Accent.MONO -> ink
            darkText -> hsl(s, 0.30f)
            else -> hsl(s, 0.72f)
        }

        return Palette(
            darkText = darkText,
            text = ink,
            muted = ink.copy(alpha = if (darkText) 0.68f else 0.72f),
            accent = accent,
            tileBg = if (darkText) hsl(0.5f, 0.84f, 0.92f) else hsl(0.35f, 0.14f, 0.55f),
            tileFg = if (darkText) hsl(0.6f, 0.24f) else hsl(0.85f, 0.84f),
            pill = opposite.copy(alpha = if (darkText) 0.6f else 0.4f),
            ring = opposite.copy(alpha = if (darkText) 0.9f else 0.45f),
            scrim = opposite.copy(alpha = if (darkText) 0.6f else 0.45f),
            card = opposite.copy(alpha = if (darkText) 0.55f else 0.35f),
            backdrop = opposite,
            shadow = shadow(a.textShadow, opposite),
            font = font(a.font),
            iconShape = when (a.iconShape) {
                IconShape.ROUNDED -> RoundedCornerShape(percent = 32)
                IconShape.CIRCLE -> CircleShape
                IconShape.SQUARE -> RoundedCornerShape(percent = 14)
            },
            glass = a.glass,
            // Dark glass under light text, milky glass under dark text: enough tint to read over busy
            // photos without hiding them. Without the wallpaper to blur, a heavier frosted tint.
            glassTint = if (darkText) Color.White.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.2f),
            glassTintNoBlur = if (darkText) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.42f),
        )
    }

    /** Softer shadows blur wide and faint; stronger ones are tight and dark, almost an outline. */
    private fun shadow(level: TextShadow, colour: Color): Shadow? = when (level) {
        TextShadow.OFF -> null
        TextShadow.SOFT -> Shadow(colour.copy(alpha = 0.35f), Offset(0f, 1f), 10f)
        TextShadow.MEDIUM -> Shadow(colour.copy(alpha = 0.6f), Offset(0f, 1f), 6f)
        TextShadow.STRONG -> Shadow(colour.copy(alpha = 0.9f), Offset(0f, 1.5f), 3f)
    }

    private fun font(choice: LauncherFont): FontFamily = when (choice) {
        LauncherFont.SYSTEM -> FontFamily.Default
        LauncherFont.CONDENSED -> FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)))
        LauncherFont.SERIF -> FontFamily.Serif
        LauncherFont.MONO -> FontFamily.Monospace
    }
}
