package io.github.peteroomen.launcher.system

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

data class NowPlaying(
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val packageName: String,
    val durationMs: Long,
    /** Playback position at [positionAt] (elapsed realtime); it moves on at [speed] while playing. */
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float,
) {
    fun positionNow(): Long {
        if (!playing || positionMs < 0) return positionMs.coerceAtLeast(0)
        val moved = ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
        return (positionMs + moved).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }
}

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

    fun skipNext() {
        controller?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        controller?.transportControls?.skipToPrevious()
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
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val state = c?.playbackState
        nowPlaying.value = if (c == null || title.isNullOrBlank()) null else NowPlaying(
            title = title,
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST),
            art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = state?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: -1,
            positionAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed ?: 1f,
        )
    }
}
