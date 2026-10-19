package io.github.peteroomen.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.launcher.data.Accent
import io.github.peteroomen.launcher.data.AppTextSize
import io.github.peteroomen.launcher.data.AppTextWeight
import io.github.peteroomen.launcher.data.Appearance
import io.github.peteroomen.launcher.data.Backdrop
import io.github.peteroomen.launcher.data.ClockAlign
import io.github.peteroomen.launcher.data.ClockBackground
import io.github.peteroomen.launcher.data.ClockSize
import io.github.peteroomen.launcher.data.ClockStyle
import io.github.peteroomen.launcher.data.ClockWeight
import io.github.peteroomen.launcher.data.FavouritesLayout
import io.github.peteroomen.launcher.data.GlassStyle
import io.github.peteroomen.launcher.data.IconShape
import io.github.peteroomen.launcher.data.IconStyle
import io.github.peteroomen.launcher.data.LauncherFont
import io.github.peteroomen.launcher.data.LetterSize
import io.github.peteroomen.launcher.data.ListStyle
import io.github.peteroomen.launcher.data.TextShadow
import io.github.peteroomen.launcher.data.TextTone
import io.github.peteroomen.launcher.data.needsGlassBackdrop
import io.github.peteroomen.launcher.system.SystemActions
import io.github.peteroomen.launcher.system.WallpaperBackdrop
import io.github.peteroomen.launcher.ui.openNotificationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { SettingsScreen(viewModel) }
            }
        }
    }
}

