package io.github.peteroomen.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import java.util.concurrent.TimeUnit

data class NextEvent(val eventId: Long, val title: String, val begin: Long, val allDay: Boolean)

sealed interface CalendarState {
    data object NoPermission : CalendarState
    data object Empty : CalendarState
    data class Upcoming(val event: NextEvent) : CalendarState
}

object CalendarSource {

    /** The next event that hasn't ended yet, within a week. */
    fun read(context: Context): CalendarState {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return CalendarState.NoPermission
        }
        val now = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + TimeUnit.DAYS.toMillis(7))
        }.build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY)
        return runCatching {
            context.contentResolver.query(uri, projection, "${Instances.END} > ?", arrayOf(now.toString()), "${Instances.BEGIN} ASC")?.use { c ->
                if (!c.moveToFirst()) return@use null
                NextEvent(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getInt(3) == 1)
            }
        }.getOrNull()?.let { CalendarState.Upcoming(it) } ?: CalendarState.Empty
    }
}
