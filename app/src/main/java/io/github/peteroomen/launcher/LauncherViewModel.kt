package io.github.peteroomen.launcher

import android.app.Application
import android.app.WallpaperManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.peteroomen.launcher.data.AppInfo
import io.github.peteroomen.launcher.data.AppRepository
import io.github.peteroomen.launcher.data.Appearance
import io.github.peteroomen.launcher.data.CalendarSource
import io.github.peteroomen.launcher.data.CalendarState
import io.github.peteroomen.launcher.data.DefaultFavourites
import io.github.peteroomen.launcher.data.IconCache
import io.github.peteroomen.launcher.data.LauncherStore
import io.github.peteroomen.launcher.data.Saved
import io.github.peteroomen.launcher.model.Fav
import io.github.peteroomen.launcher.model.FavOps
import io.github.peteroomen.launcher.system.NotificationDots
import io.github.peteroomen.launcher.system.PaletteSource
import io.github.peteroomen.launcher.system.SystemActions
import io.github.peteroomen.launcher.system.WallpaperBackdrop
import io.github.peteroomen.launcher.ui.IconLoader
import io.github.peteroomen.launcher.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

data class HomeUiState(
    /** Apps for the A–Z list and search, not hidden, sorted by the name shown. */
    val apps: List<AppInfo> = emptyList(),
    val byKey: Map<String, AppInfo> = emptyMap(),
    /** Favourites to draw (installed, not hidden). */
    val favs: List<Fav> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val appearance: Appearance = Appearance(),
    val dots: Set<String> = emptySet(),
) {
    fun label(key: String) = renames[key] ?: byKey[key]?.label ?: key
    fun hasDot(key: String) = byKey[key]?.packageName in dots
    fun isFavourite(key: String) = FavOps.contains(favs, key)
    fun folderOf(key: String) = FavOps.folderOf(favs, key)
    val folders get() = favs.filterIsInstance<Fav.Folder>()
    fun folder(id: String) = folders.firstOrNull { it.id == id }
}

