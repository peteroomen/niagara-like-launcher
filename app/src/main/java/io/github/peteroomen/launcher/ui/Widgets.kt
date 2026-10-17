package io.github.peteroomen.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.Appearance
import io.github.peteroomen.launcher.data.appNameWeight
import io.github.peteroomen.launcher.data.ClockAlign
import io.github.peteroomen.launcher.data.ClockBackground
import io.github.peteroomen.launcher.data.ClockStyle
import io.github.peteroomen.launcher.data.ClockWeight
import io.github.peteroomen.launcher.system.MediaState
import io.github.peteroomen.launcher.system.NowPlaying
import io.github.peteroomen.launcher.system.SystemActions
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
    val datePattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM") }
    val align = when (appearance.clockAlign) {
        ClockAlign.LEFT -> Alignment.Start
        ClockAlign.CENTER -> Alignment.CenterHorizontally
        ClockAlign.RIGHT -> Alignment.End
    }
    // The home screen keeps more room on the right (for the A–Z letters): centre on the screen, not that space
    val nudge = if (appearance.clockAlign == ClockAlign.CENTER && !appearance.hideLetters) 12.dp else 0.dp
    Column(Modifier.fillMaxWidth().offset(x = nudge), horizontalAlignment = align) {
        ClockPanel(appearance.clockBackground) {
            Column(horizontalAlignment = align) {
                when (appearance.clockStyle) {
                    ClockStyle.DIGITAL -> {
                        val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
                        val weight = when (appearance.clockWeight) {
                            ClockWeight.LIGHT -> FontWeight.Light
                            ClockWeight.REGULAR -> FontWeight.Normal
                            ClockWeight.BOLD -> FontWeight.SemiBold
                        }
                        Text(
                            DateFormat.format(timePattern, now).toString(),
                            color = palette.text,
                            modifier = Modifier.quietClick { SystemActions.openAlarms(context) },
                            style = palette.style(appearance.clockSize.sp.sp, weight, (-1).sp),
                        )
                    }
                    ClockStyle.ANALOG -> AnalogClock(
                        now = now,
                        size = appearance.clockSize.sp.dp * 2,
                        weight = appearance.clockWeight,
                        modifier = Modifier.padding(bottom = 10.dp).quietClick { SystemActions.openAlarms(context) },
                    )
                }
                Text(
                    DateFormat.format(datePattern, now).toString(),
                    color = palette.muted,
                    modifier = Modifier.quietClick { SystemActions.openCalendar(context) },
                    style = palette.style(14.sp),
                )
            }
        }
    }
}

/** The clock's optional panel, in its own look whatever the Panels setting is. */
@Composable
private fun ClockPanel(background: ClockBackground, content: @Composable () -> Unit) {
    if (background == ClockBackground.NONE) {
        content()
        return
    }
    val base = LocalPalette.current
    val palette = when {
        base.loud -> base
        else -> when (background) {
            ClockBackground.GLASS -> base.copy(glass = true, glassClear = true, glassBezel = false)
            ClockBackground.BLUR -> base.copy(glass = true, glassClear = false, glassBezel = false)
            ClockBackground.BEZEL -> base.copy(glass = true, glassClear = false, glassBezel = true)
            else -> base.copy(glass = false)
        }
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        Box(Modifier.panel(RoundedCornerShape(26.dp), palette.card, largeText = true).padding(horizontal = 22.dp, vertical = 16.dp)) {
            content()
        }
    }
}

