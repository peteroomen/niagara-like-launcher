package io.github.peteroomen.launcher.system

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow

data class NowPlaying(val title: String, val artist: String?, val playing: Boolean, val packageName: String)

/**
 * What's playing, for the music widget. Reading other apps' media sessions needs notification
 * access, so this is attached by [NotificationListener].
 */
object MediaState {

    val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    fun attach(context: Context) {
        val m = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, NotificationListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listener)
            manager = m
            pick(m.getActiveSessions(listener))
        }
    }

    fun detach() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        manager = null
        controller?.unregisterCallback(callback)
        controller = null
        publish()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    /** Opens the player's own screen, if the session offers one. */
    fun openPlayer(): Boolean = runCatching { controller?.sessionActivity?.send() != null }.getOrDefault(false)

    private fun pick(sessions: List<MediaController>) {
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        nowPlaying.value = if (c == null || title.isNullOrBlank()) null else NowPlaying(
            title = title,
            artist = c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
        )
    }
}
