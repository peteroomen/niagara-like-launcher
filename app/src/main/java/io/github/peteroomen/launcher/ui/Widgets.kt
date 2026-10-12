package io.github.peteroomen.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.Appearance
import io.github.peteroomen.launcher.data.ClockWeight
import io.github.peteroomen.launcher.system.MediaState
import io.github.peteroomen.launcher.system.NowPlaying
import io.github.peteroomen.launcher.system.SystemActions
import kotlinx.coroutines.delay
import java.util.Locale

/** Clickable without a ripple, so nothing flashes over the wallpaper. */
@Composable
fun Modifier.quietClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun ClockAndDate(appearance: Appearance) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000) // tick on the minute
        }
    }
    val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    val datePattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM") }
    val weight = when (appearance.clockWeight) {
        ClockWeight.LIGHT -> FontWeight.Light
        ClockWeight.REGULAR -> FontWeight.Normal
        ClockWeight.BOLD -> FontWeight.SemiBold
    }
    Column {
        Text(
            DateFormat.format(timePattern, now).toString(),
            color = palette.text,
            modifier = Modifier.quietClick { SystemActions.openAlarms(context) },
            style = palette.style(appearance.clockSize.sp.sp, weight, (-1).sp),
        )
        Text(
            DateFormat.format(datePattern, now).toString(),
            color = palette.muted,
            modifier = Modifier.quietClick { SystemActions.openCalendar(context) },
            style = palette.style(14.sp),
        )
    }
}

@Composable
private fun WidgetLabel(text: String) {
    val palette = LocalPalette.current
    Text(text.uppercase(), color = palette.accent, style = palette.style(10.5.sp, FontWeight.SemiBold, 1.sp))
}

@Composable
private fun WidgetText(text: String, muted: Boolean = false, size: Int = 15, weight: FontWeight = FontWeight.Normal) {
    val palette = LocalPalette.current
    Text(
        text,
        color = if (muted) palette.muted else palette.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = palette.style(size.sp, weight),
    )
}

/** A small player: album art, title and artist, previous / play-pause / next, and a progress line. */
@Composable
fun MusicWidget(nowPlaying: NowPlaying?, hasAccess: Boolean, appLabel: String?, onNeedAccess: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .panel(shape, palette.card)
            .quietClick {
                when {
                    !hasAccess -> onNeedAccess()
                    nowPlaying == null -> SystemActions.openMusic(context, null)
                    !MediaState.openPlayer() -> SystemActions.openMusic(context, nowPlaying.packageName)
                }
            },
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(nowPlaying)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                when {
                    !hasAccess -> {
                        WidgetLabel(stringResource(R.string.music))
                        WidgetText(stringResource(R.string.music_needs_access), muted = true, size = 13)
                    }
                    nowPlaying == null -> {
                        WidgetLabel(stringResource(R.string.music))
                        WidgetText(stringResource(R.string.nothing_playing), muted = true)
                    }
                    else -> {
                        WidgetLabel(appLabel ?: stringResource(R.string.music))
                        WidgetText(nowPlaying.title, weight = FontWeight.Medium)
                        nowPlaying.artist?.let { WidgetText(it, muted = true, size = 13) }
                    }
                }
            }
            if (hasAccess && nowPlaying != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(R.drawable.ic_skip_previous, R.string.previous, 36) { MediaState.skipPrevious() }
                    ControlButton(
                        if (nowPlaying.playing) R.drawable.ic_pause else R.drawable.ic_play,
                        if (nowPlaying.playing) R.string.pause else R.string.play,
                        44,
                        filled = true,
                    ) { MediaState.togglePlayPause() }
                    ControlButton(R.drawable.ic_skip_next, R.string.next, 36) { MediaState.skipNext() }
                }
            }
        }
        if (nowPlaying != null && nowPlaying.durationMs > 0) Progress(nowPlaying)
    }
}

@Composable
private fun AlbumArt(nowPlaying: NowPlaying?) {
    val palette = LocalPalette.current
    val art = nowPlaying?.art
    val image = remember(art) { art?.asImageBitmap() }
    val shape = RoundedCornerShape(12.dp)
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(shape))
    } else {
        Box(Modifier.size(52.dp).panel(shape, palette.tileBg), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = palette.tileFg, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ControlButton(icon: Int, label: Int, size: Int, filled: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(palette.text) else Modifier)
            .clickable(onClickLabel = stringResource(label), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(label),
            tint = if (filled) palette.backdrop else palette.text,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

/** A thin line along the bottom of the card, ticking along while music plays. */
@Composable
private fun Progress(nowPlaying: NowPlaying) {
    val palette = LocalPalette.current
    val position by produceState(nowPlaying.positionNow(), nowPlaying) {
        while (nowPlaying.playing) {
            value = nowPlaying.positionNow()
            delay(1000)
        }
        value = nowPlaying.positionNow()
    }
    val fraction = (position.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(3.dp).background(palette.text.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(palette.accent))
    }
}

/** Icon size in the A–Z list and search results. */
val ListIconSize = 30.dp

@Composable
fun AppRow(
    key: String,
    label: String,
    appearance: Appearance,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val palette = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(key, appearance.iconStyle, ListIconSize, badge = false)
        Text(
            label,
            color = if (highlighted) palette.accent else palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = palette.style(20.sp, FontWeight.Medium),
        )
    }
}
