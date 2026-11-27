package io.github.peteroomen.launcher.system

import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.AppRepository
import io.github.peteroomen.launcher.data.LauncherStore
import io.github.peteroomen.launcher.model.FavOps
import kotlinx.coroutines.launch

/**
 * Handles "Add to home screen" from other apps (Chrome, Maps...). Android only offers that, and
 * Chrome only shows "Install app", when the default home app has an activity for
 * CONFIRM_PIN_SHORTCUT. The asking app has already confirmed with the person, so this accepts
 * straight away and puts the shortcut in favourites. It draws nothing.
 */
class PinShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = runCatching { getSystemService(LauncherApps::class.java).getPinItemRequest(intent) }.getOrNull()
        val shortcut = request?.shortcutInfo
        if (request == null || !request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ||
            shortcut == null || !runCatching { request.accept() }.getOrDefault(false)
        ) {
            finish()
            return
        }
        val key = AppRepository.shortcutKey(shortcut.`package`, shortcut.id)
        val label = (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString()
        lifecycleScope.launch {
            LauncherStore(applicationContext).update { it.copy(favs = FavOps.add(it.favs, key)) }
            if (label.isNotBlank()) Toast.makeText(applicationContext, getString(R.string.added_to_favourites, label), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
