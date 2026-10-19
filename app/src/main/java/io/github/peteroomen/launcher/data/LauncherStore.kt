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
import io.github.peteroomen.launcher.model.AppUsage
import io.github.peteroomen.launcher.model.Fav
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [GLASS]: the colour icon frosted under glass, like the music card's album art, with the app's glyph sharp on top. */
enum class IconStyle { THEMED, COLOUR, GREYSCALE, GLASS }
enum class IconShape { ROUNDED, CIRCLE, SQUARE }
enum class TextShadow { OFF, SOFT, MEDIUM, STRONG }
enum class TextTone { AUTO, LIGHT, DARK }
enum class LauncherFont { SYSTEM, CONDENSED, SERIF, MONO }
enum class ClockSize(val sp: Int) { SMALL(44), MEDIUM(56), LARGE(72) }
enum class ClockWeight { LIGHT, REGULAR, BOLD }
enum class ClockStyle { DIGITAL, ANALOG }
/** App names in the A–Z list, search and favourites. */
enum class AppTextSize(val sp: Int) { SMALL(16), MEDIUM(18), LARGE(20) }
enum class AppTextWeight { LIGHT, REGULAR, MEDIUM }
/** The letter headings in the A–Z list. */
enum class LetterSize(val sp: Int) { SMALL(14), MEDIUM(20), LARGE(28) }
enum class ClockAlign { LEFT, CENTER, RIGHT }
/** A panel behind the clock, in any of the panel looks, whatever the Panels setting is. */
enum class ClockBackground { NONE, GLASS, BLUR, FLAT, BEZEL }

/** The style for app names, wherever they're listed. */
val Appearance.appNameWeight get() = when (appTextWeight) {
    AppTextWeight.LIGHT -> androidx.compose.ui.text.font.FontWeight.Light
    AppTextWeight.REGULAR -> androidx.compose.ui.text.font.FontWeight.Normal
    AppTextWeight.MEDIUM -> androidx.compose.ui.text.font.FontWeight.Medium
}

/** Whether anything on screen shows the wallpaper through glass, so its copies need loading. */
val Appearance.needsGlassBackdrop get() =
    glass || clockBackground == ClockBackground.GLASS || clockBackground == ClockBackground.BLUR || clockBackground == ClockBackground.BEZEL
enum class FavouritesLayout { COLUMN, PIE }
/**
 * Clear: barely blurred, the whole panel bends what's behind like a lens (like Apple's). Frosted:
 * blurred and tinted. Bezel: slightly blurred under a darker middle, framed by a lighter band just
 * inside a bright rim, like a pane of glass set in a glass bezel.
 */
enum class GlassStyle { CLEAR, FROSTED, BEZEL }
/** How search and the A–Z list are shown: straight on the wallpaper, or on a sheet from the bottom. */
enum class ListStyle { WALLPAPER, PANEL }
/** Something behind the text to lift it off busy wallpapers. */
enum class Backdrop { NONE, LEFT_FADE, DIM }

/**
 * Accent colour: taken from the wallpaper, a fixed hue, or plain white/black ([MONO]). The Loud
 * palettes, one for each of the Loud launcher's palettes, go fully Loud: themed icons get a fixed opaque [tile] and
 * [glyph] colour, and glass gives way to solid outlined panels with hard shadows, filled [bright]
 * under dark text or [deep] under light text.
 */
enum class Accent(val hue: Float?, val tile: Long? = null, val glyph: Long? = null, val bright: Long? = null, val deep: Long? = null) {
    WALLPAPER(null), AMBER(36f), CORAL(10f), PINK(330f), VIOLET(265f), BLUE(212f), TEAL(172f), GREEN(128f), MONO(null),
    // Appended so stored names never change meaning; listed in Loud's order in settings
    POP_GARDEN(330f, 0xFF4BE37A, 0xFF171714, 0xFFFF9ACB, 0xFF0E3B2C),
    BABY_BLUE(205f, 0xFF9FD8FF, 0xFFAD1457, 0xFFD6EEFF, 0xFF123A5C),
    RAVE(85f, 0xFFFF6FB5, 0xFF0E3B2C, 0xFFB8F25C, 0xFF0E3B2C),
    TAXI(54f, 0xFFFFE600, 0xFF171714, 0xFFF5F0E5, 0xFF171714),
    RISO(338f, 0xFFFF88B3, 0xFF171714, 0xFFFFF5EC, 0xFF171714),
    ACID(75f, 0xFFD1F555, 0xFF171714, 0xFFF0F1DF, 0xFF171714),
    NIGHT_BRUTAL(72f, 0xFFE0FC67, 0xFF171714, 0xFFE8E5DC, 0xFF202023),
    CANDY(45f, 0xFFF8DE8B, 0xFF171714, 0xFFFFF2E8, 0xFF171714);

