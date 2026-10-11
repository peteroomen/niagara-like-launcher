package io.github.peteroomen.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.peteroomen.launcher.data.IconCache
import kotlin.math.max

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
    /** Tint over the blurred wallpaper inside glass, for text contrast */
    val glassTint: Color,
    /** Heavier tint used when the wallpaper can't be seen, so text stays readable */
    val glassTintNoBlur: Color,
) {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontSize = size, fontWeight = weight, fontFamily = font, letterSpacing = letterSpacing, shadow = shadow)
}

val LocalPalette = staticCompositionLocalOf<Palette> { error("No palette") }

/** The blurred wallpaper and the screen it's drawn across, for glass panels to show through. */
class GlassBackdrop(val image: ImageBitmap, val screen: Size)

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

private val Vibrancy = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })

/**
 * Liquid glass, after Apple's: the wallpaper behind the panel shows through blurred and a little
 * more vivid, under a light tint for contrast. The lighting is quiet: a hairline rim that catches
 * light at the top-left and bottom-right corners, and a faint glow along the top edge.
 */
@Composable
fun Modifier.glass(shape: Shape, palette: Palette, rim: Dp = 1.dp): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val position = remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position.value = it.positionOnScreen() }
        .clip(shape)
        .drawBehind {
            if (backdrop != null) {
                // Lay the blurred wallpaper out exactly as the system does (centre-cropped to the
                // screen), then shift it so this panel shows the part that's behind it
                val image = backdrop.image
                val scale = max(backdrop.screen.width / image.width, backdrop.screen.height / image.height)
                val left = (backdrop.screen.width - image.width * scale) / 2 - position.value.x
                val top = (backdrop.screen.height - image.height * scale) / 2 - position.value.y
                translate(left, top) {
                    scale(scale, pivot = Offset.Zero) {
                        drawImage(image, colorFilter = Vibrancy, filterQuality = FilterQuality.High)
                    }
                }
                drawRect(palette.glassTint)
            } else {
                drawRect(palette.glassTintNoBlur)
            }
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.35f to Color.Transparent))
        }
        .border(
            BorderStroke(
                rim,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.3f to Color.White.copy(alpha = 0.12f),
                    0.7f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.32f),
                ),
            ),
            shape,
        )
}

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
