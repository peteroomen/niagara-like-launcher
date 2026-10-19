package io.github.peteroomen.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import android.os.Build
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.max
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.Appearance
import io.github.peteroomen.launcher.data.IconCache
import io.github.peteroomen.launcher.data.IconStyle
import io.github.peteroomen.launcher.data.appNameWeight
import io.github.peteroomen.launcher.model.Fav

private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
private fun rememberIcons(key: String, sizePx: Int): IconCache.Icons? {
    val loader = LocalIconLoader.current
    // Keyed on the app, so a row that switches to another app (as search results do while typing)
    // never keeps showing the previous app's icon
    val icons = remember(key, sizePx, loader) { mutableStateOf(loader.cached(key, sizePx)) }
    LaunchedEffect(icons) {
        if (icons.value == null) icons.value = loader.load(key, sizePx)
    }
    return icons.value
}

/**
 * One app icon, cut to the chosen shape. Themed style draws the app's monochrome layer on a tile
 * tinted from the wallpaper, like Android 13 themed icons; apps without one keep their normal icon.
 */
@Composable
fun AppIcon(key: String, style: IconStyle, size: Dp, badge: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = palette.iconShape
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icons = rememberIcons(key, sizePx)
    Box(modifier.size(size)) {
        when {
            icons == null -> Box(Modifier.fillMaxSize().panel(shape, palette.tileBg))
            // Loud has no glass: its icons stay solid colour
            style == IconStyle.GLASS && !palette.loud && icons.fullBleed -> GlassIcon(icons, size)
            style == IconStyle.THEMED && icons.mono != null -> Box(Modifier.fillMaxSize().panel(shape, palette.tileBg)) {
                Image(icons.mono, contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(palette.tileFg))
            }
            icons.fullBleed -> Image(
                icons.full,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(shape),
                colorFilter = if (style == IconStyle.GREYSCALE) Greyscale else null,
            )
            // An old-style icon with its own outline: shrunk onto a plate of the icon shape, as
            // Android's own launcher does, so every icon has the same outline
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(if (style == IconStyle.THEMED) palette.tileBg else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    icons.full,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.72f),
                    colorFilter = if (style == IconStyle.GREYSCALE) Greyscale else null,
                )
            }
        }
        if (badge) NotificationDot(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp))
    }
}

/**
 * A glass icon: the app's own colour icon, untouched in the middle, with only its edges lensed, as
 * the music card's album art is in clear glass: pulled inwards at the rim with faint colour
 * fringes, under a thin lit rim. No tint or frosting over it. Cut to the theme's icon shape.
 * Without the lens (before Android 13, or if it can't compile) it's the icon with a hairline rim.
 */
private const val GlassIconAlpha = 0.8f

@Composable
private fun GlassIcon(icons: IconCache.Icons, size: Dp) {
    val shape = LocalPalette.current.iconShape
    val lens = remember(icons) { if (Build.VERSION.SDK_INT >= 33) runCatching { GlassEdge(icons.full) }.getOrNull() else null }
    Box(
        Modifier
            .fillMaxSize()
            // A little see-through, so the wallpaper shows faintly through the glass
            .graphicsLayer { alpha = GlassIconAlpha }
            .clip(shape)
            .drawBehind {
                val outline = shape.createOutline(this.size, layoutDirection, this)
                val corner = when (outline) {
                    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                    is Outline.Rectangle -> 0f
                    is Outline.Generic -> this.size.minDimension / 2
                }
                val image = icons.full
                val scale = max(this.size.width / image.width, this.size.height / image.height)
                val shortest = this.size.minDimension
                val shader = if (lens != null && Build.VERSION.SDK_INT >= 33) {
                    lens.clearShaderFor(
                        size = this.size,
                        radius = corner,
                        // A thinner bevel than the music card's, hugging the rim, so more of the icon is as it is
                        bevel = minOf(shortest * 0.14f, 10.dp.toPx()),
                        strength = minOf(shortest * 0.1f, 6.dp.toPx()),
                        zoom = 1f,
                        panelOrigin = Offset.Zero,
                        imageOrigin = Offset.Zero,
                        imageScale = scale,
                    )
                } else {
                    null
                }
                if (shader != null) {
                    drawRect(ShaderBrush(shader))
                } else {
                    scale(scale, pivot = Offset.Zero) { drawImage(image, filterQuality = FilterQuality.High) }
                    drawOutline(outline, Color.White.copy(alpha = 0.45f), style = Stroke(width = 2.dp.toPx())) // half falls outside the clip
                }
            },
    )
}

