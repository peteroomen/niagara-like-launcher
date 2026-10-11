package io.github.peteroomen.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import io.github.peteroomen.launcher.model.Fav

/** First-run favourites: the default phone, messages, camera, browser, maps and music apps. */
object DefaultFavourites {

    fun pick(context: Context, apps: List<AppInfo>): List<Fav> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
        )
        val pm = context.packageManager
        return intents
            .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg }?.key }
            .distinct()
            .map(Fav::App)
    }
}
