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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.IconCache
import io.github.peteroomen.launcher.data.IconStyle
import io.github.peteroomen.launcher.model.Fav

private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
private fun rememberIcons(key: String, sizePx: Int): IconCache.Icons? {
    val loader = LocalIconLoader.current
    val icons by produceState(loader.cached(key, sizePx), key, sizePx, loader) {
        if (value == null) value = loader.load(key, sizePx)
    }
    return icons
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
            style == IconStyle.THEMED && icons.mono != null -> Box(Modifier.fillMaxSize().panel(shape, palette.tileBg)) {
                Image(icons.mono, contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(palette.tileFg))
            }
            else -> Image(
                icons.full,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().then(if (icons.adaptive) Modifier.clip(shape) else Modifier),
                colorFilter = if (style == IconStyle.GREYSCALE) Greyscale else null,
            )
        }
        if (badge) NotificationDot(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp))
    }
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
private fun NameLabel(text: String, size: Int = 17) {
    val palette = LocalPalette.current
    Text(
        text,
        color = palette.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = palette.style(size.sp, FontWeight.Medium),
    )
}

@Composable
fun FavouriteApp(key: String, state: HomeUiState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(key, state.appearance.iconStyle, state.appearance.iconSizeDp.dp, badge = state.hasDot(key))
        if (state.appearance.showNames) {
            Spacer(Modifier.width(12.dp))
            NameLabel(state.label(key))
        }
    }
}

/**
 * A folder: its first four apps in a little grid. When open, its apps slide out as a row of icons
 * beside it, over the wallpaper.
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
    Box {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            FolderIcon(folder, state, size, open)
            if (appearance.showNames) {
                Spacer(Modifier.width(12.dp))
                NameLabel(folder.name)
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.CenterStart,
                offset = IntOffset(with(density) { (size + 12.dp).roundToPx() }, 0),
                onDismissRequest = onClose,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .panel(RoundedCornerShape(18.dp), palette.pill)
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        folder.name.uppercase(),
                        color = palette.accent,
                        style = palette.style(10.5.sp, FontWeight.SemiBold, 1.sp).copy(shadow = null),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        folder.apps.forEach { key ->
                            val label = state.label(key)
                            Column(
                                Modifier.pressable(label, { onLaunch(key) }, { onAppLongClick(key) }),
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
                    }
                }
            }
        }
    }
}

@Composable
fun FolderIcon(folder: Fav.Folder, state: HomeUiState, size: Dp, open: Boolean = false) {
    val palette = LocalPalette.current
    val shape = palette.iconShape
    val style = state.appearance.iconStyle
    val padding = size * 0.15f
    val gap = size * 0.07f
    val mini = (size - padding * 2 - gap) / 2
    Box(Modifier.size(size)) {
        Column(
            Modifier
                .fillMaxSize()
                .panel(shape, if (style == IconStyle.THEMED) palette.tileBg else palette.pill)
                .then(if (open) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            folder.apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { AppIcon(it, style, mini, badge = false) }
                }
            }
        }
        if (folder.apps.any(state::hasDot)) NotificationDot(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp))
    }
}

@Composable
fun folderLabel(folder: Fav.Folder) = stringResource(R.string.folder_description, folder.name)
