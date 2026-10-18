package io.github.peteroomen.keyboard

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit

enum class LookChoice { SYSTEM, LIGHT, DARK }

/**
 * How readily a space puts in a fix, as AOSP's keyboard offers it: the score a fix needs,
 * normalised against what was typed. [OFF] never fixes.
 */
enum class AutocorrectLevel(val threshold: Float) {
    OFF(Float.MAX_VALUE),
    MODEST(0.185f),
    AGGRESSIVE(0.067f),
    VERY_AGGRESSIVE(Float.NEGATIVE_INFINITY),
}

/** The bubble that shows the letter under your finger: none, key-sized, or tall and wide like Gboard's. */
enum class KeyPopup { OFF, NORMAL, LARGE }

/** Where the Enter key and other highlights get their colour. */
enum class AccentChoice { APP, SYSTEM }

/** The keyboard's few settings, read each time it opens. */
class KeyboardPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    var vibrate: Boolean
        get() = prefs.getBoolean("vibrate", true)
        set(value) = prefs.edit { putBoolean("vibrate", value) }

    var sound: Boolean
        get() = prefs.getBoolean("sound", false)
        set(value) = prefs.edit { putBoolean("sound", value) }

    /** Suggestions from the spell checker as you type */
    var suggestions: Boolean
        get() = prefs.getBoolean("suggestions", true)
        set(value) = prefs.edit { putBoolean("suggestions", value) }

    /** How readily a fix goes in on space (it was an on/off switch: off stays off) */
    var autocorrect: AutocorrectLevel
        get() = prefs.getString("autocorrect_level", null)?.let { runCatching { AutocorrectLevel.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean("autocorrect", true)) AutocorrectLevel.AGGRESSIVE else AutocorrectLevel.OFF
        set(value) = prefs.edit { putString("autocorrect_level", value.name) }

    /** An emoji key on the bottom row, beside ?123 */
    var emojiKey: Boolean
        get() = prefs.getBoolean("emoji_key", true)
        set(value) = prefs.edit { putBoolean("emoji_key", value) }

    var keyPopup: KeyPopup
        get() = runCatching { KeyPopup.valueOf(prefs.getString("key_popup", null) ?: "") }.getOrDefault(KeyPopup.LARGE)
        set(value) = prefs.edit { putString("key_popup", value.name) }

    var accent: AccentChoice
        get() = runCatching { AccentChoice.valueOf(prefs.getString("accent", null) ?: "") }.getOrDefault(AccentChoice.APP)
        set(value) = prefs.edit { putString("accent", value.name) }

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