/** First letter for the A–Z list; accented letters count as their base letter, anything else as '#'. */
fun indexLetter(label: String): Char {
    val c = Normalizer.normalize(label.take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

class LauncherViewModel(application: Application) : AndroidViewModel(application), IconLoader {

    private val app get() = getApplication<Application>()
    private val repo = AppRepository(application, viewModelScope)
    private val iconCache = IconCache(application)
    private val store = LauncherStore(application)

    private val wallpaperColors = MutableStateFlow(PaletteSource.wallpaperColors(application))
    /** Blurred wallpaper for liquid glass; null when it can't be read. */
    val glassBackdrop = MutableStateFlow<WallpaperBackdrop.Images?>(null)
    val calendar = MutableStateFlow<CalendarState>(CalendarState.Empty)
    /** Fires when Home is pressed while the launcher is already showing. */
    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val wallpaperManager = WallpaperManager.getInstance(application)
    private val colorsListener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) {
            wallpaperColors.value = colors
            onWallpaperChanged()
        }
    }

    val ui: StateFlow<HomeUiState> = combine(repo.apps, store.saved, NotificationDots.packages) { apps, saved, dots ->
        val byKey = apps.associateBy { it.key }
        val label = { key: String -> saved.renames[key] ?: byKey[key]?.label ?: key }
        HomeUiState(
            apps = apps.filter { it.key !in saved.hidden }.sortedBy { label(it.key).lowercase() },
            byKey = byKey,
            favs = FavOps.visible(saved.favs) { it in byKey && it !in saved.hidden },
            hidden = saved.hidden,
            renames = saved.renames,
            appearance = saved.appearance,
            dots = dots,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    /** Colours, shadows, font and icon shape: the wallpaper's colours combined with the appearance settings. */
    val palette: StateFlow<Palette> = combine(wallpaperColors, ui.map { it.appearance }.distinctUntilChanged()) { colors, appearance ->
        PaletteSource.resolve(colors, appearance)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PaletteSource.resolve(wallpaperColors.value, Appearance()))

    init {
        wallpaperManager.addOnColorsChangedListener(colorsListener, Handler(Looper.getMainLooper()))
        // Icons may have changed when apps are installed or updated
        repo.apps.drop(1).onEach { iconCache.clear() }.launchIn(viewModelScope)
        refreshGlassBackdrop()
        viewModelScope.launch {
            val apps = repo.apps.first { it.isNotEmpty() }
            store.update { if (it.seeded) it else it.copy(favs = DefaultFavourites.pick(app, apps), seeded = true) }
        }
        viewModelScope.launch {
            while (true) {
                refreshCalendar()
                delay(60_000)
            }
        }
    }

    override fun onCleared() {
        wallpaperManager.removeOnColorsChangedListener(colorsListener)
        repo.close()
    }

    fun onResume() {
        wallpaperColors.value = PaletteSource.wallpaperColors(app)
        refreshGlassBackdrop()
        refreshCalendar()
    }

    fun refreshGlassBackdrop() {
        viewModelScope.launch {
            // Read the saved setting, not ui.value, which is still a placeholder at start-up
            val glassOn = store.saved.first().appearance.glass
            glassBackdrop.value = if (!glassOn) null else withContext(Dispatchers.IO) { WallpaperBackdrop.load(app) }
        }
    }

    /**
     * The wallpaper changed. If glass was showing a photo picked in the launcher and the person has
     * since set a different wallpaper elsewhere, that copy is stale: drop it.
     */
    private fun onWallpaperChanged() {
        if (!WallpaperBackdrop.isOwnChange() && WallpaperBackdrop.pickedFile(app).exists()) {
            viewModelScope.launch(Dispatchers.IO) { WallpaperBackdrop.forgetPicked(app) }
        }
        refreshGlassBackdrop()
    }

    /** Use a photo picked in the launcher as the wallpaper, and keep a copy for glass to blur. */
    fun usePickedWallpaper(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { WallpaperBackdrop.usePicked(app, uri) }
            refreshGlassBackdrop()
        }
    }

    fun refreshCalendar() {
        viewModelScope.launch { calendar.value = withContext(Dispatchers.IO) { CalendarSource.read(app) } }
    }

    // ---- icons ----

    override fun cached(key: String, sizePx: Int) = iconCache.cached(key, sizePx)

    override suspend fun load(key: String, sizePx: Int): IconCache.Icons? = withContext(Dispatchers.IO) {
        repo.activityInfo(key)?.let { iconCache.load(it, key, sizePx) }
    }

    // ---- launching ----

    fun launch(key: String) {
        ui.value.byKey[key]?.let(repo::launch)
    }

    fun openAppInfo(key: String) {
        ui.value.byKey[key]?.let(repo::openAppInfo)
    }

    fun uninstall(key: String) {
        ui.value.byKey[key]?.let { SystemActions.uninstall(app, it.packageName) }
    }

    // ---- editing ----

    private fun edit(transform: (Saved) -> Saved) {
        viewModelScope.launch { store.update(transform) }
    }

    private fun editFavs(transform: (List<Fav>) -> List<Fav>) = edit { it.copy(favs = transform(it.favs)) }

    fun addFavourite(key: String) = editFavs { FavOps.add(it, key) }
    fun removeFavourite(key: String) = editFavs { FavOps.detach(it, key) }
    fun setFavourite(key: String, favourite: Boolean) = if (favourite) addFavourite(key) else removeFavourite(key)
    /** [shownOrder] is the new order of the favourites on screen, as [FavOps.idOf] ids. */
    fun reorderFavourites(shownOrder: List<String>) = editFavs { FavOps.reorder(it, shownOrder) }
    fun moveOutOfFolder(key: String) = editFavs { FavOps.moveOutOfFolder(it, key) }
    fun moveToFolder(key: String, folderId: String) = editFavs { FavOps.moveToFolder(it, key, folderId) }
    fun newFolder(key: String, name: String) = editFavs { FavOps.newFolder(it, key, name) }
    fun createFolder(name: String, keys: List<String>) = editFavs { FavOps.createFolder(it, name, keys) }
    fun mergeIntoNewFolder(key: String, ontoKey: String, name: String) = editFavs { FavOps.mergeIntoNewFolder(it, key, ontoKey, name) }
    fun renameFolder(id: String, name: String) = editFavs { FavOps.renameFolder(it, id, name) }
    fun ungroup(id: String) = editFavs { FavOps.ungroup(it, id) }
    fun removeFolder(id: String) = editFavs { FavOps.removeFolder(it, id) }

    fun rename(key: String, name: String) = edit { s ->
        val original = ui.value.byKey[key]?.label
        val trimmed = name.trim()
        s.copy(renames = if (trimmed.isEmpty() || trimmed == original) s.renames - key else s.renames + (key to trimmed))
    }

    fun hide(key: String) = edit { it.copy(hidden = it.hidden + key) }
    fun unhide(key: String) = edit { it.copy(hidden = it.hidden - key) }
    fun setAppearance(appearance: Appearance) {
        val glassChanged = appearance.glass != ui.value.appearance.glass
        viewModelScope.launch {
            store.update { it.copy(appearance = appearance) }
            if (glassChanged) refreshGlassBackdrop()
        }
    }
}
