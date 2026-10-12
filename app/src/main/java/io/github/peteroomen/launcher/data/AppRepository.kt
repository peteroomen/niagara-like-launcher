package io.github.peteroomen.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
)

/** Every launchable app for this user, kept up to date as apps are installed, updated and removed. */
class AppRepository(context: Context, private val scope: CoroutineScope) {

    private val packageManager = context.packageManager

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    @Volatile private var infos: Map<String, LauncherActivityInfo> = emptyMap()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun close() = launcherApps.unregisterCallback(callback)

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val list = launcherApps.getActivityList(null, Process.myUserHandle())
            infos = list.associateBy { it.componentName.flattenToShortString() }
            val installed = HashMap<String, Long>()
            _apps.value = list.map {
                val pkg = it.componentName.packageName
                val at = installed.getOrPut(pkg) { runCatching { packageManager.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L) }
                AppInfo(it.componentName.flattenToShortString(), it.label.toString(), pkg, it.componentName, it.user, at)
            }.sortedBy { it.label.lowercase() }
        }
    }

    fun activityInfo(key: String): LauncherActivityInfo? = infos[key]

    fun launch(app: AppInfo) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, null, null) }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }
}