@Composable
fun NotificationDot(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier
            .size(12.dp)
            .background(palette.ring, CircleShape)
            .padding(2.dp)
            .background(palette.accent, CircleShape),
    )
}

/** Press feedback that suits icons on a wallpaper: a slight fade and shrink instead of a ripple. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(label: String, onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .graphicsLayer {
            alpha = if (pressed) 0.55f else 1f
            scaleX = if (pressed) 0.94f else 1f
            scaleY = scaleX
            transformOrigin = TransformOrigin(0f, 0.5f) // full-width rows shrink towards their icon
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClickLabel = label,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        .semantics { contentDescription = label }
}

@Composable
private fun NameLabel(text: String, appearance: Appearance) {
    val palette = LocalPalette.current
    Text(
        text,
        color = palette.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = palette.style(appearance.appTextSize.sp.sp, appearance.appNameWeight).copy(fontFamily = palette.appFont),
    )
}

@Composable
fun FavouriteApp(key: String, state: HomeUiState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(key, state.appearance.iconStyle, state.appearance.iconSizeDp.dp, badge = state.hasDot(key))
        if (state.appearance.showNames) {
            Spacer(Modifier.width(12.dp))
            NameLabel(state.label(key), state.appearance)
        }
    }
}

/**
 * A folder: its first four apps in a little grid. When open, its apps slide out as a row of icons
 * beside it, over the wallpaper.
 *
 * It moves like the pie: the folder grows a little while it's open, the row grows out of the
 * folder's side, and its apps swoop out from the folder one after another, overshooting a touch
 * and settling. Closing tucks everything back into the folder. With a Loud accent the row is
 * stamped down and the apps punch out tilted, like stickers, and closing is instant.
 */
