package io.github.peteroomen.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateListOf
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
import io.github.peteroomen.launcher.LauncherViewModel
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.data.FavouritesLayout
import io.github.peteroomen.launcher.model.DropTarget
import io.github.peteroomen.launcher.model.EditDrag
import io.github.peteroomen.launcher.model.Fav
import io.github.peteroomen.launcher.model.FavOps

private val RowHeight = 60.dp
private val ChildHeight = 52.dp

/**
 * Favourites first, in order, with each folder's apps listed under it. Drag a handle to reorder;
 * drop an app on a folder to add it, or on another app to make a folder. Then every other app, to
 * tick on. Dragging is off while searching, since only some rows are showing.
 */
@Composable
fun EditFavouritesDialog(state: HomeUiState, vm: LauncherViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    fun matches(label: String) = q.isEmpty() || label.contains(q, ignoreCase = true)
    var renaming by remember { mutableStateOf<Fav.Folder?>(null) }
    var creating by remember { mutableStateOf(false) }
    val defaultFolderName = stringResource(R.string.default_folder_name)

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

    // Drag state, worked out over the full favourites list
    val canDrag = q.isEmpty()
    val ids = favs.map(FavOps::idOf)
    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    val childPx = with(density) { ChildHeight.toPx() }
    val heights = favs.map { if (it is Fav.Folder) rowPx + it.apps.size * childPx else rowPx }
    val haptics = LocalHapticFeedback.current
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val from = ids.indexOf(dragId)
    val draggingApp = favs.getOrNull(from) is Fav.App
    val target: DropTarget? = if (from < 0) null else EditDrag.resolve(heights, rowPx, from, dragOffset) { draggingApp }
    LaunchedEffect(target) { if (target is DropTarget.Into) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            // Icons and labels follow the system theme here, not the wallpaper
            val palette = LocalPalette.current.copy(text = MaterialTheme.colorScheme.onSurface, shadow = null, glass = false)
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
                        item(key = "#favs") {
                            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { Section(stringResource(R.string.in_favourites)) }
                                TextButton(onClick = { creating = true }) {
                                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.new_folder), Modifier.padding(start = 6.dp))
                                }
                            }
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
                            val index = ids.indexOf(id)
                            val dragging = id == dragId
                            val dropInto = (target as? DropTarget.Into)?.index == index
                            val shift by animateFloatAsState(EditDrag.shift(index, from, target, heights.getOrElse(from) { 0f }), label = "shift")
                            val handle = if (!canDrag) null else Modifier.dragHandle(
                                onStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragId = id
                                    dragOffset = 0f
                                },
                                onDrag = { dragOffset += it },
                                onEnd = {
                                    val dragged = favs.getOrNull(from)
                                    when (target) {
                                        is DropTarget.At -> if (target.index != from) {
                                            val newOrder = FavOps.move(ids, from, target.index)
                                            pending = newOrder
                                            vm.reorderFavourites(newOrder)
                                        }
                                        is DropTarget.Into -> if (dragged is Fav.App) {
                                            when (val onto = favs[target.index]) {
                                                is Fav.Folder -> vm.moveToFolder(dragged.key, onto.id)
                                                is Fav.App -> vm.mergeIntoNewFolder(dragged.key, onto.key, defaultFolderName)
                                            }
                                        }
                                        null -> Unit
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
                                    .then(if (dragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                                    .then(
                                        if (dropInto) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                        else Modifier,
                                    ),
                            ) {
                                when (fav) {
                                    is Fav.App -> CheckRow(fav.key, state, checked = true, handle = handle) { vm.removeFavourite(fav.key) }
                                    is Fav.Folder -> FolderBlock(
                                        folder = fav,
                                        state = state,
                                        handle = handle,
                                        onRename = { renaming = fav },
                                        onRemove = { vm.removeFolder(fav.id) },
                                        onMoveOut = vm::moveOutOfFolder,
                                        onRemoveApp = vm::removeFavourite,
                                    )
                                }
                            }
                        }
                        if (others.isNotEmpty()) item(key = "#others") { Section(stringResource(R.string.other_apps)) }
                        items(others, key = { it }) { key -> CheckRow(key, state, checked = false, handle = null) { vm.addFavourite(key) } }
                    }
                }
            renaming?.let { folder ->
                NameDialog(stringResource(R.string.rename_folder), folder.name, onDismiss = { renaming = null }) {
                    if (it.isNotBlank()) vm.renameFolder(folder.id, it.trim())
                    renaming = null
                }
            }
            if (creating) {
                NewFolderDialog(state, onDismiss = { creating = false }) { name, keys ->
                    vm.createFolder(name.ifBlank { defaultFolderName }, keys)
                    creating = false
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
        Handle(handle)
        AppIcon(key, state.appearance.iconStyle, ListIconSize, badge = false)
        Text(state.label(key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** A folder row (tap to rename, untick to remove the folder) with its apps listed under it. */
@Composable
private fun FolderBlock(
    folder: Fav.Folder,
    state: HomeUiState,
    handle: Modifier?,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onMoveOut: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clickable(onClickLabel = stringResource(R.string.rename_folder), onClick = onRename)
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
            Checkbox(checked = true, onCheckedChange = { onRemove() })
        }
        folder.apps.forEach { key ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ChildHeight)
                    .clickable(role = Role.Checkbox) { onRemoveApp(key) }
                    .padding(start = 60.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(key, state.appearance.iconStyle, 26.dp, badge = false)
                Text(state.label(key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMoveOut(key) }) {
                    Icon(
                        painterResource(R.drawable.ic_move_out),
                        contentDescription = stringResource(R.string.move_out_of, folder.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Checkbox(checked = true, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Name the folder and tick its apps, like Niagara's "Create pop-up folder". */
@Composable
private fun NewFolderDialog(state: HomeUiState, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) })
                Text(
                    stringResource(R.string.pick_folder_apps),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.apps, key = { it.key }) { app ->
                        val checked = app.key in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { if (checked) picked.remove(app.key) else picked.add(app.key) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.key, state.appearance.iconStyle, 28.dp, badge = false)
                            Text(state.label(app.key), Modifier.weight(1f).padding(start = 12.dp))
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, picked.toList()) }, enabled = picked.isNotEmpty()) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
