package io.github.peteroomen.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    /** Component name, e.g. `com.whatsapp/.Main`. Used as the app's id everywhere. */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    /** When the app was first installed, for "recently installed" in search. */
    val installedAt: Long = 0L,
    /** Set for a pinned shortcut (e.g. a website added from Chrome) rather than an app. */
    val shortcutId: String? = null,
)

/**
 * Every launchable app for this user, plus shortcuts pinned to the home screen (websites added from
 * Chrome, a Maps destination...), kept up to date as apps are installed, updated and removed.
 */
class AppRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val packageManager = context.packageManager

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    @Volatile private var infos: Map<String, LauncherActivityInfo> = emptyMap()
    @Volatile private var shortcuts: Map<String, ShortcutInfo> = emptyMap()

    private val _added = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Package names as they're installed. */
    val added: SharedFlow<String> = _added

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            packageName?.let { _added.tryEmit(it) }
            refresh()
        }
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun close() = launcherApps.unregisterCallback(callback)

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val list = launcherApps.getActivityList(null, Process.myUserHandle())
            val pinned = pinnedShortcuts()
            infos = list.associateBy { it.componentName.flattenToShortString() }
            shortcuts = pinned.associateBy { shortcutKey(it.`package`, it.id) }
            _apps.value = (toApps(list, packageManager) + pinned.map(::toApp)).sortedBy { it.label.lowercase() }
        }
    }

    /** Only the default home app may read pinned shortcuts; otherwise there are none to show. */
    private fun pinnedShortcuts(): List<ShortcutInfo> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = ShortcutQuery().setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
        return runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()) }.getOrNull().orEmpty().filter { it.isEnabled }
    }

    private fun toApp(s: ShortcutInfo) = AppInfo(
        key = shortcutKey(s.`package`, s.id),
        label = (s.shortLabel ?: s.longLabel ?: s.`package`).toString(),
        packageName = s.`package`,
        component = s.activity ?: ComponentName(s.`package`, ""),
        user = s.userHandle,
        installedAt = s.lastChangedTimestamp,
        shortcutId = s.id,
    )

    companion object {
        private const val SHORTCUT_PREFIX = "shortcut:"

        /** Key for a pinned shortcut. App keys are component names, so they never start with this. */
        fun shortcutKey(packageName: String, id: String) = "$SHORTCUT_PREFIX$packageName/$id"

        fun isShortcut(key: String) = key.startsWith(SHORTCUT_PREFIX)

        /** Packages Chrome-family browsers create when a website is installed as an app. */
        fun isWebApk(packageName: String) = packageName.contains(".webapk")

        fun toApps(list: List<LauncherActivityInfo>, packageManager: PackageManager): List<AppInfo> {
            val installed = HashMap<String, Long>()
            return list.map {
                val pkg = it.componentName.packageName
                val at = installed.getOrPut(pkg) { runCatching { packageManager.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L) }
                AppInfo(it.componentName.flattenToShortString(), it.label.toString(), pkg, it.componentName, it.user, at)
            }.sortedBy { it.label.lowercase() }
        }
    }

    fun activityInfo(key: String): LauncherActivityInfo? = infos[key]

    fun shortcutInfo(key: String): ShortcutInfo? = shortcuts[key]

    fun shortcutIcon(info: ShortcutInfo): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(info, appContext.resources.displayMetrics.densityDpi) }.getOrNull()

    fun launch(app: AppInfo) {
        runCatching {
            val id = app.shortcutId
            if (id != null) launcherApps.startShortcut(app.packageName, id, null, null, app.user)
            else launcherApps.startMainActivity(app.component, app.user, null, null)
        }
    }

    /** For a shortcut, the details of the app that made it (Chrome for a website). */
    fun openAppInfo(app: AppInfo) {
        runCatching {
            if (app.shortcutId == null) launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
            else appContext.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Takes a pinned shortcut off the home screen. The app that made it stays installed. */
    fun unpin(app: AppInfo) {
        val id = app.shortcutId ?: return
        val keep = shortcuts.values.filter { it.`package` == app.packageName && it.id != id }.map { it.id }
        runCatching { launcherApps.pinShortcuts(app.packageName, keep, app.user) }
        refresh()
    }
}
