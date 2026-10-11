package io.github.peteroomen.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.peteroomen.launcher.data.IconCache

/** How everything drawn straight onto the wallpaper looks: colours from the wallpaper plus the appearance settings. */
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
    /** Music widget card */
    val card: Color,
    /** Colour of the readability backdrop (fade or dim) */
    val backdrop: Color,
    val shadow: Shadow?,
    val font: FontFamily,
    val iconShape: Shape,
    /** Liquid glass panels instead of flat translucent ones */
    val glass: Boolean,
    val glassTop: Color,
    val glassBottom: Color,
    val glassSheen: Float,
) {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontSize = size, fontWeight = weight, fontFamily = font, letterSpacing = letterSpacing, shadow = shadow)
}

val LocalPalette = staticCompositionLocalOf<Palette> { error("No palette") }

/**
 * Liquid glass: a see-through fill that's brighter at the top, a soft sheen from the top-left
 * corner, and a bright rim that fades as it goes round, like light catching the edge of glass.
 */
fun Modifier.glass(shape: Shape, palette: Palette, rim: Dp = 1.dp): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(palette.glassTop, palette.glassBottom)))
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = palette.glassSheen), Color.Transparent),
                center = Offset(size.width * 0.2f, 0f),
                radius = size.maxDimension * 0.9f,
            ),
        )
    }
    .border(
        BorderStroke(
            rim,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.32f)),
            ),
        ),
        shape,
    )

/** A panel behind something: liquid glass when that's switched on, otherwise a flat [fill]. */
@Composable
fun Modifier.panel(shape: Shape, fill: Color, rim: Dp = 1.dp): Modifier {
    val palette = LocalPalette.current
    return if (palette.glass) glass(shape, palette, rim) else clip(shape).background(fill)
}

interface IconLoader {
    fun cached(key: String, sizePx: Int): IconCache.Icons?
    suspend fun load(key: String, sizePx: Int): IconCache.Icons?
}

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("No icon loader") }
