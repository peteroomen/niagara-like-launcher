package io.github.peteroomen.launcher.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
    /** Fonts for app names and for the A–Z list's letter headings (the main font unless chosen). */
    val appFont: FontFamily = font,
    val letterFont: FontFamily = font,
    val iconShape: Shape,
    /** Liquid glass panels instead of flat translucent ones */
    val glass: Boolean,
    /** Tint over big clear glass panels, for text contrast */
    val glassTint: Color,
    /** Heavier tint used when the wallpaper can't be seen, so text stays readable */
    val glassTintNoBlur: Color,
    /** Tint over frosted glass */
    val glassFrostedTint: Color,
    /** Clear glass: lightly blurred, the whole panel acts as a lens (Android 13+) */
    val glassClear: Boolean,
    /** A Loud combo accent is on: punchier, sticker-slap motion where the launcher animates. */
    val loud: Boolean = false,
) {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontSize = size, fontWeight = weight, fontFamily = font, letterSpacing = letterSpacing, shadow = shadow)
}

val LocalPalette = staticCompositionLocalOf<Palette> { error("No palette") }

/** The wallpaper copies and the screen they're drawn across, for glass panels to show through. */
class GlassBackdrop(val frosted: ImageBitmap, val sharp: ImageBitmap, val screen: Size)

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

private val Vibrancy = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/** Every glass panel's rim, and the depth of the frosted top glow, the same whatever the panel's size. */
private val RimWidth = 1.dp
private val GlowDepth = 24.dp

private val RimBrush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.7f),
    0.3f to Color.White.copy(alpha = 0.16f),
    0.7f to Color.White.copy(alpha = 0.08f),
    1f to Color.White.copy(alpha = 0.45f),
)

/**
 * Liquid glass, after Apple's. In the clear style every panel is a lens: the wallpaper behind is
 * barely blurred and bent inwards at the rim with faint colour fringes, under a lit rim (Android
 * 13+), under a light tint so text and icons stay readable. Otherwise panels are frosted: the
 * wallpaper blurred and more vivid under a light tint, with a faint glow at the top and a hairline
 * rim. Every panel gets the same recipe, whatever its size.
 */
@Composable
fun Modifier.glass(shape: Shape, palette: Palette, sheet: Boolean = false, bendScale: Float = 1f): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val position = remember { mutableStateOf(Offset.Zero) }
    val sharp = backdrop?.sharp
    val lens = remember(sharp, palette.glassClear) {
        // The shader compiles on the phone; if that ever fails, fall back to frosted glass
        if (sharp != null && palette.glassClear && Build.VERSION.SDK_INT >= 33) runCatching { GlassEdge(sharp) }.getOrNull() else null
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
                        bevel = minOf(shortest * 0.25f, 16.dp.toPx()) * bendScale.coerceAtLeast(0.3f),
                        strength = minOf(shortest * 0.16f, 9.dp.toPx()) * bendScale,
                        // No magnifying: on a big panel even a little moves things a long way
                        zoom = 1f,
                        panelOrigin = position.value,
                        imageOrigin = sharpOrigin,
                        imageScale = sharpScale,
                        extendLeft = away,
                        extendRight = away,
                        extendBottom = away,
                    )
                    if (shader != null) {
                        drawRect(ShaderBrush(shader))
                        // A light tint keeps text and icons readable over clear glass
                        drawRect(palette.glassTint)
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
                    drawRect(palette.glassFrostedTint)
                }
            } else {
                drawRect(palette.glassTintNoBlur)
            }
            if (!clearDrawn) {
                // Clear glass lights its own rim; frosted gets a faint top glow and a hairline rim
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.2f), 1f to Color.Transparent, endY = GlowDepth.toPx()))
                drawOutline(outline, RimBrush, style = Stroke(width = RimWidth.toPx() * 2)) // half of it falls outside the clip
            }
        }
}

/**
 * True inside something that's already glass (a folder, the folder row, the pie, a sheet). Like
 * iOS, glass isn't stacked on glass: panels in there are flat fills instead.
 */
val LocalOnGlass = staticCompositionLocalOf { false }

/** [content] sits on glass, if glass is on: its own panels drop to flat fills. */
@Composable
fun OnGlass(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOnGlass provides LocalPalette.current.glass, content = content)
}

/**
 * A panel behind something: liquid glass when that's switched on, otherwise a flat [fill]. A
 * [sheet] sits against the sides and bottom of the screen, so only its top edge is a glass edge.
 * [lens] scales how strongly clear glass bends at its rim (1 = the music card). [rimOver] draws
 * the lit rim and an inner glow over the content too, for content that runs to the edge (like
 * the music card's album art), so the edges still read as glass.
 */
@Composable
fun Modifier.panel(shape: Shape, fill: Color, sheet: Boolean = false, lens: Float = 1f, rimOver: Boolean = false): Modifier {
    val palette = LocalPalette.current
    return when {
        palette.loud -> loudPanel(shape, fill, palette.text, sheet)
        palette.glass && LocalOnGlass.current -> clip(shape).background(fill)
        palette.glass -> glass(shape, palette, sheet, lens).then(if (rimOver) Modifier.rimOver(shape) else Modifier)
        else -> clip(shape).background(fill)
    }
}

/** The glass rim and a soft inner glow, drawn over the content (inside the panel's clip). */
private fun Modifier.rimOver(shape: Shape): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    // Half of each stroke falls outside the clip: a wide faint glow inside, then the lit rim
    drawOutline(outline, Color.White.copy(alpha = 0.1f), style = Stroke(width = 12.dp.toPx()))
    drawOutline(outline, Color.White.copy(alpha = 0.06f), style = Stroke(width = 24.dp.toPx()))
    drawOutline(outline, RimBrush, style = Stroke(width = RimWidth.toPx() * 3))
}

private val LoudStroke = 2.5.dp
private val LoudShadow = 4.dp

/**
 * Loud's neubrutal panel: a solid fill, an outline and a hard offset shadow in the text colour (ink
 * on bright panels, pale on deep ones, like Loud's Night Brutal). A sheet only shadows upwards.
 */
fun Modifier.loudPanel(shape: Shape, fill: Color, line: Color, sheet: Boolean = false): Modifier =
    drawBehind {
        val offset = LoudShadow.toPx()
        translate(if (sheet) 0f else offset, if (sheet) -offset else offset) {
            drawOutline(shape.createOutline(size, layoutDirection, this), line)
        }
    }
        .clip(shape)
        .background(fill.copy(alpha = 1f))
        .border(LoudStroke, line, shape)

interface IconLoader {
    fun cached(key: String, sizePx: Int): IconCache.Icons?
    suspend fun load(key: String, sizePx: Int): IconCache.Icons?
}

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("No icon loader") }