/** A clock face: twelve marks, hour and minute hands, in the text colour with its shadow. */
@Composable
private fun AnalogClock(now: Long, size: Dp, weight: ClockWeight, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val minutes = calendar.get(Calendar.MINUTE)
    val hours = calendar.get(Calendar.HOUR) + minutes / 60f
    val stroke = when (weight) {
        ClockWeight.LIGHT -> 1f
        ClockWeight.REGULAR -> 1.5f
        ClockWeight.BOLD -> 2.2f
    }
    val shadow = palette.shadow
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        val unit = r / 40f * stroke
        fun hand(angle: Float, length: Float, width: Float, color: Color, offset: Offset = Offset.Zero) {
            val rad = Math.toRadians(angle.toDouble() - 90)
            val end = Offset(c.x + cos(rad).toFloat() * length, c.y + sin(rad).toFloat() * length)
            drawLine(color, c + offset, end + offset, strokeWidth = width, cap = StrokeCap.Round)
        }
        fun face(color: Color, offset: Offset) {
            for (i in 0 until 12) {
                val major = i % 3 == 0
                val rad = Math.toRadians(i * 30.0)
                val outer = r * 0.96f
                val inner = r * (if (major) 0.80f else 0.86f)
                drawLine(
                    color.copy(alpha = color.alpha * (if (major) 1f else 0.6f)),
                    Offset(c.x + sin(rad).toFloat() * inner, c.y - cos(rad).toFloat() * inner) + offset,
                    Offset(c.x + sin(rad).toFloat() * outer, c.y - cos(rad).toFloat() * outer) + offset,
                    strokeWidth = unit * (if (major) 2.2f else 1.4f),
                    cap = StrokeCap.Round,
                )
            }
            hand(hours * 30f, r * 0.5f, unit * 3.4f, color, offset)
            hand(minutes * 6f, r * 0.74f, unit * 2.4f, color, offset)
        }
        // A plain offset shadow under everything, for contrast like the text's
        shadow?.let { face(it.color, Offset(0f, maxOf(1f, it.offset.y * 1.5f))) }
        face(palette.text, Offset.Zero)
        drawCircle(palette.accent, radius = unit * 3.2f, center = c)
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
    val shape = RoundedCornerShape(CardCorner)
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    Column(
        Modifier
            .widthIn(max = 480.dp) // the full width on a phone; not stretched across a tablet
            .fillMaxWidth()
            .panel(shape, palette.card)
            .onSizeChanged { cardSize = it }
            .quietClick {
                when {
                    !hasAccess -> onNeedAccess()
                    nowPlaying == null -> SystemActions.openMusic(context, null)
                    !MediaState.openPlayer() -> SystemActions.openMusic(context, nowPlaying.packageName)
                }
            },
    ) {
        // The art fills the card's whole height above the progress line, edge to edge (the card's
        // corners round it). It's sized from the text and controls beside it, never the other way.
        val density = LocalDensity.current
        var contentHeight by remember { mutableIntStateOf(0) }
        val artSize = maxOf(with(density) { contentHeight.toDp() }, MinArtSize)
        val art = nowPlaying?.art
        val image = remember(art) { art?.asImageBitmap() }
        val hasProgress = nowPlaying != null && nowPlaying.durationMs > 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(image, artSize, cardSize)
            Row(
                Modifier.weight(1f).onSizeChanged { contentHeight = it.height }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(start = 2.dp)) {
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
        }
        if (hasProgress) Progress(nowPlaying!!)
    }
}

private val MinArtSize = 72.dp
private val CardCorner = 20.dp

/** Fades the right-hand third out to transparent. */
private fun Modifier.fadeOutEnd(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(0.6f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }

@Composable
private fun AlbumArt(image: ImageBitmap?, size: Dp, cardSize: IntSize) {
    val palette = LocalPalette.current
    if (image != null && palette.glass) {
        // Under the card's glass: bent and lit at the card's edges like the wallpaper is, and
        // fading into the rest of the glass on its inner side
        Box(Modifier.size(size).fadeOutEnd().underGlass(image, cardSize, CardCorner))
    } else if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
    } else {
        // No cover: on glass just a faint tile under it, so nothing solid sits on the glass
        val tile = if (palette.glass) palette.tileBg.copy(alpha = palette.tileBg.alpha * 0.35f) else palette.tileBg
        Box(Modifier.size(size).background(tile), contentAlignment = Alignment.Center) {
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
    // Along the bottom edge of the card, in the accent colour; smooth between the once-a-second ticks
    val shown by animateFloatAsState(fraction, tween(1000, easing = LinearEasing), label = "progress")
    Box(Modifier.fillMaxWidth().height(4.dp).background(palette.text.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(palette.accent))
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
    /** A line under the name, e.g. why it's suggested. */
    subtitle: String? = null,
) {
    val palette = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(key, appearance.iconStyle, ListIconSize, badge = false)
        Column {
            Text(
                label,
                color = if (highlighted) palette.accent else palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = palette.style(appearance.appTextSize.sp.sp, appearance.appNameWeight).copy(fontFamily = palette.appFont),
            )
            if (subtitle != null) {
                Text(subtitle, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = palette.style(12.sp))
            }
        }
    }
}
