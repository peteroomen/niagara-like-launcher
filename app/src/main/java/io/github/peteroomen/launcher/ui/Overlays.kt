package io.github.peteroomen.launcher.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.ListStyle
import io.github.peteroomen.launcher.indexLetter
import io.github.peteroomen.launcher.model.FavOps

val AzLetters = listOf('#') + ('A'..'Z')

/**
 * Letters down the right edge. Dragging along them reports the letter under the finger and its y position.
 * Sliding left off the strip reports [onSelect] instead, so a name in the list can be picked by letting go.
 */
@Composable
fun AzStrip(
    available: Set<Char>,
    onScrub: (Char, Float) -> Unit,
    onSelect: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val scrub by rememberUpdatedState(onScrub)
    val select by rememberUpdatedState(onSelect)
    val release by rememberUpdatedState(onRelease)
    var active by remember { mutableStateOf<Char?>(null) }
    Column(
        modifier
            .width(30.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
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
                    pick(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        if (change.position.x < -leaveDistance) {
                            active = null
                            select(change.position.y)
                        } else {
                            pick(change.position.y)
                        }
                        change.consume()
                    }
                    active = null
                    release()
                }
            },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AzLetters.forEach { letter ->
            Text(
                letter.toString(),
                color = when {
                    letter == active -> palette.accent
                    letter in available -> palette.muted
                    else -> palette.muted.copy(alpha = palette.muted.alpha * 0.35f)
                },
                modifier = Modifier.graphicsLayer {
                    scaleX = if (letter == active) 1.5f else 1f
                    scaleY = scaleX
                },
                style = palette.style(10.sp, FontWeight.SemiBold),
            )
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
    // Where each visible name is on screen, to find the one under a finger coming off the A–Z strip
    val rowBounds = remember { HashMap<String, ClosedFloatingPointRange<Float>>() }
    val highlighted = selectY?.let { y ->
        listState.layoutInfo.visibleItemsInfo
            .mapNotNull { it.key as? String }
            .firstOrNull { rowBounds[it]?.contains(y) == true }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(highlighted) {
        if (highlighted != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onHighlight(highlighted)
    }
    val bars = WindowInsets.systemBars.asPaddingValues()
    val sheet = state.appearance.listStyle == ListStyle.PANEL
    ListContainer(sheet, onDismiss) {
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

/** Search apps by name. Hidden apps only turn up once you've typed two letters. */
fun searchApps(state: HomeUiState, query: String): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return FavOps.allKeys(state.favs).take(5)
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
    onLaunch: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onWebSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, state) { searchApps(state, query) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Same container as the A–Z list: straight on the wallpaper, or a sheet from the bottom
    val sheet = state.appearance.listStyle == ListStyle.PANEL
    val ink = palette.text
    val listPalette = if (sheet) palette.copy(shadow = null) else palette
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
                textStyle = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.Medium, fontFamily = palette.font),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    results.firstOrNull()?.let(onLaunch) ?: if (query.isNotBlank()) onWebSearch(query) else Unit
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .background(ink.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) Text(stringResource(R.string.search_apps), color = ink.copy(alpha = 0.5f), fontSize = 22.sp, fontFamily = palette.font)
                        field()
                    }
                },
            )
            Box(Modifier.padding(top = 10.dp))
            CompositionLocalProvider(LocalPalette provides listPalette) {
                results.forEach { key ->
                    key(key) { SearchResult(key, state, onLaunch, onLongClick) }
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
private fun SearchResult(key: String, state: HomeUiState, onLaunch: (String) -> Unit, onLongClick: (String) -> Unit) {
    AppRow(
        key = key,
        label = state.label(key),
        appearance = state.appearance,
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
                    .panel(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), palette.scrim, rim = 1.5.dp)
                    .pointerInput(Unit) { detectTapGestures { } }, // taps on the sheet itself don't close it
            ) { content() }
        } else {
            content()
        }
    }
}
