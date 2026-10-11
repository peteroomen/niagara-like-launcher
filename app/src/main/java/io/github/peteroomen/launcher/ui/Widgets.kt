package io.github.peteroomen.launcher.ui

import android.Manifest
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.CalendarState
import io.github.peteroomen.launcher.data.NextEvent
import io.github.peteroomen.launcher.system.MediaState
import io.github.peteroomen.launcher.system.NowPlaying
import io.github.peteroomen.launcher.system.SystemActions
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/** Clickable without a ripple, so nothing flashes over the wallpaper. */
@Composable
fun Modifier.quietClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun ClockAndDate() {
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
    Column {
        Text(
            DateFormat.format(timePattern, now).toString(),
            color = palette.text,
            modifier = Modifier.quietClick { SystemActions.openAlarms(context) },
            style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, shadow = palette.shadow),
        )
        Text(
            DateFormat.format(datePattern, now).toString(),
            color = palette.muted,
            modifier = Modifier.quietClick { SystemActions.openCalendar(context) },
            style = TextStyle(fontSize = 14.sp, shadow = palette.shadow),
        )
    }
}

@Composable
private fun WidgetLabel(text: String) {
    val palette = LocalPalette.current
    Text(
        text.uppercase(),
        color = palette.accent,
        style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, shadow = palette.shadow),
    )
}

@Composable
private fun WidgetText(text: String, muted: Boolean = false, size: Int = 15) {
    val palette = LocalPalette.current
    Text(
        text,
        color = if (muted) palette.muted else palette.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = size.sp, shadow = palette.shadow),
    )
}

@Composable
fun CalendarWidget(state: CalendarState, onPermissionResult: () -> Unit) {
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionResult() }
    Column(
        Modifier.quietClick {
            when (state) {
                CalendarState.NoPermission -> askPermission.launch(Manifest.permission.READ_CALENDAR)
                CalendarState.Empty -> SystemActions.openCalendar(context)
                is CalendarState.Upcoming -> SystemActions.openCalendar(context, state.event.eventId)
            }
        },
    ) {
        WidgetLabel(stringResource(R.string.calendar))
        when (state) {
            CalendarState.NoPermission -> WidgetText(stringResource(R.string.calendar_permission), muted = true)
            CalendarState.Empty -> WidgetText(stringResource(R.string.calendar_empty), muted = true)
            is CalendarState.Upcoming -> WidgetText(describe(context, state.event))
        }
    }
}

/** "Today 14:30 · Dentist", "Tomorrow · Holiday", "Tue 09:00 · Standup" */
private fun describe(context: Context, event: NextEvent): String {
    val day = when {
        DateUtils.isToday(event.begin) || event.begin < System.currentTimeMillis() -> context.getString(R.string.today)
        DateUtils.isToday(event.begin - DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.tomorrow)
        else -> DateFormat.format("EEE", event.begin).toString()
    }
    val time = if (event.allDay) "" else " " + DateFormat.getTimeFormat(context).format(Date(event.begin))
    return "$day$time · ${event.title}"
}

@Composable
fun MusicWidget(nowPlaying: NowPlaying?, hasAccess: Boolean, appLabel: String?, onNeedAccess: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    Column(
        Modifier.quietClick {
            when {
                !hasAccess -> onNeedAccess()
                nowPlaying == null -> SystemActions.openMusic(context, null)
                !MediaState.openPlayer() -> SystemActions.openMusic(context, nowPlaying.packageName)
            }
        },
    ) {
        WidgetLabel(appLabel ?: stringResource(R.string.music))
        when {
            !hasAccess -> WidgetText(stringResource(R.string.music_needs_access), muted = true)
            nowPlaying == null -> WidgetText(stringResource(R.string.nothing_playing), muted = true)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f, fill = false)) { WidgetText(nowPlaying.title) }
                    Text(
                        stringResource(if (nowPlaying.playing) R.string.pause else R.string.play),
                        color = palette.text,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .border(1.dp, palette.muted, RoundedCornerShape(50))
                            .quietClick { MediaState.togglePlayPause() }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                nowPlaying.artist?.let { WidgetText(it, muted = true, size = 13) }
            }
        }
    }
}