@Composable
fun FolderEntry(
    folder: Fav.Folder,
    state: HomeUiState,
    open: Boolean,
    onClose: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val appearance = state.appearance
    val size = appearance.iconSizeDp.dp
    val density = LocalDensity.current
    val loud = palette.loud
    val stagger = (if (loud) LoudStaggerMs else StaggerMs) * 3 / 2
    val row = remember { Animatable(0f) }
    val apps = remember(folder.apps.size) { List(folder.apps.size) { Animatable(0f) } }
    // The row stays up while it tucks away, after the folder has closed
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(open, apps) {
        if (open) {
            shown = true
            launch { row.animateTo(1f, if (loud) LoudStampSpring else DiscSpring) }
            apps.forEachIndexed { i, a -> launch { delay(i * stagger); a.animateTo(1f, if (loud) LoudPunchSpring else SwoopSpring) } }
        } else if (shown) {
            if (!loud) coroutineScope {
                launch { row.animateTo(0f, CloseTween) }
                apps.forEach { launch { it.animateTo(0f, CloseTween) } }
            }
            row.snapTo(0f)
            apps.forEach { it.snapTo(0f) }
            shown = false
        }
    }
    val grow by animateFloatAsState(
        if (open) (if (loud) 1.15f else 1.08f) else 1f,
        if (loud) LoudSelectSpring else SelectSpring,
        label = "folder open",
    )
    // How far each app travels out of the folder: from the folder's middle to its place in the row
    val step = with(density) { ((if (appearance.showNames) 52.dp else 40.dp) + 10.dp).toPx() }
    val fromFolder = with(density) { (size / 2 + 12.dp + 12.dp + 20.dp).toPx() }
    Box {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.graphicsLayer { scaleX = grow; scaleY = grow }) { FolderIcon(folder, state, size, open) }
            if (appearance.showNames) {
                Spacer(Modifier.width(12.dp))
                NameLabel(folder.name, appearance)
            }
        }
        if (shown) {
            Popup(
                alignment = Alignment.CenterStart,
                offset = IntOffset(with(density) { (size + 12.dp).roundToPx() }, 0),
                onDismissRequest = onClose,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .graphicsLayer {
                            // Grows out of the folder's side, as the pie's disc grows from the finger
                            val p = row.value
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            if (loud) {
                                scaleX = 1.3f - 0.3f * p
                                scaleY = 1.3f - 0.3f * p
                                rotationZ = LoudTiltDegrees / 2 * (1 - p)
                                alpha = if (p > 0.02f) 1f else 0f
                            } else {
                                scaleX = 0.4f + 0.6f * p
                                scaleY = 0.4f + 0.6f * p
                                alpha = p.coerceIn(0f, 1f)
                            }
                        }
                        .panel(RoundedCornerShape(18.dp), palette.pill)
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        folder.name.uppercase(),
                        color = palette.accent,
                        style = palette.style(10.5.sp, FontWeight.SemiBold, 1.sp).copy(shadow = null),
                    )
                    OnGlass { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        folder.apps.forEachIndexed { i, key ->
                            val label = state.label(key)
                            val p = apps.getOrNull(i)
                            Column(
                                Modifier
                                    .graphicsLayer {
                                        val t = p?.value ?: 1f
                                        if (loud) {
                                            // Punched out where it lands, tilted until it does
                                            val scale = 0.6f + 0.4f * t
                                            scaleX = scale
                                            scaleY = scale
                                            rotationZ = LoudTiltDegrees * (1 - t)
                                            alpha = if (t > 0.02f) 1f else 0f
                                        } else {
                                            // Out of the folder along a gentle curve, springing a touch past its place
                                            val back = 1 - t
                                            translationX = -back * (fromFolder + i * step)
                                            translationY = back * back * 10.dp.toPx()
                                            val scale = 0.35f + 0.65f * t
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = t.coerceIn(0f, 1f)
                                        }
                                    }
                                    .pressable(label, { onLaunch(key) }, { onAppLongClick(key) }),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(key, appearance.iconStyle, 40.dp, badge = state.hasDot(key))
                                if (appearance.showNames) {
                                    Spacer(Modifier.size(4.dp))
                                    Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            label,
                                            color = palette.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = palette.style(11.sp).copy(shadow = null),
                                        )
                                    }
                                }
                            }
                        }
                    } }
                }
            }
        }
    }
}

/** Closing tucks a folder's row away quickly, without the opening's overshoot. */
private val CloseTween = tween<Float>(durationMillis = 160)

@Composable
fun FolderIcon(folder: Fav.Folder, state: HomeUiState, size: Dp, open: Boolean = false) {
    val palette = LocalPalette.current
    val shape = palette.iconShape
    val style = state.appearance.iconStyle
    val padding = size * 0.15f
    val gap = size * 0.07f
    val mini = (size - padding * 2 - gap) / 2
    // On glass (the pie's disc), a folder looks like the search box on the search sheet
    val onGlass = palette.glass && LocalOnGlass.current
    Box(Modifier.size(size)) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (onGlass) Modifier.clip(shape).onGlassField(shape)
                    else Modifier.panel(shape, if (style == IconStyle.THEMED) palette.tileBg else palette.pill),
                )
                // Open: on glass, lit up (an accent outline can vanish into the glass's tint); otherwise outlined
                .then(
                    when {
                        !open -> Modifier
                        palette.glass -> Modifier.litRim(shape)
                        else -> Modifier.border(2.dp, palette.accent, shape)
                    },
                )
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            OnGlass {
                folder.apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { AppIcon(it, style, mini, badge = false) }
                    }
                }
            }
        }
        if (folder.apps.any(state::hasDot)) NotificationDot(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp))
    }
}

@Composable
fun folderLabel(folder: Fav.Folder) = stringResource(R.string.folder_description, folder.name)
