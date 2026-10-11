package io.github.peteroomen.launcher.system

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import io.github.peteroomen.launcher.R

object SystemActions {

    const val POWER_BUBBLE_PACKAGE = "io.github.peteroomen.powerbubble"
    private const val ACTION_SCREEN_OFF = "io.github.peteroomen.powerbubble.action.SCREEN_OFF"
    private const val ACTION_NOTIFICATIONS = "io.github.peteroomen.powerbubble.action.NOTIFICATIONS"

    /** Double-tap: Power Bubble's accessibility service turns the screen off like the power button. */
    fun screenOff(context: Context) {
        if (!start(context, Intent(ACTION_SCREEN_OFF).setPackage(POWER_BUBBLE_PACKAGE))) {
            Toast.makeText(context, R.string.power_bubble_missing, Toast.LENGTH_LONG).show()
        }
    }

    /** Swipe down. Uses the status bar service directly, falling back to Power Bubble. */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        val expanded = runCatching {
            val statusBar = context.getSystemService("statusbar")!!
            statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
        }.isSuccess
        if (!expanded) start(context, Intent(ACTION_NOTIFICATIONS).setPackage(POWER_BUBBLE_PACKAGE))
    }

    fun openAlarms(context: Context) = start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openCalendar(context: Context, eventId: Long? = null) {
        val uri = if (eventId != null) ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        else CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
        start(context, Intent(Intent.ACTION_VIEW, uri))
    }

    fun openMusic(context: Context, packageName: String?) {
        val intent = packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        start(context, intent)
    }

    fun webSearch(context: Context, query: String) =
        start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))

    fun setWallpaper(context: Context) =
        start(context, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), context.getString(R.string.set_wallpaper)))

    fun uninstall(context: Context, packageName: String) =
        start(context, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))

    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
