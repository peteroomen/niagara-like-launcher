package io.github.peteroomen.launcher.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.LauncherViewModel
import io.github.peteroomen.launcher.data.Backdrop
import io.github.peteroomen.launcher.indexLetter
import io.github.peteroomen.launcher.model.Fav
import io.github.peteroomen.launcher.model.FavOps
import io.github.peteroomen.launcher.system.MediaState
import io.github.peteroomen.launcher.system.NotificationDots
import io.github.peteroomen.launcher.system.NotificationListener
import io.github.peteroomen.launcher.system.SystemActions
import kotlin.math.roundToInt

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
    var editingFavourites by remember { mutableStateOf(false) }
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
        editingFavourites = false
    }

    LaunchedEffect(Unit) { vm.homePressed.collect { goHome() } }
    BackHandler(enabled = overlay != Overlay.NONE || openFolder != null) { goHome() }
    SystemBarIcons(dark = palette.darkText)
    Orientation(lockPortrait = state.appearance.lockPortrait)

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
                ReadabilityBackdrop(state.appearance.backdrop)

                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(start = 24.dp, end = 48.dp, top = 28.dp, bottom = 16.dp),
                ) {
                    ClockAndDate(state.appearance)
                    Spacer(Modifier.height(22.dp))
                    CalendarWidget(calendar, onPermissionResult = vm::refreshCalendar)
                    Spacer(Modifier.height(14.dp))
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
                            onDragStart = { openFolder = null },
                            onReorder = vm::reorderFavourites,
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
                        style = palette.style(64.sp, FontWeight.SemiBold),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(-with(density) { 52.dp.roundToPx() }, (stripTop + scrubY).toInt() - with(density) { 40.dp.roundToPx() }) },
                    )
                }

                AnimatedVisibility(overlay == Overlay.SEARCH, enter = fadeIn(), exit = fadeOut()) {
                    SearchOverlay(
                        state = state,
                        onLaunch = { vm.launch(it); goHome() },
                        onLongClick = { menu = MenuTarget.App(it) },
                        onWebSearch = { SystemActions.webSearch(context, it); goHome() },
                        onDismiss = ::goHome,
                    )
                }

                menu?.let {
                    MenuSheet(
                        target = it,
                        state = state,
                        vm = vm,
                        onEditFavourites = {
                            menu = null
                            editingFavourites = true
                        },
                        onDismiss = { menu = null },
                    )
                }

                if (editingFavourites) {
                    EditFavouritesDialog(state, onSetFavourite = vm::setFavourite, onDismiss = { editingFavourites = false })
                }
            }
        }
    }
}

/** Darkens (or lightens, for dark text) the wallpaper behind the launcher's text. */
@Composable
private fun ReadabilityBackdrop(backdrop: Backdrop) {
    val palette = LocalPalette.current
    when (backdrop) {
        Backdrop.NONE -> Unit
        Backdrop.LEFT_FADE -> Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to palette.backdrop.copy(alpha = 0.55f), 0.75f to Color.Transparent),
            ),
        )
        Backdrop.DIM -> Box(Modifier.fillMaxSize().background(palette.backdrop.copy(alpha = 0.3f)))
    }
}

/**
 * The favourites column. Hold an icon and drag it up or down to move it; the others slide out of
 * the way. Hold and let go without moving to open its menu.
 */
@Composable
private fun Favourites(
    state: HomeUiState,
    openFolder: String?,
    onToggleFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onLaunch: (String) -> Unit,
    onMenu: (MenuTarget) -> Unit,
    onDragStart: () -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val pitch = with(LocalDensity.current) { (state.appearance.iconSizeDp.dp + 10.dp).toPx() }

    // After a drop, show the new order straight away until the saved order catches up
    var pending by remember { mutableStateOf<List<String>?>(null) }
    val savedIds = state.favs.map(FavOps::idOf)
    LaunchedEffect(savedIds) { pending = null }
    val byId = state.favs.associateBy(FavOps::idOf)
    val favs = pending?.mapNotNull { byId[it] }?.takeIf { it.size == state.favs.size } ?: state.favs
    val ids = favs.map(FavOps::idOf)

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val from = ids.indexOf(dragId)
    val target = if (from < 0) -1 else (from + (dragOffset / pitch).roundToInt()).coerceIn(0, ids.lastIndex)
    val currentIds by rememberUpdatedState(ids)
    val currentTarget by rememberUpdatedState(target)

    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        favs.forEachIndexed { i, fav ->
            val id = ids[i]
            val dragging = i == from
            val shift by animateFloatAsState(
                when {
                    from < 0 || dragging -> 0f
                    from < target && i in (from + 1)..target -> -pitch
                    from > target && i in target until from -> pitch
                    else -> 0f
                },
                label = "shift",
            )
            val label = when (fav) {
                is Fav.App -> state.label(fav.key)
                is Fav.Folder -> folderLabel(fav)
            }
            val modifier = Modifier
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (dragging) dragOffset else shift
                    val scale = if (dragging) 1.12f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .favouriteGestures(
                    label = label,
                    onTap = {
                        when (fav) {
                            is Fav.App -> onLaunch(fav.key)
                            is Fav.Folder -> onToggleFolder(fav.id)
                        }
                    },
                    onMenu = {
                        onMenu(
                            when (fav) {
                                is Fav.App -> MenuTarget.App(fav.key)
                                is Fav.Folder -> MenuTarget.Folder(fav.id)
                            },
                        )
                    },
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart()
                        dragId = id
                        dragOffset = 0f
                    },
                    onDrag = { dragOffset += it },
                    onDragEnd = { moved ->
                        val fromIndex = currentIds.indexOf(id)
                        val toIndex = currentTarget
                        if (moved && fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                            val newOrder = FavOps.move(currentIds, fromIndex, toIndex)
                            pending = newOrder
                            onReorder(newOrder)
                        }
                        dragId = null
                        dragOffset = 0f
                    },
                )
            when (fav) {
                is Fav.App -> FavouriteApp(fav.key, state, modifier)
                is Fav.Folder -> FolderEntry(
                    folder = fav,
                    state = state,
                    open = openFolder == fav.id,
                    onClose = onCloseFolder,
                    onLaunch = onLaunch,
                    onAppLongClick = { onMenu(MenuTarget.App(it)) },
                    modifier = modifier,
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

/** Holds the home screen upright, or lets it follow the phone, per the setting. */
@Composable
private fun Orientation(lockPortrait: Boolean) {
    val view = LocalView.current
    LaunchedEffect(lockPortrait) {
        (view.context as? Activity)?.requestedOrientation =
            if (lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

fun openNotificationAccess(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        ComponentName(context, NotificationListener::class.java).flattenToString(),
    )
    if (!SystemActions.start(context, intent)) SystemActions.start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
