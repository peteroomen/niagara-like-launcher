package io.github.peteroomen.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.ListStyle
import io.github.peteroomen.launcher.data.appNameWeight
import io.github.peteroomen.launcher.indexLetter
import io.github.peteroomen.launcher.model.FavOps
import io.github.peteroomen.launcher.model.Reason
import io.github.peteroomen.launcher.model.Suggestions
import kotlin.math.exp
import kotlin.math.roundToInt

val AzLetters = listOf('#') + ('A'..'Z')

/** How wide the A–Z strip's touch zone is (the letters themselves take the right 30dp). */
val StripTouchWidth = 44.dp

/**
 * The A–Z strip, Niagara style. Touching it bends the letters out to the left around the finger,
 * further the further the finger goes, magnifies the letter under it and shows that letter in a
 * bubble beside the finger. Sliding off to the left picks from the list instead ([onSelect]), and
 * letting go over a name opens it ([onRelease]).
 */
@Composable
fun AzStrip(
    available: Set<Char>,
    onScrub: (Char, Float) -> Unit,
    onSelect: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    /** Only show the letters while the strip is touched, or while [shown] (the app list is open). */
    hidden: Boolean = false,
    shown: Boolean = false,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scrub by rememberUpdatedState(onScrub)
    val select by rememberUpdatedState(onSelect)
    val release by rememberUpdatedState(onRelease)
    var active by remember { mutableStateOf<Char?>(null) }
    var touching by remember { mutableStateOf(false) }
    // The finger, in the strip's own coordinates: x goes negative as it moves left off the strip
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }

    val bulge = with(density) { 22.dp.toPx() }
    val reach = with(density) { 220.dp.toPx() }
    val pull by animateFloatAsState(
        if (touching) (bulge - fingerX.coerceAtMost(0f)).coerceAtMost(reach) else 0f,
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "pull",
    )
    val focus by animateFloatAsState(if (touching) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "focus")
    val alpha by animateFloatAsState(if (!hidden || touching || shown) 1f else 0f, label = "letters")

    // The letters sit in the rightmost 30dp; the touch zone reaches further left, for a thumb that
    // lands a little short of them
    val lettersLeft = with(density) { (StripTouchWidth - 30.dp).toPx() }
    Box(
        modifier
            .width(StripTouchWidth)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .graphicsLayer { this.alpha = alpha }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    touching = true
                    var picking = false
                    fun follow(x: Float, y: Float) {
                        fingerX = (x - lettersLeft).coerceAtMost(0f)
                        fingerY = y.coerceIn(0f, size.height.toFloat())
                    }
                    fun pick(y: Float) {
                        val i = (y / size.height * AzLetters.size).toInt().coerceIn(0, AzLetters.lastIndex)
                        val letter = AzLetters[i]
                        if (letter != active) {
                            active = letter
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        scrub(letter, y)
                    }
                    val leaveDistance = 24.dp.toPx()
                    follow(down.position.x, down.position.y)
                    pick(down.position.y)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            follow(change.position.x, change.position.y)
                            // Off the strip to the left: the letter stays put and the finger picks a name
                            if (picking || change.position.x - lettersLeft < -leaveDistance) {
                                picking = true
                                select(change.position.y)
                            } else {
                                pick(change.position.y)
                            }
                            change.consume()
                        }
                    } finally {
                        touching = false
                        active = null
                        release()
                    }
                }
            },
    ) {
        val slot = height.toFloat() / AzLetters.size
        val spread = height * 0.2f // how far up and down the bend reaches
        Column(Modifier.align(Alignment.CenterEnd).width(30.dp).fillMaxHeight()) {
            AzLetters.forEachIndexed { i, letter ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        letter.toString(),
                        color = when {
                            letter == active -> palette.accent
                            letter in available -> palette.text.copy(alpha = palette.muted.alpha + (1f - palette.muted.alpha) * focus)
                            else -> palette.muted.copy(alpha = palette.muted.alpha * 0.35f)
                        },
                        modifier = Modifier.graphicsLayer {
                            val d = (i + 0.5f) * slot - fingerY
                            val near = exp(-(d * d) / (2 * spread * spread))
                            translationX = -pull * near
                            val scale = 1f + focus * (0.35f * near + if (letter == active) 0.9f else 0f)
                            scaleX = scale
                            scaleY = scale
                        },
                        style = palette.style(10.sp, FontWeight.SemiBold),
                    )
                }
            }
        }
        // The letter, big, in a bubble just left of the bent strip
        val shownLetter = active
        if (shownLetter != null) {
            val bubble = with(density) { 60.dp.toPx() }
            val gap = with(density) { 28.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (lettersLeft + with(density) { 15.dp.toPx() } - pull - gap - bubble).roundToInt(),
                            (fingerY - bubble / 2).roundToInt(),
                        )
                    }
                    .size(60.dp)
                    .graphicsLayer {
                        scaleX = 0.6f + 0.4f * focus
                        scaleY = scaleX
                        this.alpha = focus
                    }
                    .background(palette.backdrop.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(shownLetter.toString(), color = palette.text, style = palette.style(32.sp, FontWeight.Medium))
            }
        }
    }
}

