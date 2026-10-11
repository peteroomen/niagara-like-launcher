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

enum class IconStyle { THEMED, COLOUR, GREYSCALE }
enum class IconShape { ROUNDED, CIRCLE, SQUARE }
enum class TextShadow { OFF, SOFT, MEDIUM, STRONG }
enum class TextTone { AUTO, LIGHT, DARK }
enum class LauncherFont { SYSTEM, CONDENSED, SERIF, MONO }
enum class ClockSize(val sp: Int) { SMALL(44), MEDIUM(56), LARGE(72) }
enum class ClockWeight { LIGHT, REGULAR, BOLD }
enum class FavouritesLayout { COLUMN, PIE }
/** Something behind the text to lift it off busy wallpapers. */
enum class Backdrop { NONE, LEFT_FADE, DIM }

/** Accent colour: taken from the wallpaper, a fixed hue, or plain white/black ([MONO]). */
enum class Accent(val hue: Float?) {
    WALLPAPER(null), AMBER(36f), CORAL(10f), PINK(330f), VIOLET(265f), BLUE(212f), TEAL(172f), GREEN(128f), MONO(null)
}

data class Appearance(
    /** A column of icons on the left, or a pie that opens where you hold the screen. */
    val favouritesLayout: FavouritesLayout = FavouritesLayout.COLUMN,
    val showNames: Boolean = false,
    val iconStyle: IconStyle = IconStyle.THEMED,
    val iconSizeDp: Int = 44,
    val iconShape: IconShape = IconShape.ROUNDED,
    val textShadow: TextShadow = TextShadow.SOFT,
    val textTone: TextTone = TextTone.AUTO,
    val accent: Accent = Accent.WALLPAPER,
    val font: LauncherFont = LauncherFont.SYSTEM,
    val clockSize: ClockSize = ClockSize.MEDIUM,
    val clockWeight: ClockWeight = ClockWeight.LIGHT,
    val backdrop: Backdrop = Backdrop.NONE,
    /** Panels (icon tiles, folders, music card, search, pie) drawn as liquid glass. */
    val glass: Boolean = false,
    /** With glass: bend the wallpaper at panel edges like a lens (Android 13+). */
    val glassBend: Boolean = true,
    /** Keep the home screen upright even when the phone is turned sideways. */
    val lockPortrait: Boolean = true,
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

private inline fun <reified T : Enum<T>> Preferences.enumOf(key: Preferences.Key<String>, default: T): T =
    enumValues<T>().firstOrNull { it.name == this[key] } ?: default

class LauncherStore(private val context: Context) {

    val saved: Flow<Saved> = context.dataStore.data.map(::read)

    suspend fun update(transform: (Saved) -> Saved) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): Saved {
        val d = Appearance()
        return Saved(
            favs = FavJson.decode(p[FAVS]),
            hidden = p[HIDDEN] ?: emptySet(),
            renames = FavJson.decodeMap(p[RENAMES]),
            appearance = Appearance(
                favouritesLayout = p.enumOf(FAVOURITES_LAYOUT, d.favouritesLayout),
                showNames = p[SHOW_NAMES] ?: d.showNames,
                iconStyle = p.enumOf(ICON_STYLE, d.iconStyle),
                iconSizeDp = p[ICON_SIZE] ?: d.iconSizeDp,
                iconShape = p.enumOf(ICON_SHAPE, d.iconShape),
                textShadow = p.enumOf(TEXT_SHADOW, d.textShadow),
                textTone = p.enumOf(TEXT_TONE, d.textTone),
                accent = p.enumOf(ACCENT, d.accent),
                font = p.enumOf(FONT, d.font),
                clockSize = p.enumOf(CLOCK_SIZE, d.clockSize),
                clockWeight = p.enumOf(CLOCK_WEIGHT, d.clockWeight),
                backdrop = p.enumOf(BACKDROP, d.backdrop),
                lockPortrait = p[LOCK_PORTRAIT] ?: d.lockPortrait,
                glass = p[GLASS] ?: d.glass,
                glassBend = p[GLASS_BEND] ?: d.glassBend,
            ),
            seeded = p[SEEDED] ?: false,
        )
    }

    private fun write(p: MutablePreferences, s: Saved) {
        val a = s.appearance
        p[FAVS] = FavJson.encode(s.favs)
        p[HIDDEN] = s.hidden
        p[RENAMES] = FavJson.encodeMap(s.renames)
        p[FAVOURITES_LAYOUT] = a.favouritesLayout.name
        p[SHOW_NAMES] = a.showNames
        p[ICON_STYLE] = a.iconStyle.name
        p[ICON_SIZE] = a.iconSizeDp
        p[ICON_SHAPE] = a.iconShape.name
        p[TEXT_SHADOW] = a.textShadow.name
        p[TEXT_TONE] = a.textTone.name
        p[ACCENT] = a.accent.name
        p[FONT] = a.font.name
        p[CLOCK_SIZE] = a.clockSize.name
        p[CLOCK_WEIGHT] = a.clockWeight.name
        p[BACKDROP] = a.backdrop.name
        p[LOCK_PORTRAIT] = a.lockPortrait
        p[GLASS] = a.glass
        p[GLASS_BEND] = a.glassBend
        p[SEEDED] = s.seeded
    }

    private companion object {
        val FAVS = stringPreferencesKey("favs")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val RENAMES = stringPreferencesKey("renames")
        val FAVOURITES_LAYOUT = stringPreferencesKey("favourites_layout")
        val SHOW_NAMES = booleanPreferencesKey("show_names")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val ICON_SIZE = intPreferencesKey("icon_size")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val TEXT_SHADOW = stringPreferencesKey("text_shadow")
        val TEXT_TONE = stringPreferencesKey("text_tone")
        val ACCENT = stringPreferencesKey("accent")
        val FONT = stringPreferencesKey("font")
        val CLOCK_SIZE = stringPreferencesKey("clock_size")
        val CLOCK_WEIGHT = stringPreferencesKey("clock_weight")
        val BACKDROP = stringPreferencesKey("backdrop")
        val LOCK_PORTRAIT = booleanPreferencesKey("lock_portrait")
        val GLASS = booleanPreferencesKey("glass")
        val GLASS_BEND = booleanPreferencesKey("glass_bend")
        val SEEDED = booleanPreferencesKey("seeded")
    }
}