    val loud get() = tile != null
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
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val appTextSize: AppTextSize = AppTextSize.SMALL,
    val appTextWeight: AppTextWeight = AppTextWeight.REGULAR,
    /** Font for app names; null follows [font]. */
    val appFont: LauncherFont? = null,
    val letterSize: LetterSize = LetterSize.MEDIUM,
    /** Font for the A–Z list's letter headings; null follows [font]. */
    val letterFont: LauncherFont? = null,
    val clockAlign: ClockAlign = ClockAlign.LEFT,
    val clockBackground: ClockBackground = ClockBackground.NONE,
    val backdrop: Backdrop = Backdrop.NONE,
    /** Panels (icon tiles, folders, music card, search, pie) drawn as liquid glass. */
    val glass: Boolean = false,
    val glassStyle: GlassStyle = GlassStyle.CLEAR,
    val listStyle: ListStyle = ListStyle.PANEL,
    /** Keep the home screen upright even when the phone is turned sideways. */
    val lockPortrait: Boolean = true,
    /** Hide the A–Z letters until the right edge is touched. */
    val hideLetters: Boolean = false,
)

/** Everything the launcher remembers. */
data class Saved(
    val favs: List<Fav> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    /** How often each app is opened, and opened from search, for suggestions in search. */
    val usage: Map<String, AppUsage> = emptyMap(),
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
            usage = FavJson.decodeUsage(p[USAGE]),
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
                clockStyle = p.enumOf(CLOCK_STYLE, d.clockStyle),
                appTextSize = p.enumOf(APP_TEXT_SIZE, d.appTextSize),
                appTextWeight = p.enumOf(APP_TEXT_WEIGHT, d.appTextWeight),
                appFont = p[APP_FONT]?.let { name -> LauncherFont.entries.firstOrNull { it.name == name } },
                letterSize = p.enumOf(LETTER_SIZE, d.letterSize),
                letterFont = p[LETTER_FONT]?.let { name -> LauncherFont.entries.firstOrNull { it.name == name } },
                clockAlign = p.enumOf(CLOCK_ALIGN, d.clockAlign),
                clockBackground = p.enumOf(CLOCK_BACKGROUND, d.clockBackground),
                backdrop = p.enumOf(BACKDROP, d.backdrop),
                lockPortrait = p[LOCK_PORTRAIT] ?: d.lockPortrait,
                hideLetters = p[HIDE_LETTERS] ?: d.hideLetters,
                glass = p[GLASS] ?: d.glass,
                glassStyle = p.enumOf(GLASS_STYLE, d.glassStyle),
                listStyle = p.enumOf(LIST_STYLE, d.listStyle),
            ),
            seeded = p[SEEDED] ?: false,
        )
    }

    private fun write(p: MutablePreferences, s: Saved) {
        val a = s.appearance
        p[FAVS] = FavJson.encode(s.favs)
        p[HIDDEN] = s.hidden
        p[RENAMES] = FavJson.encodeMap(s.renames)
        p[USAGE] = FavJson.encodeUsage(s.usage)
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
        p[CLOCK_STYLE] = a.clockStyle.name
        p[APP_TEXT_SIZE] = a.appTextSize.name
        p[APP_TEXT_WEIGHT] = a.appTextWeight.name
        a.appFont?.let { p[APP_FONT] = it.name } ?: p.remove(APP_FONT)
        p[LETTER_SIZE] = a.letterSize.name
        a.letterFont?.let { p[LETTER_FONT] = it.name } ?: p.remove(LETTER_FONT)
        p[CLOCK_ALIGN] = a.clockAlign.name
        p[CLOCK_BACKGROUND] = a.clockBackground.name
        p[BACKDROP] = a.backdrop.name
        p[LOCK_PORTRAIT] = a.lockPortrait
        p[HIDE_LETTERS] = a.hideLetters
        p[GLASS] = a.glass
        p[GLASS_STYLE] = a.glassStyle.name
        p[LIST_STYLE] = a.listStyle.name
        p[SEEDED] = s.seeded
    }

    private companion object {
        val FAVS = stringPreferencesKey("favs")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val RENAMES = stringPreferencesKey("renames")
        val USAGE = stringPreferencesKey("usage")
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
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val APP_TEXT_SIZE = stringPreferencesKey("app_text_size")
        val APP_TEXT_WEIGHT = stringPreferencesKey("app_text_weight")
        val APP_FONT = stringPreferencesKey("app_font")
        val LETTER_SIZE = stringPreferencesKey("letter_size")
        val LETTER_FONT = stringPreferencesKey("letter_font")
        val CLOCK_ALIGN = stringPreferencesKey("clock_align")
        val CLOCK_BACKGROUND = stringPreferencesKey("clock_background")
        val BACKDROP = stringPreferencesKey("backdrop")
        val LOCK_PORTRAIT = booleanPreferencesKey("lock_portrait")
        val HIDE_LETTERS = booleanPreferencesKey("hide_letters")
        val GLASS = booleanPreferencesKey("glass")
        val GLASS_STYLE = stringPreferencesKey("glass_style")
        val LIST_STYLE = stringPreferencesKey("list_style")
        val SEEDED = booleanPreferencesKey("seeded")
    }
}
