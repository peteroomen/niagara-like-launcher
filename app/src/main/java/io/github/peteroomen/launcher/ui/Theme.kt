package io.github.peteroomen.launcher.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
    /** Bezel glass: slightly blurred, a darker middle framed by a lighter band inside the rim */
    val glassBezel: Boolean = false,
    /** A Loud combo accent is on: punchier, sticker-slap motion where the launcher animates. */
    val loud: Boolean = false,
) {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontSize = size, fontWeight = weight, fontFamily = font, letterSpacing = letterSpacing, shadow = shadow)
}

val LocalPalette = staticCompositionLocalOf<Palette> { error("No palette") }

/**
 * The wallpaper copies and the screen they're drawn across, for glass panels to show through:
 * [thick] is the heavily blurred one sheets use.
 */
class GlassBackdrop(val frosted: ImageBitmap, val sharp: ImageBitmap, val thick: ImageBitmap, val screen: Size)

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

private val Vibrancy = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })
/** Thick glass is more vivid still: the heavy blur greys colours out, and Apple's materials push them back */
private val ThickVibrancy = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.8f) })

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
 *
 * Big sheets (search, the A–Z list, hold menus) are always thick frosted glass, after Apple's
 * thick material: the wallpaper heavily blurred, so nothing behind it competes with the text,
 * more vivid, under a heavier tint. Like iOS, clear glass is for small things over the
 * wallpaper, and a sheet full of text needs to read as a surface. Without the wallpaper to blur,
 * a sheet gets a near-solid tint.
 *
 * The tint adapts to the wallpaper behind each panel: it's measured there, and the tint is made
 * just strong enough for the text colour to read on its brightest (or darkest) parts.
 *
 * A panel holding only [largeText] (the clock) is clearer: big text reads with less contrast, so
 * clear glass keeps its light tint and only eases down the brightest parts behind it (or lifts
 * the darkest, under dark text) instead of tinting the whole panel for them.
 *
 * A panel holding only [icons] (a folder) keeps the same tint wherever it is: icons read on any
 * glass, and folders side by side would otherwise look different, darker over brighter wallpaper.
 */
