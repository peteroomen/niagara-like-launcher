package io.github.peteroomen.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.launcher.data.Accent
import io.github.peteroomen.launcher.data.Appearance
import io.github.peteroomen.launcher.data.Backdrop
import io.github.peteroomen.launcher.data.ClockSize
import io.github.peteroomen.launcher.data.ClockWeight
import io.github.peteroomen.launcher.data.FavouritesLayout
import io.github.peteroomen.launcher.data.IconShape
import io.github.peteroomen.launcher.data.IconStyle
import io.github.peteroomen.launcher.data.LauncherFont
import io.github.peteroomen.launcher.data.TextShadow
import io.github.peteroomen.launcher.data.TextTone
import io.github.peteroomen.launcher.system.SystemActions
import io.github.peteroomen.launcher.system.WallpaperBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.peteroomen.launcher.ui.openNotificationAccess

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
    val hasCalendar = remember(resumes) { context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED }
    val hasPowerBubble = remember(resumes) { context.packageManager.getLaunchIntentForPackage(SystemActions.POWER_BUBBLE_PACKAGE) != null }

    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumes++ }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumes++
        vm.refreshCalendar()
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_name), style = MaterialTheme.typography.headlineMedium)

        Section(stringResource(R.string.set_home), stringResource(if (isHome) R.string.set_home_done else R.string.set_home_todo)) {
            if (!isHome) Button(onClick = { requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }) {
                Text(stringResource(R.string.set_home_button))
            }
        }

        Section(stringResource(R.string.notif_title), stringResource(if (hasNotificationAccess) R.string.notif_on else R.string.notif_off)) {
            if (!hasNotificationAccess) {
                Button(onClick = { openNotificationAccess(context) }) { Text(stringResource(R.string.notif_button)) }
                Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { openAppInfo(context) }) { Text(stringResource(R.string.open_app_info)) }
            }
        }

        Section(stringResource(R.string.calendar_title), stringResource(if (hasCalendar) R.string.calendar_on else R.string.calendar_off)) {
            if (!hasCalendar) Button(onClick = { requestCalendar.launch(Manifest.permission.READ_CALENDAR) }) {
                Text(stringResource(R.string.calendar_button))
            }
        }

        Section(stringResource(R.string.power_bubble_title), stringResource(if (hasPowerBubble) R.string.power_bubble_on else R.string.power_bubble_off)) {}

        val a = state.appearance
        fun set(change: Appearance) = vm.setAppearance(change)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.readability), style = MaterialTheme.typography.titleMedium)
        Chips(stringResource(R.string.text_shadow), a.textShadow, listOf(
            TextShadow.OFF to R.string.shadow_off, TextShadow.SOFT to R.string.shadow_soft,
            TextShadow.MEDIUM to R.string.shadow_medium, TextShadow.STRONG to R.string.shadow_strong,
        )) { set(a.copy(textShadow = it)) }
        Chips(stringResource(R.string.backdrop), a.backdrop, listOf(
            Backdrop.NONE to R.string.backdrop_none, Backdrop.LEFT_FADE to R.string.backdrop_left, Backdrop.DIM to R.string.backdrop_dim,
        )) { set(a.copy(backdrop = it)) }
        Chips(stringResource(R.string.text_colour), a.textTone, listOf(
            TextTone.AUTO to R.string.tone_auto, TextTone.LIGHT to R.string.tone_light, TextTone.DARK to R.string.tone_dark,
        )) { set(a.copy(textTone = it)) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.liquid_glass))
                Text(stringResource(R.string.liquid_glass_summary), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = a.glass, onCheckedChange = { set(a.copy(glass = it)) })
        }
        if (a.glass) GlassSource(vm, resumes)
        Chips(stringResource(R.string.accent_colour), a.accent, listOf(
            Accent.WALLPAPER to R.string.accent_wallpaper, Accent.AMBER to R.string.accent_amber, Accent.CORAL to R.string.accent_coral,
            Accent.PINK to R.string.accent_pink, Accent.VIOLET to R.string.accent_violet, Accent.BLUE to R.string.accent_blue,
            Accent.TEAL to R.string.accent_teal, Accent.GREEN to R.string.accent_green, Accent.MONO to R.string.accent_mono,
        )) { set(a.copy(accent = it)) }
        Chips(stringResource(R.string.font), a.font, listOf(
            LauncherFont.SYSTEM to R.string.font_system, LauncherFont.CONDENSED to R.string.font_condensed,
            LauncherFont.SERIF to R.string.font_serif, LauncherFont.MONO to R.string.font_mono,
        )) { set(a.copy(font = it)) }
        Chips(stringResource(R.string.clock_size), a.clockSize, listOf(
            ClockSize.SMALL to R.string.size_small, ClockSize.MEDIUM to R.string.size_medium, ClockSize.LARGE to R.string.size_large,
        )) { set(a.copy(clockSize = it)) }
        Chips(stringResource(R.string.clock_weight), a.clockWeight, listOf(
            ClockWeight.LIGHT to R.string.weight_light, ClockWeight.REGULAR to R.string.weight_regular, ClockWeight.BOLD to R.string.weight_bold,
        )) { set(a.copy(clockWeight = it)) }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.lock_portrait), Modifier.weight(1f))
            Switch(checked = a.lockPortrait, onCheckedChange = { set(a.copy(lockPortrait = it)) })
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.icons), style = MaterialTheme.typography.titleMedium)
        Chips(stringResource(R.string.favourites_layout), a.favouritesLayout, listOf(
            FavouritesLayout.COLUMN to R.string.layout_column, FavouritesLayout.PIE to R.string.layout_pie,
        )) { set(a.copy(favouritesLayout = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_names), Modifier.weight(1f))
            Switch(checked = a.showNames, onCheckedChange = { set(a.copy(showNames = it)) })
        }
        Chips(stringResource(R.string.icon_style), a.iconStyle, listOf(
            IconStyle.THEMED to R.string.icons_themed, IconStyle.COLOUR to R.string.icons_colour, IconStyle.GREYSCALE to R.string.icons_greyscale,
        )) { set(a.copy(iconStyle = it)) }
        Chips(stringResource(R.string.icon_shape), a.iconShape, listOf(
            IconShape.ROUNDED to R.string.shape_rounded, IconShape.CIRCLE to R.string.shape_circle, IconShape.SQUARE to R.string.shape_square,
        )) { set(a.copy(iconShape = it)) }
        Chips(stringResource(R.string.icon_size), a.iconSizeDp, listOf(
            36 to R.string.size_small, 44 to R.string.size_medium, 52 to R.string.size_large,
        )) { set(a.copy(iconSizeDp = it)) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.hidden_apps), style = MaterialTheme.typography.titleMedium)
        val hidden = state.hidden.filter { it in state.byKey }.sortedBy { state.label(it).lowercase() }
        if (hidden.isEmpty()) Text(stringResource(R.string.hidden_none), style = MaterialTheme.typography.bodyMedium)
        hidden.forEach { key ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.label(key), Modifier.weight(1f))
                OutlinedButton(onClick = { vm.unhide(key) }) { Text(stringResource(R.string.unhide)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, status: String, actions: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(status, style = MaterialTheme.typography.bodyMedium)
        actions()
    }
}

/** Where liquid glass gets the wallpaper to blur from, and how to give it access. */
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
    Column(Modifier.padding(start = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(
                when (source) {
                    WallpaperBackdrop.Source.SYSTEM -> R.string.glass_source_system
                    WallpaperBackdrop.Source.PICKED -> R.string.glass_source_picked
                    WallpaperBackdrop.Source.NONE -> R.string.glass_source_none
                    null -> R.string.glass_source_checking
                },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (source != null && source != WallpaperBackdrop.Source.SYSTEM) {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text(stringResource(R.string.glass_allow_access)) }
            Text(stringResource(R.string.glass_access_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(stringResource(R.string.glass_pick_photo)) }
        }
    }
}

/** A labelled row of chips; tapping one picks it. Scrolls sideways when there are many. */
@Composable
private fun <T> Chips(title: String, selected: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
            }
        }
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}
