package io.github.peteroomen.launcher.system

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow

/** Packages with a notification that should show a dot, and whether we have notification access at all. */
object NotificationDots {
    val packages = MutableStateFlow<Set<String>>(emptySet())
    val connected = MutableStateFlow(false)
}

class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationDots.connected.value = true
        publish()
        MediaState.attach(this)
    }

    override fun onListenerDisconnected() {
        NotificationDots.connected.value = false
        NotificationDots.packages.value = emptySet()
        MediaState.detach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = publish()

    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = currentRanking
        val r = Ranking()
        // Respect each app's "show notification dot" setting, and skip ongoing ones (music, downloads)
        NotificationDots.packages.value = active
            .filter { it.isClearable && (ranking == null || !ranking.getRanking(it.key, r) || r.canShowBadge()) }
            .map { it.packageName }
            .toSet()
        // Media notifications carry the artwork some players (YouTube) leave out of their session
        MediaState.setNotificationArt(
            active.filter { it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }
                .mapNotNull { sbn -> artOf(sbn.notification)?.let { sbn.packageName to it } }
                .toMap(),
        )
    }

    private fun artOf(notification: Notification): Bitmap? = runCatching {
        notification.getLargeIcon()?.loadDrawable(this)?.let { d ->
            (d as? BitmapDrawable)?.bitmap ?: d.toBitmap(256, 256)
        }
    }.getOrNull()
}