@Composable
fun Modifier.glass(shape: Shape, palette: Palette, sheet: Boolean = false, bendScale: Float = 1f, largeText: Boolean = false, icons: Boolean = false): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val position = remember { mutableStateOf(Offset.Zero) }
    // What's behind: the darkest and brightest of it (10th and 90th percentile luminance)
    val behind = remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val sharp = backdrop?.sharp
    val lens = remember(sharp, palette.glassClear, palette.glassBezel, sheet) {
        // The shader compiles on the phone; if that ever fails, fall back to frosted glass.
        // Clear glass isn't used for sheets; bezel glass is, with its facet only along the top.
        val wanted = (palette.glassClear && !sheet) || palette.glassBezel
        if (wanted && sharp != null && Build.VERSION.SDK_INT >= 33) runCatching { GlassEdge(sharp) }.getOrNull() else null
    }
    return this
        .onGloballyPositioned {
            position.value = it.positionOnScreen()
            if (backdrop != null && !icons) behind.value = luminanceBehind(backdrop, position.value, it.size)
        }
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
                if (!palette.glassBezel && lens != null && Build.VERSION.SDK_INT >= 33) {
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
                        // Big text reads at about 3:1: light text over at most 0.3 luminance, dark over at least 0.25
                        ceiling = if (largeText && !palette.darkText) 0.3f else 1f,
                        floor = if (largeText && palette.darkText) 0.25f else 0f,
                    )
                    if (shader != null) {
                        drawRect(ShaderBrush(shader))
                        // A light tint keeps text and icons readable over clear glass
                        drawRect(if (largeText) palette.glassTint else adaptiveTint(palette, palette.glassTint, behind.value))
                        clearDrawn = true
                    }
                }
                val bezelShader = if (palette.glassBezel && lens != null && Build.VERSION.SDK_INT >= 33) {
                    val away = if (sheet) 40.dp.toPx() else 0f
                    lens.bezelShaderFor(
                        size = size,
                        radius = corner,
                        facet = BezelFacet.toPx(),
                        shift = BezelShift.toPx(),
                        panelOrigin = position.value,
                        imageOrigin = sharpOrigin,
                        imageScale = sharpScale,
                        blur = 2.dp.toPx(),
                        extendLeft = away,
                        extendRight = away,
                        extendBottom = away,
                    )
                } else {
                    null
                }
                if (bezelShader != null) {
                    // Bezel, cut: a flat facet round the rim and a softly blurred face
                    drawRect(ShaderBrush(bezelShader))
                    drawRect(adaptiveTint(palette, bezelMiddle(palette), behind.value))
                } else if (!clearDrawn && palette.glassBezel) {
                    // Bezel without the shader: only slightly blurred (the sharp copy, half covered by the frosted one)
                    translate(sharpOrigin.x - position.value.x, sharpOrigin.y - position.value.y) {
                        scale(sharpScale, pivot = Offset.Zero) {
                            drawImage(sharpImage, filterQuality = FilterQuality.High)
                        }
                    }
                    val image = backdrop.frosted
                    val scale = max(backdrop.screen.width / image.width, backdrop.screen.height / image.height)
                    translate((backdrop.screen.width - image.width * scale) / 2 - position.value.x, (backdrop.screen.height - image.height * scale) / 2 - position.value.y) {
                        scale(scale, pivot = Offset.Zero) {
                            drawImage(image, alpha = 0.5f, filterQuality = FilterQuality.High)
                        }
                    }
                    drawRect(adaptiveTint(palette, bezelMiddle(palette), behind.value))
                } else if (!clearDrawn) {
                    // Frosted: the blurred copy (heavily blurred for a sheet), shifted so this panel shows the part behind it
                    val image = if (sheet) backdrop.thick else backdrop.frosted
                    val scale = max(backdrop.screen.width / image.width, backdrop.screen.height / image.height)
                    val left = (backdrop.screen.width - image.width * scale) / 2 - position.value.x
                    val top = (backdrop.screen.height - image.height * scale) / 2 - position.value.y
                    translate(left, top) {
                        scale(scale, pivot = Offset.Zero) {
                            drawImage(image, colorFilter = if (sheet) ThickVibrancy else Vibrancy, filterQuality = FilterQuality.High)
                        }
                    }
                    val frostedTint = if (sheet) palette.glassFrostedTint.copy(alpha = palette.glassFrostedTint.alpha * 2.2f) else palette.glassFrostedTint
                    drawRect(adaptiveTint(palette, frostedTint, behind.value))
                }
            } else {
                drawRect(if (sheet) palette.glassTintNoBlur.copy(alpha = sheetTintNoBlur(palette)) else palette.glassTintNoBlur)
            }
            if (palette.glassBezel && !clearDrawn) {
                if (sheet) {
                    // A sheet's sides and bottom sit against the screen: only its top edge is cut
                    val away = 40.dp.toPx()
                    val wide = shape.createOutline(Size(size.width + away * 2, size.height + away), layoutDirection, this)
                    translate(-away, 0f) { bezelEdges(wide, palette) }
                } else {
                    bezelEdges(outline, palette)
                }
            }
            if (palette.glassBezel && !clearDrawn) {
                // Bezel glass is evenly lit: an even rim, no top glow
                bezelRim(outline)
            } else if (!clearDrawn) {
                // Clear glass lights its own rim; frosted gets a faint top glow and a hairline rim
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.2f), 1f to Color.Transparent, endY = GlowDepth.toPx()))
                drawOutline(outline, RimBrush, style = Stroke(width = RimWidth.toPx() * 2)) // half of it falls outside the clip
            }
        }
}

/**
 * Glass that's selected (an open folder, the pie's icon under the finger): it takes on the
 * [accent] and catches more light, as if lifted towards you. Everything is a smooth gradient, no
 * bands: the accent, strongest at the top-left and easing off across the shape (behind the
 * content), a soft sheen of light falling from the top-left (over it), and a thin lit rim. It
 * reads on any wallpaper, where an accent outline could match the glass's own tint and vanish.
 * Cut to [shape].
 */
fun Modifier.litRim(shape: Shape, accent: Color): Modifier = clip(shape).drawWithContent {
    val outline = shape.createOutline(size, layoutDirection, this)
    val corner = Offset(size.width, size.height)
    drawOutline(outline, Brush.linearGradient(0f to accent.copy(alpha = 0.45f), 1f to accent.copy(alpha = 0.12f), start = Offset.Zero, end = corner))
    drawContent()
    drawOutline(
        outline,
        Brush.radialGradient(0f to Color.White.copy(alpha = 0.24f), 1f to Color.Transparent, center = Offset.Zero, radius = size.maxDimension),
    )
    // The rim: brightest where it faces the light (half of it falls outside the clip)
    drawOutline(outline, Brush.linearGradient(*LitRimStops, start = Offset.Zero, end = corner), style = Stroke(width = 2.5.dp.toPx()))
}

private val LitRimStops = arrayOf(
    0f to Color.White.copy(alpha = 0.95f),
    0.5f to Color.White.copy(alpha = 0.4f),
    1f to Color.White.copy(alpha = 0.7f),
)

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
 * [lens] scales how strongly clear glass bends at its rim (1 = the music card). [largeText]: it
 * holds only big text, so clear glass can stay clearer; [icons], only icons, so its tint doesn't
 * vary with the wallpaper (see [glass]).
 */
