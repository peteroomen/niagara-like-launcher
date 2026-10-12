package io.github.peteroomen.launcher.system

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import android.view.SurfaceControlViewHost
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.data.AppRepository
import io.github.peteroomen.launcher.data.FavouritesLayout
import io.github.peteroomen.launcher.data.IconCache
import io.github.peteroomen.launcher.data.LauncherStore
import io.github.peteroomen.launcher.homeUiState
import io.github.peteroomen.launcher.indexLetter
import io.github.peteroomen.launcher.ui.AzStrip
import io.github.peteroomen.launcher.ui.ClockAndDate
import io.github.peteroomen.launcher.ui.Favourites
import io.github.peteroomen.launcher.ui.IconLoader
import io.github.peteroomen.launcher.ui.LocalIconLoader
import io.github.peteroomen.launcher.ui.LocalPalette
import io.github.peteroomen.launcher.ui.MusicWidget
import io.github.peteroomen.launcher.ui.Palette
import io.github.peteroomen.launcher.ui.ReadabilityBackdrop
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lets the system wallpaper picker show this home screen in its preview, over the wallpaper being
 * chosen. The picker finds this provider through the home activity's
 * `com.android.launcher3.grid_control` meta-data (the same handshake as Android's own launcher) and
 * asks for a preview, handing over a surface to draw into. Only callers allowed to bind
 * wallpapers (the system picker) get one.
 */
class HomePreviewProvider : ContentProvider() {

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_GET_PREVIEW || extras == null) return null
        val context = context ?: return null
        if (context.checkPermission(Manifest.permission.BIND_WALLPAPER, Binder.getCallingPid(), Binder.getCallingUid()) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val hostToken = extras.getBinder(KEY_HOST_TOKEN) ?: return null
        val width = extras.getInt(KEY_WIDTH)
        val height = extras.getInt(KEY_HEIGHT)
        val displayId = extras.getInt(KEY_DISPLAY_ID)
        if (width <= 0 || height <= 0) return null

        return try {
            val identity = Binder.clearCallingIdentity()
            val state = try {
                runBlocking { withTimeoutOrNull(3_000) { loadState(context) } }
            } finally {
                Binder.restoreCallingIdentity(identity)
            } ?: return null
            // Views and the surface host belong on the main thread
            val task = FutureTask { HomePreview(context, hostToken, displayId, width, height, state) }
            Handler(Looper.getMainLooper()).post(task)
            val preview = task.get(5, TimeUnit.SECONDS)
            Bundle().apply {
                putParcelable(KEY_SURFACE_PACKAGE, preview.surfacePackage)
                // The picker sends this back when it's done with the preview
                val messenger = Messenger(
                    Handler(Looper.getMainLooper()) { message ->
                        if (message.what == 0) preview.release()
                        true
                    },
                )
                putParcelable(KEY_CALLBACK, Message.obtain().apply { replyTo = messenger })
            }
        } catch (e: Exception) {
            Log.w("Launcher", "Couldn't render the home screen preview", e)
            null
        }
    }

    private suspend fun loadState(context: Context): Pair<HomeUiState, Palette> = withContext(Dispatchers.IO) {
        val saved = LauncherStore(context).saved.first()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val apps = AppRepository.toApps(launcherApps.getActivityList(null, Process.myUserHandle()), context.packageManager)
        val state = homeUiState(apps, saved)
        state to PaletteSource.resolve(PaletteSource.wallpaperColors(context), saved.appearance)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        const val METHOD_GET_PREVIEW = "get_preview"
        const val KEY_HOST_TOKEN = "host_token"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_DISPLAY_ID = "display_id"
        const val KEY_SURFACE_PACKAGE = "surface_package"
        const val KEY_CALLBACK = "callback"
    }
}

/**
 * The home screen, laid out at full screen size and scaled down into the picker's surface. The
 * picker draws the wallpaper itself, underneath.
 */
private class HomePreview(
    context: Context,
    hostToken: IBinder,
    displayId: Int,
    width: Int,
    height: Int,
    state: Pair<HomeUiState, Palette>,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private val host: SurfaceControlViewHost
    val surfacePackage get() = host.surfacePackage

    init {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            ?: error("No display $displayId")
        val displayContext = context.createDisplayContext(display)
        val screen = displayContext.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val scale = minOf(width.toFloat() / screen.width(), height.toFloat() / screen.height())

        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val icons = PreviewIcons(displayContext)
        val compose = ComposeView(displayContext).apply {
            setViewTreeLifecycleOwner(this@HomePreview)
            setViewTreeSavedStateRegistryOwner(this@HomePreview)
            setViewTreeViewModelStoreOwner(this@HomePreview)
            pivotX = 0f
            pivotY = 0f
            scaleX = scale
            scaleY = scale
            setContent { PreviewHome(state.first, state.second, icons) }
        }
        val root = FrameLayout(displayContext).apply {
            addView(compose, FrameLayout.LayoutParams(screen.width(), screen.height()))
        }
        host = SurfaceControlViewHost(displayContext, display, hostToken)
        host.setView(root, width, height)
        runCatching { hostToken.linkToDeath({ Handler(Looper.getMainLooper()).post { release() } }, 0) }
    }

    fun release() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        host.release()
    }
}

/** App icons for the preview, loaded straight from the system. */
private class PreviewIcons(context: Context) : IconLoader {
    private val cache = IconCache(context)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val infos by lazy {
        launcherApps.getActivityList(null, Process.myUserHandle()).associateBy { it.componentName.flattenToShortString() }
    }

    override fun cached(key: String, sizePx: Int) = cache.cached(key, sizePx)

    override suspend fun load(key: String, sizePx: Int): IconCache.Icons? = withContext(Dispatchers.IO) {
        infos[key]?.let { cache.load(it, key, sizePx) }
    }
}

/** The home screen as it looks at rest: clock, favourites, music card and the A–Z strip. */
@Composable
private fun PreviewHome(state: HomeUiState, palette: Palette, icons: IconLoader) {
    CompositionLocalProvider(LocalPalette provides palette, LocalIconLoader provides icons) {
        Box(Modifier.fillMaxSize()) {
            ReadabilityBackdrop(state.appearance.backdrop)
            // No system bars here: leave room where the status and navigation bars would be
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp, bottom = 48.dp)
                    .padding(start = 24.dp, end = if (state.appearance.hideLetters) 24.dp else 48.dp, top = 28.dp, bottom = 16.dp),
            ) {
                ClockAndDate(state.appearance)
                if (state.appearance.favouritesLayout == FavouritesLayout.PIE) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Favourites(
                            state = state,
                            openFolder = null,
                            onToggleFolder = {},
                            onCloseFolder = {},
                            onLaunch = {},
                            onMenu = {},
                        )
                    }
                }
                MusicWidget(nowPlaying = MediaState.nowPlaying.collectAsState().value, hasAccess = true, appLabel = null, onNeedAccess = {})
            }
            if (!state.appearance.hideLetters) {
                AzStrip(
                    available = state.apps.map { indexLetter(state.label(it.key)) }.toSet(),
                    onScrub = { _, _ -> },
                    onSelect = {},
                    onRelease = {},
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 88.dp)
                        .fillMaxHeight(0.72f),
                )
            }
        }
    }
}