/** The settings pages, Android style: a list of categories, each opening its own page. */
private enum class Page(val title: Int, val summary: Int, val icon: Int) {
    HOME(R.string.page_home, R.string.page_home_summary, R.drawable.ic_home),
    CLOCK(R.string.page_clock, R.string.page_clock_summary, R.drawable.ic_schedule),
    TEXT(R.string.page_text, R.string.page_text_summary, R.drawable.ic_text_fields),
    ICONS(R.string.page_icons, R.string.page_icons_summary, R.drawable.ic_apps),
    LOOK(R.string.page_look, R.string.page_look_summary, R.drawable.ic_palette),
    LIST(R.string.page_list, R.string.page_list_summary, R.drawable.ic_list),
    SETUP(R.string.page_setup, R.string.page_setup_summary, R.drawable.ic_check_circle),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val state by vm.ui.collectAsStateWithLifecycle()
    // Re-check permissions and roles whenever we come back from system settings
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val roleManager = context.getSystemService(RoleManager::class.java)
    val isHome = remember(resumes) { roleManager.isRoleHeld(RoleManager.ROLE_HOME) }
    val hasNotificationAccess = remember(resumes) { context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) }
    val setupDone = isHome && hasNotificationAccess

    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    BackHandler(enabled = page != null) { page = null }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(page?.title ?: R.string.settings_name)) },
                navigationIcon = {
                    if (page != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        // Each page starts at the top
        val pageScroll = key(page) { rememberScrollState() }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(pageScroll)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            val a = state.appearance
            fun set(change: Appearance) = vm.setAppearance(change)
            when (page) {
                null -> {
                    if (!setupDone) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.finish_setup)) },
                            supportingContent = { Text(stringResource(R.string.finish_setup_summary)) },
                            leadingContent = { Icon(painterResource(R.drawable.ic_error), null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { page = Page.SETUP },
                        )
                        HorizontalDivider()
                    }
                    Page.entries.forEach { p ->
                        ListItem(
                            headlineContent = { Text(stringResource(p.title)) },
                            supportingContent = { Text(stringResource(p.summary)) },
                            leadingContent = { Icon(painterResource(p.icon), null) },
                            modifier = Modifier.clickable { page = p },
                        )
                    }
                }

                Page.HOME -> {
                    ChoiceItem(stringResource(R.string.favourites_layout), a.favouritesLayout, listOf(
                        FavouritesLayout.COLUMN to R.string.layout_column, FavouritesLayout.PIE to R.string.layout_pie,
                    )) { set(a.copy(favouritesLayout = it)) }
                    SwitchItem(stringResource(R.string.show_names), null, a.showNames) { set(a.copy(showNames = it)) }
                    SwitchItem(stringResource(R.string.lock_portrait), null, a.lockPortrait) { set(a.copy(lockPortrait = it)) }
                    SectionHeader(stringResource(R.string.readability))
                    ChoiceItem(stringResource(R.string.backdrop), a.backdrop, listOf(
                        Backdrop.NONE to R.string.backdrop_none, Backdrop.LEFT_FADE to R.string.backdrop_left, Backdrop.DIM to R.string.backdrop_dim,
                    )) { set(a.copy(backdrop = it)) }
                }

                Page.CLOCK -> {
                    ChoiceItem(stringResource(R.string.clock_style), a.clockStyle, listOf(
                        ClockStyle.DIGITAL to R.string.clock_digital, ClockStyle.ANALOG to R.string.clock_analog,
                    )) { set(a.copy(clockStyle = it)) }
                    ChoiceItem(stringResource(R.string.clock_size), a.clockSize, listOf(
                        ClockSize.SMALL to R.string.size_small, ClockSize.MEDIUM to R.string.size_medium, ClockSize.LARGE to R.string.size_large,
                    )) { set(a.copy(clockSize = it)) }
                    ChoiceItem(stringResource(R.string.clock_weight), a.clockWeight, listOf(
                        ClockWeight.LIGHT to R.string.weight_light, ClockWeight.REGULAR to R.string.weight_regular, ClockWeight.BOLD to R.string.weight_bold,
                    )) { set(a.copy(clockWeight = it)) }
                    ChoiceItem(stringResource(R.string.clock_align), a.clockAlign, listOf(
                        ClockAlign.LEFT to R.string.align_left, ClockAlign.CENTER to R.string.align_center, ClockAlign.RIGHT to R.string.align_right,
                    )) { set(a.copy(clockAlign = it)) }
                    ChoiceItem(stringResource(R.string.clock_background), a.clockBackground, listOfNotNull(
                        ClockBackground.NONE to R.string.clock_background_none,
                        if (Build.VERSION.SDK_INT >= 33) ClockBackground.GLASS to R.string.panels_glass else null,
                        ClockBackground.BLUR to R.string.panels_blur,
                        ClockBackground.FLAT to R.string.panels_flat,
                        ClockBackground.BEZEL to R.string.panels_bezel,
                    )) { set(a.copy(clockBackground = it)) }
                }

                Page.TEXT -> {
                    ChoiceItem(stringResource(R.string.font), a.font, listOf(
                        LauncherFont.SYSTEM to R.string.font_system, LauncherFont.CONDENSED to R.string.font_condensed,
                        LauncherFont.SERIF to R.string.font_serif, LauncherFont.MONO to R.string.font_mono,
                    )) { set(a.copy(font = it)) }
                    SectionHeader(stringResource(R.string.app_names))
                    ChoiceItem(stringResource(R.string.app_text_size), a.appTextSize, listOf(
                        AppTextSize.SMALL to R.string.size_small, AppTextSize.MEDIUM to R.string.size_medium, AppTextSize.LARGE to R.string.size_large,
                    )) { set(a.copy(appTextSize = it)) }
                    ChoiceItem(stringResource(R.string.app_text_weight), a.appTextWeight, listOf(
                        AppTextWeight.LIGHT to R.string.weight_light, AppTextWeight.REGULAR to R.string.weight_regular, AppTextWeight.MEDIUM to R.string.weight_medium,
                    )) { set(a.copy(appTextWeight = it)) }
                    ChoiceItem(stringResource(R.string.app_font), a.appFont, listOf(
                        null to R.string.font_same,
                        LauncherFont.SYSTEM to R.string.font_system, LauncherFont.CONDENSED to R.string.font_condensed,
                        LauncherFont.SERIF to R.string.font_serif, LauncherFont.MONO to R.string.font_mono,
                    )) { set(a.copy(appFont = it)) }
                    Footer(stringResource(R.string.app_names_footer))
                    SectionHeader(stringResource(R.string.letter_headings))
                    ChoiceItem(stringResource(R.string.app_text_size), a.letterSize, listOf(
                        LetterSize.SMALL to R.string.size_small, LetterSize.MEDIUM to R.string.size_medium, LetterSize.LARGE to R.string.size_large,
                    )) { set(a.copy(letterSize = it)) }
                    ChoiceItem(stringResource(R.string.app_font), a.letterFont, listOf(
                        null to R.string.font_same,
                        LauncherFont.SYSTEM to R.string.font_system, LauncherFont.CONDENSED to R.string.font_condensed,
                        LauncherFont.SERIF to R.string.font_serif, LauncherFont.MONO to R.string.font_mono,
                    )) { set(a.copy(letterFont = it)) }
                    Footer(stringResource(R.string.letter_headings_footer))
                    SectionHeader(stringResource(R.string.readability))
                    ChoiceItem(stringResource(R.string.text_colour), a.textTone, listOf(
                        TextTone.AUTO to R.string.tone_auto, TextTone.LIGHT to R.string.tone_light, TextTone.DARK to R.string.tone_dark,
                    )) { set(a.copy(textTone = it)) }
                    ChoiceItem(stringResource(R.string.text_shadow), a.textShadow, listOf(
                        TextShadow.OFF to R.string.shadow_off, TextShadow.SOFT to R.string.shadow_soft,
                        TextShadow.MEDIUM to R.string.shadow_medium, TextShadow.STRONG to R.string.shadow_strong,
                    )) { set(a.copy(textShadow = it)) }
                }

                Page.ICONS -> {
                    ChoiceItem(stringResource(R.string.icon_style), a.iconStyle, listOf(
                        IconStyle.THEMED to R.string.icons_themed, IconStyle.COLOUR to R.string.icons_colour, IconStyle.GREYSCALE to R.string.icons_greyscale,
                        IconStyle.GLASS to R.string.icons_glass,
                    )) { set(a.copy(iconStyle = it)) }
                    ChoiceItem(stringResource(R.string.icon_shape), a.iconShape, listOf(
                        IconShape.ROUNDED to R.string.shape_rounded, IconShape.CIRCLE to R.string.shape_circle, IconShape.SQUARE to R.string.shape_square,
                    )) { set(a.copy(iconShape = it)) }
                    ChoiceItem(stringResource(R.string.icon_size), a.iconSizeDp, listOf(
                        36 to R.string.size_small, 44 to R.string.size_medium, 52 to R.string.size_large,
                    )) { set(a.copy(iconSizeDp = it)) }
                }

                Page.LOOK -> {
                    ChoiceItem(stringResource(R.string.accent_colour), a.accent, listOf(
                        Accent.WALLPAPER to R.string.accent_wallpaper, Accent.AMBER to R.string.accent_amber, Accent.CORAL to R.string.accent_coral,
                        Accent.PINK to R.string.accent_pink, Accent.VIOLET to R.string.accent_violet, Accent.BLUE to R.string.accent_blue,
                        Accent.TEAL to R.string.accent_teal, Accent.GREEN to R.string.accent_green, Accent.MONO to R.string.accent_mono,
                        Accent.TAXI to R.string.accent_taxi, Accent.RISO to R.string.accent_riso, Accent.ACID to R.string.accent_acid,
                        Accent.NIGHT_BRUTAL to R.string.accent_night_brutal, Accent.CANDY to R.string.accent_candy,
                        Accent.POP_GARDEN to R.string.accent_pop_garden, Accent.BABY_BLUE to R.string.accent_baby_blue, Accent.RAVE to R.string.accent_rave,
                    )) { set(a.copy(accent = it)) }
                    // One choice for panels, stored as glass on/off plus its style. Clear glass needs Android 13.
                    val panels = when {
                        !a.glass -> Panels.FLAT
                        a.glassStyle == GlassStyle.BEZEL -> Panels.BEZEL
                        a.glassStyle == GlassStyle.CLEAR && Build.VERSION.SDK_INT >= 33 -> Panels.GLASS
                        else -> Panels.BLUR
                    }
                    ChoiceItem(stringResource(R.string.panels), panels, listOfNotNull(
                        if (Build.VERSION.SDK_INT >= 33) Panels.GLASS to R.string.panels_glass else null,
                        Panels.BLUR to R.string.panels_blur,
                        Panels.BEZEL to R.string.panels_bezel,
                        Panels.FLAT to R.string.panels_flat,
                    )) {
                        set(when (it) {
                            Panels.GLASS -> a.copy(glass = true, glassStyle = GlassStyle.CLEAR)
                            Panels.BLUR -> a.copy(glass = true, glassStyle = GlassStyle.FROSTED)
                            Panels.BEZEL -> a.copy(glass = true, glassStyle = GlassStyle.BEZEL)
                            Panels.FLAT -> a.copy(glass = false)
                        })
                    }
                    Footer(stringResource(when (panels) {
                        Panels.GLASS -> R.string.panels_glass_summary
                        Panels.BLUR -> R.string.panels_blur_summary
                        Panels.BEZEL -> R.string.panels_bezel_summary
                        Panels.FLAT -> R.string.panels_flat_summary
                    }))
                    if (a.needsGlassBackdrop) {
                        SectionHeader(stringResource(R.string.glass_wallpaper))
                        GlassSource(vm, resumes)
                    }
                }

                Page.LIST -> {
                    ChoiceItem(stringResource(R.string.list_style), a.listStyle, listOf(
                        ListStyle.PANEL to R.string.list_style_panel, ListStyle.WALLPAPER to R.string.list_style_wallpaper,
                    )) { set(a.copy(listStyle = it)) }
                    SwitchItem(stringResource(R.string.hide_letters), stringResource(R.string.hide_letters_summary), a.hideLetters) {
                        set(a.copy(hideLetters = it))
                    }
                    SectionHeader(stringResource(R.string.hidden_apps))
                    val hidden = state.hidden.filter { it in state.byKey }.sortedBy { state.label(it).lowercase() }
                    if (hidden.isEmpty()) Footer(stringResource(R.string.hidden_none))
                    hidden.forEach { key ->
                        ListItem(
                            headlineContent = { Text(state.label(key)) },
                            trailingContent = { TextButton(onClick = { vm.unhide(key) }) { Text(stringResource(R.string.unhide)) } },
                        )
                    }
                }

                Page.SETUP -> SetupPage(isHome, hasNotificationAccess, resumes, onRoleResult = { resumes++ })
            }
        }
    }
}

