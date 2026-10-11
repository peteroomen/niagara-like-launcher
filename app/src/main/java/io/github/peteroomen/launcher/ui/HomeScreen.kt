package io.github.peteroomen.launcher.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.LauncherViewModel
import io.github.peteroomen.launcher.indexLetter
import io.github.peteroomen.launcher.model.Fav
import io.github.peteroomen.launcher.system.MediaState
import io.github.peteroomen.launcher.system.NotificationDots
import io.github.peteroomen.launcher.system.NotificationListener
import io.github.peteroomen.launcher.system.SystemActions

private enum class Overlay { NONE, ALL_APPS, SEARCH }

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val nowPlaying by MediaState.nowPlaying.collectAsStateWithLifecycle()
    val hasNotificationAccess by NotificationDots.connected.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    var jumpLetter by remember { mutableStateOf<Char?>(null) }
    var scrubY by remember { mutableFloatStateOf(0f) }
    var stripTop by remember { mutableFloatStateOf(0f) }
    var selectY by remember { mutableStateOf<Float?>(null) }
    var highlighted by remember { mutableStateOf<String?>(null) }

    fun goHome() {
        overlay = Overlay.NONE
        openFolder = null
        menu = null
    }

    LaunchedEffect(Unit) { vm.homePressed.collect { goHome() } }
    BackHandler(enabled = overlay != Overlay.NONE || openFolder != null) { goHome() }
    SystemBarIcons(dark = palette.darkText)

    MaterialTheme(colorScheme = dynamicDarkColorScheme(context)) {
        CompositionLocalProvider(LocalPalette provides palette, LocalIconLoader provides vm) {
            Box(
                Modifier
                    .fillMaxSize()
                    .homeGestures(
                        onDoubleTap = { SystemActions.screenOff(context) },
                        onTap = { openFolder = null },
                        onLongPress = { menu = MenuTarget.Home },
                        onSwipeUp = { overlay = Overlay.SEARCH },
                        onSwipeDown = { SystemActions.expandNotifications(context) },
                    ),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(start = 24.dp, end = 48.dp, top = 28.dp, bottom = 16.dp),
                ) {
                    ClockAndDate()
                    Spacer(Modifier.height(22.dp))
                    CalendarWidget(calendar, onPermissionResult = vm::refreshCalendar)
                    Spacer(Modifier.height(12.dp))
                    MusicWidget(
                        nowPlaying = nowPlaying,
                        hasAccess = hasNotificationAccess,
                        appLabel = nowPlaying?.let { np -> state.apps.firstOrNull { it.packageName == np.packageName }?.label },
                        onNeedAccess = { openNotificationAccess(context) },
                    )
                    // Favourites sit centred on the left in the space below the widgets
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Favourites(
                            state = state,
                            openFolder = openFolder,
                            onToggleFolder = { id -> openFolder = if (openFolder == id) null else id },
                            onCloseFolder = { openFolder = null },
                            onLaunch = { key -> vm.launch(key); openFolder = null },
                            onMenu = { openFolder = null; menu = it },
                        )
                    }
                }

                AnimatedVisibility(overlay == Overlay.ALL_APPS, enter = fadeIn(), exit = fadeOut()) {
                    AllAppsOverlay(
                        state = state,
                        jumpTo = jumpLetter,
                        selectY = selectY,
                        onHighlight = { highlighted = it },
                        onLaunch = { vm.launch(it); goHome() },
                        onLongClick = { menu = MenuTarget.App(it) },
                        onDismiss = ::goHome,
                    )
                }

                AzStrip(
                    available = remember(state.apps, state.renames) { state.apps.map { indexLetter(state.label(it.key)) }.toSet() },
                    onScrub = { letter, y ->
                        selectY = null
                        overlay = Overlay.ALL_APPS
                        openFolder = null
                        scrubLetter = letter
                        jumpLetter = letter
                        scrubY = y
                    },
                    onSelect = { y ->
                        scrubLetter = null
                        selectY = stripTop + y
                    },
                    onRelease = {
                        // Letting go over a name opens it, like Niagara
                        val key = highlighted.takeIf { selectY != null }
                        scrubLetter = null
                        selectY = null
                        highlighted = null
                        if (key != null) {
                            vm.launch(key)
                            goHome()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .systemBarsPadding()
                        .padding(end = 4.dp)
                        .fillMaxHeight(0.62f)
                        .onGloballyPositioned { stripTop = it.positionInRoot().y },
                )

                scrubLetter?.let { letter ->
                    val density = LocalDensity.current
                    Text(
                        letter.toString(),
                        color = palette.accent,
                        style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.SemiBold, shadow = palette.shadow),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(-with(density) { 52.dp.roundToPx() }, (stripTop + scrubY).toInt() - with(density) { 40.dp.roundToPx() }) },
                    )
                }

                AnimatedVisibility(overlay == Overlay.SEARCH, enter = fadeIn(), exit = fadeOut()) {
                    SearchOverlay(
                        state = state,
                        onLaunch = { vm.launch(it); goHome() },
                        onWebSearch = { SystemActions.webSearch(context, it); goHome() },
                        onDismiss = ::goHome,
                    )
                }

                menu?.let { MenuSheet(it, state, vm, onDismiss = { menu = null }) }
            }
        }
    }
}

@Composable
private fun Favourites(
    state: HomeUiState,
    openFolder: String?,
    onToggleFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onLaunch: (String) -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    // Not scrollable on purpose: swipes that start on an icon still open search or notifications
    Column(
        Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.favs.forEach { fav ->
            when (fav) {
                is Fav.App -> FavouriteApp(
                    key = fav.key,
                    state = state,
                    onClick = { onLaunch(fav.key) },
                    onLongClick = { onMenu(MenuTarget.App(fav.key)) },
                )
                is Fav.Folder -> FolderEntry(
                    folder = fav,
                    state = state,
                    open = openFolder == fav.id,
                    onClick = { onToggleFolder(fav.id) },
                    onLongClick = { onMenu(MenuTarget.Folder(fav.id)) },
                    onClose = onCloseFolder,
                    onLaunch = onLaunch,
                    onAppLongClick = { onMenu(MenuTarget.App(it)) },
                )
            }
        }
    }
}

/** Taps, double-taps and long presses on empty space, plus vertical swipes anywhere on the home screen. */
@Composable
private fun Modifier.homeGestures(
    onDoubleTap: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
): Modifier {
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val up by rememberUpdatedState(onSwipeUp)
    val down by rememberUpdatedState(onSwipeDown)
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    return this
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { doubleTap() }, onTap = { tap() }, onLongPress = { longPress() })
        }
        .pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    when {
                        total < -threshold -> up()
                        total > threshold -> down()
                    }
                },
                onVerticalDrag = { change, dy ->
                    total += dy
                    change.consume()
                },
            )
        }
}

/** Dark status and navigation bar icons on light wallpapers. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    LaunchedEffect(dark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}

fun openNotificationAccess(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        ComponentName(context, NotificationListener::class.java).flattenToString(),
    )
    if (!SystemActions.start(context, intent)) SystemActions.start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
