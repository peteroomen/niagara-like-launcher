package io.github.peteroomen.launcher.ui

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
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
    /** Bend the wallpaper at glass edges like a lens (Android 13+) */
    val glassBend: Boolean,
    /** Clear glass: lightly blurred, the whole panel acts as a lens (Android 13+) */
    val glassClear: Boolean,
) {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontSize = size, fontWeight = weight, fontFamily = font, letterSpacing = letterSpacing, shadow = shadow)
}

val LocalPalette = staticCompositionLocalOf<Palette> { error("No palette") }

/** The wallpaper copies and the screen they're drawn across, for glass panels to show through. */
class GlassBackdrop(val frosted: ImageBitmap, val sharp: ImageBitmap, val screen: Size)

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

private val Vibrancy = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })

/** Panels at least this small (in their shortest side) are buttons and tiles; bigger ones are sheets. */
private val SheetSize = 200.dp

private val RimBrush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.55f),
    0.3f to Color.White.copy(alpha = 0.12f),
    0.7f to Color.White.copy(alpha = 0.06f),
    1f to Color.White.copy(alpha = 0.32f),
)

/**
 * Liquid glass, after Apple's. In the clear style every panel is a lens: the wallpaper behind is
 * barely blurred and bent inwards at the rim with faint colour fringes, under a lit rim (Android
 * 13+). Small panels also magnify a little; big ones (the search and app list sheet, the pie) keep
 * a light tint so their text stays readable. Otherwise panels are frosted: the wallpaper blurred
 * and a little more vivid under a light tint, and small frosted panels can bend at their edges.
 */
@Composable
fun Modifier.glass(shape: Shape, palette: Palette, rim: Dp = 1.dp, sheet: Boolean = false): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val position = remember { mutableStateOf(Offset.Zero) }
    val sharp = backdrop?.sharp
    val lens = remember(sharp, palette.glassBend, palette.glassClear) {
        // The shaders compile on the phone; if that ever fails, fall back to plain frosted glass
        if (sharp != null && (palette.glassBend || palette.glassClear) && Build.VERSION.SDK_INT >= 33) runCatching { GlassEdge(sharp) }.getOrNull() else null
    }
    return this
        .onGloballyPositioned { position.value = it.positionOnScreen() }
        .clip(shape)
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val corner = when (outline) {
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                is Outline.Rectangle -> 0f
                is Outline.Generic -> size.minDimension / 2
            }
            val small = size.minDimension < SheetSize.toPx()
            var clearDrawn = false
            if (backdrop != null) {
                // The sharp copy, laid out centre-cropped to the screen like the system does
                val sharpImage = backdrop.sharp
                val sharpScale = max(backdrop.screen.width / sharpImage.width, backdrop.screen.height / sharpImage.height)
                val sharpOrigin = Offset(
                    (backdrop.screen.width - sharpImage.width * sharpScale) / 2,
                    (backdrop.screen.height - sharpImage.height * sharpScale) / 2,
                )
                if (palette.glassClear && lens != null && Build.VERSION.SDK_INT >= 33) {
                    val shortest = size.minDimension
                    // A sheet's sides and bottom sit against the screen: push them out so only its top bends
                    val away = if (sheet) 40.dp.toPx() else 0f
                    val shader = lens.clearShaderFor(
                        size = size,
                        radius = corner,
                        // A thin bevel at the rim, so the middle shows the wallpaper as it is.
                        // (A deep bevel stretches everything behind a short panel like the music card.)
                        bevel = minOf(shortest * 0.25f, 12.dp.toPx()),
                        strength = minOf(shortest * 0.12f, 6.dp.toPx()),
                        // Big panels don't magnify: even a little moves things a long way across them
                        zoom = if (small) 1.03f else 1f,
                        panelOrigin = position.value,
                        imageOrigin = sharpOrigin,
                        imageScale = sharpScale,
                        extendLeft = away,
                        extendRight = away,
                        extendBottom = away,
                    )
                    if (shader != null) {
                        drawRect(ShaderBrush(shader))
                        // Big panels hold text: a light tint keeps it readable over clear glass
                        if (!small) drawRect(palette.glassTint)
                        clearDrawn = true
                    }
                }
                if (!clearDrawn) {
                    // Frosted: the blurred copy, shifted so this panel shows the part behind it
                    val image = backdrop.frosted
                    val scale = max(backdrop.screen.width / image.width, backdrop.screen.height / image.height)
                    val left = (backdrop.screen.width - image.width * scale) / 2 - position.value.x
                    val top = (backdrop.screen.height - image.height * scale) / 2 - position.value.y
                    translate(left, top) {
                        scale(scale, pivot = Offset.Zero) {
                            drawImage(image, colorFilter = Vibrancy, filterQuality = FilterQuality.High)
                        }
                    }
                    drawRect(palette.glassTint)
                    if (palette.glassBend && small && lens != null && Build.VERSION.SDK_INT >= 33) {
                        val shortest = size.minDimension
                        val fade = minOf(16.dp.toPx(), shortest / 3.5f)
                        val strength = minOf(10.dp.toPx(), shortest / 5f)
                        drawRect(ShaderBrush(lens.shaderFor(size, corner, fade, strength, position.value, sharpOrigin, sharpScale)))
                    }
                }
            } else {
                drawRect(palette.glassTintNoBlur)
            }
            if (!clearDrawn) {
                // Clear glass lights its own rim; frosted gets a faint top glow and a hairline rim
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.35f to Color.Transparent))
                drawOutline(outline, RimBrush, style = Stroke(width = rim.toPx() * 2)) // half of it falls outside the clip
            }
        }
}

/**
 * A panel behind something: liquid glass when that's switched on, otherwise a flat [fill]. A
 * [sheet] sits against the sides and bottom of the screen, so only its top edge is a glass edge.
 */
@Composable
fun Modifier.panel(shape: Shape, fill: Color, rim: Dp = 1.dp, sheet: Boolean = false): Modifier {
    val palette = LocalPalette.current
    return if (palette.glass) glass(shape, palette, rim, sheet) else clip(shape).background(fill)
}

interface IconLoader {
    fun cached(key: String, sizePx: Int): IconCache.Icons?
    suspend fun load(key: String, sizePx: Int): IconCache.Icons?
}

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("No icon loader") }