@Composable
private fun SetupPage(isHome: Boolean, hasNotificationAccess: Boolean, resumes: Int, onRoleResult: () -> Unit) {
    val context = LocalContext.current
    val roleManager = context.getSystemService(RoleManager::class.java)
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onRoleResult() }
    val hasPowerBubble = remember(resumes) { context.packageManager.getLaunchIntentForPackage(SystemActions.POWER_BUBBLE_PACKAGE) != null }
    StatusItem(
        title = stringResource(R.string.set_home),
        summary = stringResource(if (isHome) R.string.set_home_done else R.string.set_home_todo),
        ok = isHome,
        onClick = if (isHome) null else ({ requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }),
    )
    StatusItem(
        title = stringResource(R.string.notif_title),
        summary = stringResource(if (hasNotificationAccess) R.string.notif_on else R.string.notif_off),
        ok = hasNotificationAccess,
        onClick = if (hasNotificationAccess) null else ({ openNotificationAccess(context) }),
    )
    if (!hasNotificationAccess) {
        Footer(stringResource(R.string.restricted_hint))
        ListItem(
            headlineContent = { Text(stringResource(R.string.open_app_info)) },
            modifier = Modifier.clickable { openAppInfo(context) },
        )
    }
    StatusItem(
        title = stringResource(R.string.power_bubble_title),
        summary = stringResource(if (hasPowerBubble) R.string.power_bubble_on else R.string.power_bubble_off),
        ok = hasPowerBubble,
        onClick = null,
    )
}