@Composable
fun Modifier.panel(shape: Shape, fill: Color, sheet: Boolean = false, lens: Float = 1f, largeText: Boolean = false, icons: Boolean = false): Modifier {
    val palette = LocalPalette.current
    return when {
        palette.loud -> loudPanel(shape, fill, palette.text, sheet)
        palette.glass && LocalOnGlass.current -> clip(shape).background(fill)
        palette.glass -> glass(shape, palette, sheet, lens, largeText, icons)
        else -> clip(shape).background(fill)
    }
}

/**
 * Something that sits on glass and needs to stand out from it, like the search box on the search
 * sheet: no glass on glass, but a light translucent fill with a hairline edge.
 */
@Composable
fun Modifier.onGlassField(shape: Shape): Modifier {
    val palette = LocalPalette.current
    return this
        .background(if (palette.darkText) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f), shape)
        .border(1.dp, if (palette.darkText) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f), shape)
}

/**
 * A sheet's tint when the wallpaper can't be seen: nearly solid, since whatever is behind it (the
 * real wallpaper, unblurred, and the home screen) would otherwise show straight through its text.
 */
private fun sheetTintNoBlur(palette: Palette) = if (palette.darkText) 0.86f else 0.78f

/** Bezel glass's darker middle: dark under light text; under dark text it has to stay light. */
private fun bezelMiddle(palette: Palette) =
    if (palette.darkText) Color.White.copy(alpha = 0.53f) else Color.Black.copy(alpha = 0.55f)

/** How wide the cut facet round bezel glass is, and how far it shifts what's seen through it. */
private val BezelFacet = 5.dp
private val BezelShift = 3.dp

/**
 * Bezel glass's cut edge, in even, ambient light: a lighter band over the facet all round, a
 * faint bright ridge where it meets the face, and a soft shadow on the face just inside that,
 * the thickness of the glass. No light
 * direction: the facet's shifted, richer view of the wallpaper (the shader) does the rest.
 */
private fun DrawScope.bezelEdges(outline: Outline, palette: Palette) {
    val facet = BezelFacet.toPx()
    // The facet: centred on the edge, so only the inner half shows
    drawOutline(outline, Color.White.copy(alpha = if (palette.darkText) 0.4f else 0.16f), style = Stroke(width = facet * 2))
    val face = faceOutline(outline, facet) ?: return
    val hairline = 1.dp.toPx()
    // The ridge: a faint bright hairline
    drawOutline(face, Color.White.copy(alpha = if (palette.darkText) 0.17f else 0.12f), style = Stroke(width = hairline))
    // The thickness: a soft shadow on the face just inside the ridge
    clipPath(Path().apply { addOutline(face) }) {
        drawOutline(face, Color.Black.copy(alpha = 0.18f), style = Stroke(width = 8.dp.toPx()))
        drawOutline(face, Color.Black.copy(alpha = 0.11f), style = Stroke(width = 16.dp.toPx()))
    }
}

/** Bezel glass's outer rim: the same all round, in keeping with its even light. */
private fun DrawScope.bezelRim(outline: Outline) {
    drawOutline(outline, Color.White.copy(alpha = 0.15f), style = Stroke(width = RimWidth.toPx() * 2))
}

/** The face inside a facet [inset] wide: the panel's outline, shrunk by that much. */
private fun faceOutline(outline: Outline, inset: Float): Outline? = when (outline) {
    is Outline.Rectangle -> outline.rect.deflate(inset).takeIf { it.width > 0 && it.height > 0 }?.let { Outline.Rectangle(it) }
    is Outline.Rounded -> {
        val r = outline.roundRect
        val rect = Rect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
        if (rect.width <= 0 || rect.height <= 0) {
            null
        } else {
            fun shrink(c: CornerRadius) = CornerRadius((c.x - inset).coerceAtLeast(0f), (c.y - inset).coerceAtLeast(0f))
            Outline.Rounded(RoundRect(rect, shrink(r.topLeftCornerRadius), shrink(r.topRightCornerRadius), shrink(r.bottomRightCornerRadius), shrink(r.bottomLeftCornerRadius)))
        }
    }
    is Outline.Generic -> null
}

