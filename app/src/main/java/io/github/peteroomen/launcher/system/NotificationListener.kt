package io.github.peteroomen.launcher.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
    }
}
