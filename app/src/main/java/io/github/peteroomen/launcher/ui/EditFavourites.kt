package io.github.peteroomen.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.FavouritesLayout
import io.github.peteroomen.launcher.model.Fav
import io.github.peteroomen.launcher.model.FavOps
import kotlin.math.roundToInt

private val RowHeight = 60.dp

/**
 * Favourites first, in order: drag the handle to reorder, untick to remove. Then every other app,
 * to tick on. Reordering is turned off while searching, since only some rows are showing.
 */
@Composable
fun EditFavouritesDialog(
    state: HomeUiState,
    onSetFavourite: (String, Boolean) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    fun matches(label: String) = q.isEmpty() || label.contains(q, ignoreCase = true)

    // After a drop, show the new order straight away until the saved order catches up
    var pending by remember { mutableStateOf<List<String>?>(null) }
    val savedIds = state.favs.map(FavOps::idOf)
    LaunchedEffect(savedIds) { pending = null }
    val byId = state.favs.associateBy(FavOps::idOf)
    val favs = pending?.mapNotNull { byId[it] }?.takeIf { it.size == state.favs.size } ?: state.favs
    val shownFavs = favs.filter {
        when (it) {
            is Fav.App -> matches(state.label(it.key))
            is Fav.Folder -> matches(it.name) || it.apps.any { key -> matches(state.label(key)) }
        }
    }
    val favouriteKeys = FavOps.allKeys(state.favs).toSet()
    val others = state.apps.map { it.key }.filter { it !in favouriteKeys && matches(state.label(it)) }

    val canReorder = q.isEmpty()
    val ids = favs.map(FavOps::idOf)
    val pitch = with(LocalDensity.current) { RowHeight.toPx() }
    val haptics = LocalHapticFeedback.current
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val from = ids.indexOf(dragId)
    val target = if (from < 0) -1 else (from + (dragOffset / pitch).roundToInt()).coerceIn(0, ids.lastIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            // Icons and labels follow the system theme here, not the wallpaper
            val palette = LocalPalette.current.copy(text = MaterialTheme.colorScheme.onSurface, shadow = null)
            CompositionLocalProvider(LocalPalette provides palette) {
                Column(Modifier.systemBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.edit_favourites), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize(), userScrollEnabled = dragId == null) {
                        if (shownFavs.isNotEmpty()) item(key = "#favs") {
                            Section(stringResource(R.string.in_favourites))
                            val hint = if (state.appearance.favouritesLayout == FavouritesLayout.PIE) R.string.reorder_hint_pie else R.string.reorder_hint
                            Text(
                                stringResource(hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                            )
                        }
                        items(shownFavs, key = FavOps::idOf) { fav ->
                            val id = FavOps.idOf(fav)
                            val dragging = id == dragId
                            val index = ids.indexOf(id)
                            val shift by animateFloatAsState(
                                when {
                                    from < 0 || dragging -> 0f
                                    from < target && index in (from + 1)..target -> -pitch
                                    from > target && index in target until from -> pitch
                                    else -> 0f
                                },
                                label = "shift",
                            )
                            val handle = if (!canReorder) null else Modifier.dragHandle(
                                onStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragId = id
                                    dragOffset = 0f
                                },
                                onDrag = { dragOffset += it },
                                onEnd = {
                                    if (from >= 0 && target >= 0 && from != target) {
                                        val newOrder = FavOps.move(ids, from, target)
                                        pending = newOrder
                                        onReorder(newOrder)
                                    }
                                    dragId = null
                                    dragOffset = 0f
                                },
                            )
                            Box(
                                Modifier
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (dragging) dragOffset else shift
                                        shadowElevation = if (dragging) 8.dp.toPx() else 0f
                                    }
                                    .then(if (dragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier),
                            ) {
                                when (fav) {
                                    is Fav.App -> CheckRow(fav.key, state, checked = true, handle = handle) { onSetFavourite(fav.key, false) }
                                    is Fav.Folder -> FolderRow(fav, state, handle) { onRemoveFolder(fav.id) }
                                }
                            }
                        }
                        if (others.isNotEmpty()) item(key = "#others") { Section(stringResource(R.string.other_apps)) }
                        items(others, key = { it }) { key -> CheckRow(key, state, checked = false, handle = null) { onSetFavourite(key, true) } }
                    }
                }
            }
        }
    }
}

/** Drag starts as soon as the handle moves; no long press needed. */
@Composable
private fun Modifier.dragHandle(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { start() },
            onDrag = { change, amount ->
                change.consume()
                drag(amount.y)
            },
            onDragEnd = { end() },
            onDragCancel = { end() },
        )
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Handle(modifier: Modifier?) {
    if (modifier == null) {
        Box(Modifier.size(24.dp))
    } else {
        Icon(
            painterResource(R.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.drag_to_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(24.dp),
        )
    }
}

@Composable
private fun CheckRow(key: String, state: HomeUiState, checked: Boolean, handle: Modifier?, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(start = 12.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (handle != null || checked) Handle(handle) else Box(Modifier.size(24.dp))
        AppIcon(key, state.appearance.iconStyle, ListIconSize, badge = false)
        Text(state.label(key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FolderRow(folder: Fav.Folder, state: HomeUiState, handle: Modifier?, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(role = Role.Checkbox, onClick = onRemove)
            .padding(start = 12.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Handle(handle)
        FolderIcon(folder, state, ListIconSize)
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(R.plurals.folder_apps, folder.apps.size, folder.apps.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = true, onCheckedChange = null)
    }
}
