package io.github.peteroomen.keyboard

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit

enum class LookChoice { SYSTEM, LIGHT, DARK }

/** The keyboard's few settings, read each time it opens. */
class KeyboardPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    var vibrate: Boolean
        get() = prefs.getBoolean("vibrate", true)
        set(value) = prefs.edit { putBoolean("vibrate", value) }

    var sound: Boolean
        get() = prefs.getBoolean("sound", false)
        set(value) = prefs.edit { putBoolean("sound", value) }

    var look: LookChoice
        get() = runCatching { LookChoice.valueOf(prefs.getString("look", null) ?: "") }.getOrDefault(LookChoice.SYSTEM)
        set(value) = prefs.edit { putString("look", value.name) }

    /** Emoji typed lately, most recent first */
    var recentEmoji: List<String>
        get() = prefs.getString("recent_emoji", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        set(value) = prefs.edit { putString("recent_emoji", value.joinToString("\n")) }

    fun dark(context: Context) = when (look) {
        LookChoice.DARK -> true
        LookChoice.LIGHT -> false
        LookChoice.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