private sealed interface ListRow {
    data class Header(val letter: Char) : ListRow
    data class App(val key: String) : ListRow
}

/** Every app, A to Z, over a dimmed wallpaper. Opened by the A–Z strip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsOverlay(
    state: HomeUiState,
    jumpTo: Char?,
    /** While the finger slides off the A–Z strip: its y in root coordinates. */
    selectY: Float?,
    /**
     * While a finger is on the A–Z strip (or sliding off it to pick): only this letter's apps show,
     * grouped beside the finger at [anchorY] (root coordinates), like Niagara.
     */
    onlyLetter: Char?,
    anchorY: Float,
    onHighlight: (String?) -> Unit,
    onLaunch: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()
    val rows = remember(state.apps, state.renames) {
        state.apps.groupBy { indexLetter(state.label(it.key)) }.toSortedMap().flatMap { (letter, apps) ->
            listOf(ListRow.Header(letter)) + apps.map { ListRow.App(it.key) }
        }
    }
    LaunchedEffect(jumpTo, rows) {
        val target = jumpTo ?: return@LaunchedEffect
        // jump to this letter, or the next one that has apps
        val index = rows.indexOfFirst { it is ListRow.Header && it.letter >= target }.takeIf { it >= 0 }
            ?: rows.indexOfLast { it is ListRow.Header }
        if (index >= 0) listState.scrollToItem(index)
    }
    // The letter's apps (or the next letter that has any), while the finger is down
    val letterGroup = remember(onlyLetter, rows) {
        onlyLetter?.let { target ->
            val letter = rows.filterIsInstance<ListRow.Header>().firstOrNull { it.letter >= target }?.letter
                ?: rows.filterIsInstance<ListRow.Header>().lastOrNull()?.letter
            letter?.let { l -> l to state.apps.filter { indexLetter(state.label(it.key)) == l }.map { it.key } }
        }
    }
    // Where each visible name is on screen, to find the one under a finger coming off the A–Z strip
    val rowBounds = remember { HashMap<String, ClosedFloatingPointRange<Float>>() }
    val highlighted = selectY?.let { y ->
        val candidates = letterGroup?.second ?: listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
        candidates.firstOrNull { rowBounds[it]?.contains(y) == true }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(highlighted) {
        if (highlighted != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onHighlight(highlighted)
    }
    val bars = WindowInsets.systemBars.asPaddingValues()
    val sheet = state.appearance.listStyle == ListStyle.PANEL
    ListContainer(sheet, onDismiss) {
        if (letterGroup != null) {
            LetterGroup(
                letter = letterGroup.first,
                keys = letterGroup.second,
                state = state,
                highlighted = highlighted,
                anchorY = anchorY,
                rowBounds = rowBounds,
                onLaunch = onLaunch,
            )
            return@ListContainer
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 48.dp,
                top = if (sheet) 16.dp else bars.calculateTopPadding() + 24.dp,
                bottom = bars.calculateBottomPadding() + if (sheet) 24.dp else 120.dp,
            ),
        ) {
            items(rows, key = { if (it is ListRow.Header) "#h${it.letter}" else (it as ListRow.App).key }) { row ->
                when (row) {
                    is ListRow.Header -> Text(
                        row.letter.toString(),
                        color = palette.accent,
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                        style = palette.style(12.sp, FontWeight.SemiBold, 1.sp),
                    )
                    is ListRow.App -> AppRow(
                        key = row.key,
                        label = state.label(row.key),
                        appearance = state.appearance,
                        highlighted = row.key == highlighted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                val top = it.positionInRoot().y
                                rowBounds[row.key] = top..(top + it.size.height)
                            }
                            .graphicsLayer {
                                val scale = if (row.key == highlighted) 1.08f else 1f
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onLaunch(row.key) },
                                onLongClick = { onLongClick(row.key) },
                            )
                            .padding(vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/**
 * One letter's apps, centred on the finger's height so sliding left lands on them. Clamped to stay
 * on screen; a long group starts at the top.
 */
@Composable
private fun LetterGroup(
    letter: Char,
    keys: List<String>,
    state: HomeUiState,
    highlighted: String?,
    anchorY: Float,
    rowBounds: HashMap<String, ClosedFloatingPointRange<Float>>,
    onLaunch: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    var areaTop by remember { mutableFloatStateOf(0f) }
    var areaHeight by remember { mutableIntStateOf(0) }
    var groupHeight by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = bars.calculateTopPadding() + 16.dp, bottom = bars.calculateBottomPadding() + 16.dp)
            .onGloballyPositioned {
                areaTop = it.positionInRoot().y
                areaHeight = it.size.height
            },
    ) {
        val top = (anchorY - areaTop - groupHeight / 2f).coerceIn(0f, (areaHeight - groupHeight).coerceAtLeast(0).toFloat())
        Column(
            Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .onSizeChanged { groupHeight = it.height }
                .padding(start = 24.dp, end = 48.dp)
                .fillMaxWidth(),
        ) {
            Text(
                letter.toString(),
                color = palette.accent,
                modifier = Modifier.padding(bottom = 4.dp),
                style = palette.style(12.sp, FontWeight.SemiBold, 1.sp),
            )
            keys.forEach { key ->
                key(key) {
                    AppRow(
                        key = key,
                        label = state.label(key),
                        appearance = state.appearance,
                        highlighted = key == highlighted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                val y = it.positionInRoot().y
                                rowBounds[key] = y..(y + it.size.height)
                            }
                            .graphicsLayer {
                                val scale = if (key == highlighted) 1.08f else 1f
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .quietClick { onLaunch(key) }
                            .padding(vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** Search apps by name. Hidden apps only turn up once you've typed two letters. */
fun searchApps(state: HomeUiState, query: String): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val pool = state.byKey.values.filter { q.length >= 2 || it.key !in state.hidden }
    return pool
        .filter { state.label(it.key).contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true) }
        .sortedWith(compareBy({ !state.label(it.key).startsWith(q, ignoreCase = true) }, { state.label(it.key).lowercase() }))
        .take(8)
        .map { it.key }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchOverlay(
    state: HomeUiState,
    /** The app, and whether it was found by typing (counted towards "often searched for"). */
    onLaunch: (String, Boolean) -> Unit,
    onLongClick: (String) -> Unit,
    onWebSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, state) { searchApps(state, query) }
    // Before anything's typed: new apps, and the ones you search for and open most
    val suggestions = remember(state.apps, state.usage, state.favs) {
        Suggestions.pick(
            installedAt = state.apps.associate { it.key to it.installedAt },
            usage = state.usage,
            favourites = FavOps.allKeys(state.favs),
            now = System.currentTimeMillis(),
        )
    }
    val searched = query.isNotBlank()
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Same container as the A–Z list: straight on the wallpaper, or a sheet from the bottom
    val sheet = state.appearance.listStyle == ListStyle.PANEL
    val ink = palette.text
    // Frosted sheets are calm enough for plain text; clear glass and bare wallpaper need the shadow
    val listPalette = if (sheet && !(palette.glass && palette.glassClear)) palette.copy(shadow = null) else palette
    ListContainer(sheet, onDismiss) {
        Column(
            if (sheet) {
                Modifier.navigationBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp)
            } else {
                Modifier.statusBarsPadding().padding(horizontal = 22.dp, vertical = 24.dp)
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                // The same size and weight as the names below it, and in the A–Z list
                textStyle = TextStyle(color = ink, fontSize = state.appearance.appTextSize.sp.sp, fontWeight = state.appearance.appNameWeight, fontFamily = palette.font),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    results.firstOrNull()?.let { onLaunch(it, true) } ?: if (query.isNotBlank()) onWebSearch(query) else Unit
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .background(ink.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .glassRim(RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_apps),
                                color = ink.copy(alpha = 0.5f),
                                fontSize = state.appearance.appTextSize.sp.sp,
                                fontWeight = state.appearance.appNameWeight,
                                fontFamily = palette.font,
                            )
                        }
                        field()
                    }
                },
            )
            Box(Modifier.padding(top = 10.dp))
            CompositionLocalProvider(LocalPalette provides listPalette) {
                if (searched) {
                    results.forEach { key ->
                        key(key) { SearchResult(key, state, null, { onLaunch(it, true) }, onLongClick) }
                    }
                } else {
                    suggestions.forEach { s ->
                        key(s.key) { SearchResult(s.key, state, reasonText(s.reason), { onLaunch(it, false) }, onLongClick) }
                    }
                }
            }
            if (query.isNotBlank()) {
                Text(
                    stringResource(R.string.search_web, query.trim()),
                    color = ink.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontFamily = palette.font,
                    modifier = Modifier.fillMaxWidth().quietClick { onWebSearch(query.trim()) }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResult(key: String, state: HomeUiState, subtitle: String?, onLaunch: (String) -> Unit, onLongClick: (String) -> Unit) {
    AppRow(
        key = key,
        label = state.label(key),
        appearance = state.appearance,
        subtitle = subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onLaunch(key) },
                onLongClick = { onLongClick(key) },
            )
            .padding(vertical = 6.dp),
    )
}

/**
 * Where search and the A–Z list sit. As a [sheet]: a panel filling the bottom 80% of the screen,
 * edge to edge with only the top corners rounded (glass when liquid glass is on), with a light dim
 * above it. Otherwise: nothing at all, the wallpaper is the background. Tapping outside the
 * content closes it.
 */
@Composable
fun ListContainer(sheet: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .then(if (sheet) Modifier.background(Color.Black.copy(alpha = 0.2f)) else Modifier)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .imePadding(),
    ) {
        if (sheet) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .panel(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), palette.scrim, sheet = true)
                    .pointerInput(Unit) { detectTapGestures { } }, // taps on the sheet itself don't close it
            ) { content() }
        } else {
            content()
        }
    }
}

/** Why a suggestion is there, under its name. */
@Composable
private fun reasonText(reason: Reason): String = when (reason) {
    is Reason.Installed -> when (val days = Suggestions.daysAgo(reason.at, System.currentTimeMillis())) {
        0 -> stringResource(R.string.reason_installed_today)
        1 -> stringResource(R.string.reason_installed_yesterday)
        else -> pluralStringResource(R.plurals.reason_installed_days, days, days)
    }
    Reason.Searched -> stringResource(R.string.reason_searched)
    Reason.Used -> stringResource(R.string.reason_used)
    Reason.Favourite -> stringResource(R.string.reason_favourite)
}