/** Luminance below which, and above which, a tenth of the wallpaper behind this rectangle lies. */
private fun luminanceBehind(backdrop: GlassBackdrop, position: Offset, size: IntSize): Pair<Float, Float>? {
    if (size.width <= 0 || size.height <= 0) return null
    val bitmap = backdrop.frosted.asAndroidBitmap()
    val scale = max(backdrop.screen.width / bitmap.width, backdrop.screen.height / bitmap.height)
    val left = (backdrop.screen.width - bitmap.width * scale) / 2
    val top = (backdrop.screen.height - bitmap.height * scale) / 2
    val samples = ArrayList<Float>(SampleGrid * SampleGrid)
    for (i in 0 until SampleGrid) for (j in 0 until SampleGrid) {
        val x = ((position.x + size.width * (i + 0.5f) / SampleGrid - left) / scale).toInt().coerceIn(0, bitmap.width - 1)
        val y = ((position.y + size.height * (j + 0.5f) / SampleGrid - top) / scale).toInt().coerceIn(0, bitmap.height - 1)
        samples += Color(bitmap.getPixel(x, y)).luminance()
    }
    samples.sort()
    return samples[samples.size / 10] to samples[samples.size * 9 / 10]
}

private const val SampleGrid = 12

/**
 * [base], made just strong enough for the text to read on what's [behind]: light text needs the
 * brightest parts darkened below about 0.18 luminance, dark text the darkest lifted above 0.4.
 */
private fun adaptiveTint(palette: Palette, base: Color, behind: Pair<Float, Float>?): Color {
    if (behind == null) return base
    val (darkest, brightest) = behind
    val needed = if (palette.darkText) {
        if (darkest >= 0.4f) 0f else (0.4f - darkest) / (1f - darkest)
    } else {
        if (brightest <= 0.18f) 0f else 1f - 0.18f / brightest
    }
    return base.copy(alpha = needed.coerceIn(base.alpha, if (palette.darkText) 0.85f else 0.75f))
}

/**
 * Draws [image], centre-cropped to fill this element, as if it lay under the glass of the panel it
 * sits in: this element must sit at the panel's top-left, and [panelSize] and [panelCorner] are
 * the panel's. Clear glass bends it at the panel's rim with the same lens and lit rim as the
 * wallpaper, under a lighter tint; frosted glass gives it the tint, top glow and hairline rim.
 */
@Composable
fun Modifier.underGlass(image: ImageBitmap, panelSize: IntSize, panelCorner: Dp): Modifier {
    val palette = LocalPalette.current
    val lens = remember(image, palette.glassClear, palette.glassBezel) {
        if ((palette.glassClear || palette.glassBezel) && Build.VERSION.SDK_INT >= 33) runCatching { GlassEdge(image) }.getOrNull() else null
    }
    return drawBehind {
        val scale = max(size.width / image.width, size.height / image.height)
        val origin = Offset((size.width - image.width * scale) / 2, (size.height - image.height * scale) / 2)
        val panel = if (panelSize.width > 0 && panelSize.height > 0) panelSize.toSize() else size
        val shortest = panel.minDimension
        clipRect {
            val outline = RoundedCornerShape(panelCorner).createOutline(panel, layoutDirection, this)
            val shader = if (palette.glassBezel && lens != null && Build.VERSION.SDK_INT >= 33) {
                lens.bezelShaderFor(
                    size = panel,
                    radius = panelCorner.toPx(),
                    facet = BezelFacet.toPx(),
                    shift = BezelShift.toPx(),
                    panelOrigin = Offset.Zero,
                    imageOrigin = origin,
                    imageScale = scale,
                    blur = 0f,
                )
            } else if (lens != null && Build.VERSION.SDK_INT >= 33) {
                lens.clearShaderFor(
                    size = panel,
                    radius = panelCorner.toPx(),
                    bevel = minOf(shortest * 0.25f, 16.dp.toPx()),
                    strength = minOf(shortest * 0.16f, 9.dp.toPx()),
                    zoom = 1f,
                    panelOrigin = Offset.Zero,
                    imageOrigin = origin,
                    imageScale = scale,
                )
            } else {
                null
            }
            if (palette.glassBezel) {
                // Under the bezel's cut edge, which frames the art like the rest of the card
                if (shader != null) {
                    drawRect(ShaderBrush(shader))
                } else {
                    translate(origin.x, origin.y) {
                        scale(scale, pivot = Offset.Zero) { drawImage(image, filterQuality = FilterQuality.High) }
                    }
                }
                bezelEdges(outline, palette)
                bezelRim(outline)
            } else if (shader != null) {
                drawRect(ShaderBrush(shader))
                drawRect(palette.glassTint.copy(alpha = palette.glassTint.alpha * 0.5f))
            } else {
                translate(origin.x, origin.y) {
                    scale(scale, pivot = Offset.Zero) { drawImage(image, filterQuality = FilterQuality.High) }
                }
                drawRect(palette.glassFrostedTint.copy(alpha = palette.glassFrostedTint.alpha * 0.5f))
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.2f), 1f to Color.Transparent, endY = GlowDepth.toPx()))
                drawOutline(outline, RimBrush, style = Stroke(width = RimWidth.toPx() * 2))
            }
        }
    }
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
