package io.github.peteroomen.launcher.system

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

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
 * What's playing, for the media widget: music, podcasts, YouTube and other video apps, browsers,
 * anything with a media session. Reading other apps' media sessions needs notification access, so
 * this is attached by [NotificationListener].
 *
 * Every active session is watched, not just the one shown, so starting a video while music sits
 * paused switches the widget over to the video.
 */
object MediaState {

    val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var sessions: List<MediaController> = emptyList()
    private var controller: MediaController? = null
    /** When each session (by package) was last seen start playing, to prefer the newest. */
    private val startedAt = mutableMapOf<String, Long>()
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()

    /** Artwork from each player's media notification, by package. */
    private var notificationArt: Map<String, Bitmap> = emptyMap()

    fun setNotificationArt(art: Map<String, Bitmap>) {
        val changed = art.keys != notificationArt.keys || art.any { (k, v) -> notificationArt[k]?.sameAs(v) != true }
        notificationArt = art
        if (changed) publish()
    }

    /** Art some apps only give as a link to an image, loaded off the main thread and kept here. */
    private val loadedArt = mutableMapOf<String, Bitmap?>()
    private val artLoader = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }

    fun attach(context: Context) {
        appContext = context.applicationContext
        val m = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, NotificationListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listener)
            manager = m
            watch(m.getActiveSessions(listener))
        }
    }

    fun detach() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        manager = null
        watch(emptyList())
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

    /**
     * Opens the player's own screen, if the session offers one. Since Android 14 a pending intent
     * only gets the sender's right to start activities if the sender opts in; without that it
     * silently does nothing unless the player itself is on screen (as YouTube is in picture in
     * picture). The launcher is on screen, so lend it that right.
     */
    fun openPlayer(): Boolean {
        val activity = controller?.sessionActivity ?: return false
        val options = ActivityOptions.makeBasic().apply {
            when {
                Build.VERSION.SDK_INT >= 36 ->
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                Build.VERSION.SDK_INT >= 34 ->
                    @Suppress("DEPRECATION")
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        }.toBundle()
        return runCatching { activity.send(appContext, 0, null, null, null, null, options) }.isSuccess
    }

    private fun watch(active: List<MediaController>) {
        val tokens = active.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it.sessionToken !in tokens }.forEach { gone ->
            callbacks.remove(gone)?.let(gone::unregisterCallback)
        }
        val known = callbacks.keys.map { it.sessionToken }.toSet()
        active.filter { it.sessionToken !in known }.forEach { c ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = changed(c)
                override fun onMetadataChanged(metadata: MediaMetadata?) = changed(c)
                override fun onSessionDestroyed() = watch(sessions.filter { it.sessionToken != c.sessionToken })
            }
            c.registerCallback(callback)
            callbacks[c] = callback
            if (c.isPlaying()) startedAt[c.packageName] = SystemClock.elapsedRealtime()
        }
        sessions = active
        pick()
    }

    private fun changed(c: MediaController) {
        if (c.isPlaying() && controller?.sessionToken != c.sessionToken) startedAt[c.packageName] = SystemClock.elapsedRealtime()
        pick()
    }

    /**
     * The session to show: whatever started playing most recently; with nothing playing, the one
     * already shown (so pausing doesn't swap it for another app), else the last one that played.
     */
    private fun pick() {
        val playing = sessions.filter { it.isPlaying() }
        val best = playing.maxByOrNull { startedAt[it.packageName] ?: 0L }
            ?: sessions.firstOrNull { it.sessionToken == controller?.sessionToken }
            ?: sessions.maxByOrNull { startedAt[it.packageName] ?: 0L }
        controller = best
        publish()
    }

    private fun MediaController.isPlaying() = playbackState?.state.let {
        it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        // Video apps and browsers often fill in only the display title and subtitle
        val title = meta?.text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val state = c?.playbackState
        nowPlaying.value = if (c == null || title == null) null else NowPlaying(
            title = title,
            artist = meta.text(
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_AUTHOR,
            )?.takeIf { it != title },
            art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: artFromLink(meta)
                ?: notificationArt[c.packageName],
            playing = c.isPlaying(),
            packageName = c.packageName,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: -1,
            positionAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed ?: 1f,
        )
    }

    private fun MediaMetadata.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> getText(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() } }

    /** Art given as a content link: loaded once in the background, then shown on the next publish. */
    private fun artFromLink(meta: MediaMetadata): Bitmap? {
        val link = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            .firstNotNullOfOrNull { meta.getString(it)?.takeIf { uri -> uri.startsWith("content:") || uri.startsWith("android.resource:") } }
            ?: return null
        if (link in loadedArt) return loadedArt[link]
        val context = appContext ?: return null
        loadedArt[link] = null
        artLoader.execute {
            val bitmap = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(link))) { decoder, info, _ ->
                    // Small is plenty for the widget
                    val scale = 256f / maxOf(info.size.width, info.size.height)
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            main.post {
                if (loadedArt.size > 20) loadedArt.clear()
                loadedArt[link] = bitmap
                publish()
            }
        }
        return null
    }
}
