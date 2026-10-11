package io.github.peteroomen.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.peteroomen.launcher.model.Fav
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class IconStyle { THEMED, COLOUR }

data class Appearance(
    val showNames: Boolean = false,
    val iconStyle: IconStyle = IconStyle.THEMED,
    val iconSizeDp: Int = 44,
)

/** Everything the launcher remembers. */
data class Saved(
    val favs: List<Fav> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val appearance: Appearance = Appearance(),
    /** Whether default favourites have been picked on first run. */
    val seeded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "launcher")

class LauncherStore(private val context: Context) {

    val saved: Flow<Saved> = context.dataStore.data.map(::read)

    suspend fun update(transform: (Saved) -> Saved) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences) = Saved(
        favs = FavJson.decode(p[FAVS]),
        hidden = p[HIDDEN] ?: emptySet(),
        renames = FavJson.decodeMap(p[RENAMES]),
        appearance = Appearance(
            showNames = p[SHOW_NAMES] ?: false,
            iconStyle = IconStyle.entries.firstOrNull { it.name == p[ICON_STYLE] } ?: IconStyle.THEMED,
            iconSizeDp = p[ICON_SIZE] ?: 44,
        ),
        seeded = p[SEEDED] ?: false,
    )

    private fun write(p: MutablePreferences, s: Saved) {
        p[FAVS] = FavJson.encode(s.favs)
        p[HIDDEN] = s.hidden
        p[RENAMES] = FavJson.encodeMap(s.renames)
        p[SHOW_NAMES] = s.appearance.showNames
        p[ICON_STYLE] = s.appearance.iconStyle.name
        p[ICON_SIZE] = s.appearance.iconSizeDp
        p[SEEDED] = s.seeded
    }

    private companion object {
        val FAVS = stringPreferencesKey("favs")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val RENAMES = stringPreferencesKey("renames")
        val SHOW_NAMES = booleanPreferencesKey("show_names")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val ICON_SIZE = intPreferencesKey("icon_size")
        val SEEDED = booleanPreferencesKey("seeded")
    }
}