/** A requirement: a tick when it's met, otherwise a warning and (usually) a tap to sort it out. */
@Composable
private fun StatusItem(title: String, summary: String, ok: Boolean, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = {
            Icon(
                painterResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error),
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Explanatory text under a group of settings. */
@Composable
private fun Footer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** An on/off setting: the whole row toggles the switch. */
@Composable
private fun SwitchItem(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** A setting with a few options: shows the current one, and opens a list of radio buttons to change it. */
@Composable
private fun <T> ChoiceItem(title: String, selected: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = current?.let { { Text(stringResource(it)) } },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = value == selected, role = Role.RadioButton) {
                                    onSelect(value)
                                    open = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(stringResource(label), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Where glass gets the wallpaper to blur from, and how to give it access. */
@Composable
private fun GlassSource(vm: LauncherViewModel, resumes: Int) {
    val context = LocalContext.current
    var picks by remember { mutableIntStateOf(0) }
    val source by produceState<WallpaperBackdrop.Source?>(null, resumes, picks) {
        value = withContext(Dispatchers.IO) { WallpaperBackdrop.source(context) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.usePickedWallpaper(uri)
            picks++
        }
    }
    Footer(
        stringResource(
            when (source) {
                WallpaperBackdrop.Source.SYSTEM -> R.string.glass_source_system
                WallpaperBackdrop.Source.PICKED -> R.string.glass_source_picked
                WallpaperBackdrop.Source.LIVE -> R.string.glass_source_live
                WallpaperBackdrop.Source.NONE -> R.string.glass_source_none
                null -> R.string.glass_source_checking
            },
        ),
    )
    if (source != null && source != WallpaperBackdrop.Source.SYSTEM) {
        // Access doesn't help with a live wallpaper: no app can see one
        if (source != WallpaperBackdrop.Source.LIVE) ListItem(
            headlineContent = { Text(stringResource(R.string.glass_allow_access)) },
            supportingContent = { Text(stringResource(R.string.glass_access_note)) },
            modifier = Modifier.clickable {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.glass_pick_photo)) },
            modifier = Modifier.clickable {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

private enum class Panels { GLASS, BLUR, BEZEL, FLAT }
