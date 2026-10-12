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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.LauncherViewModel
import io.github.peteroomen.launcher.data.Backdrop
import io.github.peteroomen.launcher.data.FavouritesLayout
import io.github.peteroomen.launcher.data.ListStyle
import io.github.peteroomen.launcher.indexLetter
import io.github.peteroomen.launcher.model.Fav
import io.github.peteroomen.launcher.model.Pie
import io.github.peteroomen.launcher.model.PieHit
import io.github.peteroomen.launcher.system.MediaState
import io.github.peteroomen.launcher.system.NotificationDots
import io.github.peteroomen.launcher.system.NotificationListener
import io.github.peteroomen.launcher.system.SystemActions

private enum class Overlay { NONE, ALL_APPS, SEARCH }

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val glassImage by vm.glassBackdrop.collectAsStateWithLifecycle()
    val nowPlaying by MediaState.nowPlaying.collectAsStateWithLifecycle()
    val hasNotificationAccess by NotificationDots.connected.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var editingFavourites by remember { mutableStateOf(false) }
    var jumpLetter by remember { mutableStateOf<Char?>(null) }
    var scrubY by remember { mutableFloatStateOf(0f) }
    var stripTop by remember { mutableFloatStateOf(0f) }
    var selectY by remember { mutableStateOf<Float?>(null) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    // A finger is on the A–Z strip, or sliding off it to pick a name
    var fingerDown by remember { mutableStateOf(false) }
    var pie by remember { mutableStateOf<PieState?>(null) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val usePie = state.appearance.favouritesLayout == FavouritesLayout.PIE
    // The pie holds the first eight favourites, folders included
    val pieSlots = state.favs.take(Pie.SLOTS)
    val folderSize = { slot: Int -> (pieSlots.getOrNull(slot) as? Fav.Folder)?.apps?.size ?: 0 }
    val density = LocalDensity.current
    val deadZonePx = with(density) { PieDeadZone.toPx() }
    val folderRingPx = with(density) { PieFolderRing.toPx() }
    fun pieHit(center: Offset, finger: Offset, previous: PieHit?) =
        Pie.hit(finger.x - center.x, finger.y - center.y, deadZonePx, folderRingPx, folderSize, previous)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pie?.hit) { if (pie != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    // With liquid glass, the wallpaper itself blurs behind search, the A–Z list and the pie
    // (Only behind the sheet: "wallpaper only" lists show the wallpaper as it is)
    val sheetLists = state.appearance.listStyle == ListStyle.PANEL
    WallpaperBlur(on = state.appearance.glass && ((overlay != Overlay.NONE && sheetLists) || pie != null))
    // With "wallpaper only" lists, the rest of the home screen steps aside while they're open
    val homeAlpha by animateFloatAsState(if (overlay != Overlay.NONE && !sheetLists) 0f else 1f, label = "home")

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
        // Line the wallpaper up against the whole screen. Not the window: that shrinks when the
        // keyboard opens, which would zoom and shift the wallpaper inside the glass.
        val configuration = LocalConfiguration.current
        val fullScreen = remember(configuration) {
            (context as? Activity)?.windowManager?.currentWindowMetrics?.bounds?.let { Size(it.width().toFloat(), it.height().toFloat()) }
        }
        val backdrop = glassImage?.let { images -> fullScreen?.let { GlassBackdrop(images.frosted, images.sharp, it) } }
        CompositionLocalProvider(LocalPalette provides palette, LocalIconLoader provides vm, LocalGlassBackdrop provides backdrop) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { screenSize = it }
                    .homeGestures(
                        pieEnabled = usePie,
                        onDoubleTap = { SystemActions.screenOff(context) },
                        onTap = { openFolder = null },
                        onLongPress = { menu = MenuTarget.Home },
                        onSwipeUp = { overlay = Overlay.SEARCH },
                        onSwipeDown = { SystemActions.expandNotifications(context) },
                        onPieStart = { at ->
                            // Keep the whole pie on screen, including folder arcs
                            val reach = if (pieSlots.any { it is Fav.Folder }) PieReachWithFolders else PieRadius + 8.dp
                            val r = with(density) { reach.toPx() }
                            val top = with(density) { 96.dp.toPx() }
                            val center = Offset(
                                at.x.coerceIn(r, (screenSize.width - r).coerceAtLeast(r)),
                                at.y.coerceIn(r + top, (screenSize.height - r).coerceAtLeast(r + top)),
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            pie = PieState(center, at, pieHit(center, at, null))
                        },
                        onPieMove = { at -> pie = pie?.let { it.copy(finger = at, hit = pieHit(it.center, at, it.hit)) } },
                        onPieEnd = {
                            val hit = pie?.hit
                            pie = null
                            if (hit != null) {
                                when (val fav = hit.slot?.let { pieSlots.getOrNull(it) }) {
                                    null -> if (hit.slot == null) menu = MenuTarget.Home
                                    is Fav.App -> vm.launch(fav.key)
                                    // On the folder itself nothing opens: its apps are in the arc
                                    is Fav.Folder -> hit.folderIndex?.let { vm.launch(fav.apps[it]) }
                                }
                            }
                        },
                    ),
            ) {
                ReadabilityBackdrop(state.appearance.backdrop)

                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = homeAlpha }
                        .systemBarsPadding()
                        // Room on the right for the A–Z letters, unless they're hidden
                        .padding(start = 24.dp, end = if (state.appearance.hideLetters) 24.dp else 48.dp, top = 28.dp, bottom = 16.dp),
                ) {
                    ClockAndDate(state.appearance)
                    // Favourites sit centred on the left between the clock and the music player (unless they live in the pie)
                    if (usePie) {
                        Spacer(Modifier.weight(1f))
                    } else {
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
                    MusicWidget(
                        nowPlaying = nowPlaying,
                        hasAccess = hasNotificationAccess,
                        appLabel = nowPlaying?.let { np -> state.apps.firstOrNull { it.packageName == np.packageName }?.label },
                        onNeedAccess = { openNotificationAccess(context) },
                    )
                }

                AnimatedVisibility(overlay == Overlay.ALL_APPS, enter = fadeIn(), exit = fadeOut()) {
                    AllAppsOverlay(
                        state = state,
                        jumpTo = jumpLetter,
                        selectY = selectY,
                        onlyLetter = jumpLetter.takeIf { fingerDown },
                        anchorY = stripTop + scrubY,
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
                        jumpLetter = letter
                        fingerDown = true
                        scrubY = y
                    },
                    onSelect = { y ->
                        selectY = stripTop + y
                    },
                    onRelease = {
                        // Letting go over a name opens it, like Niagara
                        val key = highlighted.takeIf { selectY != null }
                        fingerDown = false
                        selectY = null
                        highlighted = null
                        if (key != null) {
                            vm.launch(key)
                            goHome()
                        }
                    },
                    modifier = Modifier
                        // Low on the screen, where a thumb reaches it
                        .align(Alignment.BottomEnd)
                        .systemBarsPadding()
                        .padding(end = 4.dp, bottom = 40.dp)
                        .fillMaxHeight(0.72f)
                        .onGloballyPositioned { stripTop = it.positionInRoot().y },
                    hidden = state.appearance.hideLetters,
                    shown = overlay == Overlay.ALL_APPS,
                )

                pie?.let { PieMenu(state, it, pieSlots) }


                AnimatedVisibility(overlay == Overlay.SEARCH, enter = fadeIn(), exit = fadeOut()) {
                    SearchOverlay(
                        state = state,
                        onLaunch = { key, searched -> vm.launch(key, fromSearch = searched); goHome() },
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
                    EditFavouritesDialog(state, vm, onDismiss = { editingFavourites = false })
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

/** The favourites column. Tap opens; hold opens the menu. Reordering lives in Edit favourites. */
@Composable
private fun Favourites(
    state: HomeUiState,
    openFolder: String?,
    onToggleFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onLaunch: (String) -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    // Rows run the full width, so a favourite can be tapped anywhere along its line (right thumb too)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.favs.forEach { fav ->
            when (fav) {
                is Fav.App -> FavouriteApp(
                    key = fav.key,
                    state = state,
                    modifier = Modifier.fillMaxWidth().pressable(state.label(fav.key), { onLaunch(fav.key) }, { onMenu(MenuTarget.App(fav.key)) }),
                )
                is Fav.Folder -> FolderEntry(
                    folder = fav,
                    state = state,
                    open = openFolder == fav.id,
                    onClose = onCloseFolder,
                    onLaunch = onLaunch,
                    onAppLongClick = { onMenu(MenuTarget.App(it)) },
                    modifier = Modifier.fillMaxWidth().pressable(folderLabel(fav), { onToggleFolder(fav.id) }, { onMenu(MenuTarget.Folder(fav.id)) }),
                )
            }
        }
    }
}

/**
 * Gestures on the home screen. Vertical swipes work anywhere. On empty space: tap, double-tap, and a
 * long press, which opens the home menu, or in pie mode shows the pie and follows the finger.
 */
@Composable
private fun Modifier.homeGestures(
    pieEnabled: Boolean,
    onDoubleTap: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onPieStart: (Offset) -> Unit,
    onPieMove: (Offset) -> Unit,
    onPieEnd: () -> Unit,
): Modifier {
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val up by rememberUpdatedState(onSwipeUp)
    val down by rememberUpdatedState(onSwipeDown)
    val pieStart by rememberUpdatedState(onPieStart)
    val pieMove by rememberUpdatedState(onPieMove)
    val pieEnd by rememberUpdatedState(onPieEnd)
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    return this
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
        .pointerInput(pieEnabled) {
            if (!pieEnabled) {
                detectTapGestures(onDoubleTap = { doubleTap() }, onTap = { tap() }, onLongPress = { longPress() })
                return@pointerInput
            }
            var lastTapAt = 0L
            awaitEachGesture {
                val first = awaitFirstDown()
                var cancelled = false
                val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation().also { if (it == null) cancelled = true }
                }
                if (lifted != null) {
                    if (lifted.uptimeMillis - lastTapAt < viewConfiguration.doubleTapTimeoutMillis) {
                        lastTapAt = 0L
                        doubleTap()
                    } else {
                        lastTapAt = lifted.uptimeMillis
                        tap()
                    }
                    return@awaitEachGesture
                }
                if (cancelled) return@awaitEachGesture // it was a swipe

                pieStart(first.position)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                    change.consume() // keeps the swipe detector from treating this as a swipe
                    if (!change.pressed) break
                    pieMove(change.position)
                }
                pieEnd()
            }
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

/** Blurs the wallpaper behind the whole launcher window (Android 12+, where the phone supports it). */
@Composable
private fun WallpaperBlur(on: Boolean) {
    val view = LocalView.current
    val radius = with(LocalDensity.current) { 28.dp.roundToPx() }
    LaunchedEffect(on) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        window.setBackgroundBlurRadius(if (on) radius else 0)
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
