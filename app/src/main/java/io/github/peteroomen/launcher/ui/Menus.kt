package io.github.peteroomen.launcher.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.LauncherViewModel
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.SettingsActivity
import io.github.peteroomen.launcher.system.SystemActions

/** What a long press opened a menu for. */
sealed interface MenuTarget {
    data class App(val key: String) : MenuTarget
    data class Folder(val id: String) : MenuTarget
    data object Home : MenuTarget
}

private enum class Page { MAIN, PICK_FOLDER, RENAME, NEW_FOLDER, RENAME_FOLDER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(target: MenuTarget, state: HomeUiState, vm: LauncherViewModel, onEditFavourites: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember(target) { mutableStateOf(Page.MAIN) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            when (target) {
                is MenuTarget.App -> when (page) {
                    Page.MAIN -> AppActions(target.key, state, vm, onEditFavourites, onDismiss) { page = it }
                    Page.PICK_FOLDER -> FolderPicker(target.key, state, vm, onDismiss) { page = Page.NEW_FOLDER }
                    Page.RENAME -> NameForm(
                        title = stringResource(R.string.rename),
                        initial = state.label(target.key),
                        action = stringResource(R.string.save),
                        resetLabel = state.byKey[target.key]?.label,
                        onReset = { vm.rename(target.key, ""); onDismiss() },
                    ) { vm.rename(target.key, it); onDismiss() }
                    Page.NEW_FOLDER -> {
                        val fallback = stringResource(R.string.default_folder_name)
                        NameForm(stringResource(R.string.new_folder), "", stringResource(R.string.create)) {
                            vm.newFolder(target.key, it.ifBlank { fallback })
                            onDismiss()
                        }
                    }
                    else -> Unit
                }
                is MenuTarget.Folder -> {
                    val folder = state.folder(target.id)
                    if (folder == null) {
                        LaunchedEffect(Unit) { onDismiss() }
                        return@Column
                    }
                    if (page == Page.RENAME_FOLDER) {
                        NameForm(stringResource(R.string.rename_folder), folder.name, stringResource(R.string.save)) {
                            if (it.isNotBlank()) vm.renameFolder(folder.id, it.trim())
                            onDismiss()
                        }
                    } else {
                        SheetTitle(folder.name)
                        MenuItem(stringResource(R.string.rename_folder)) { page = Page.RENAME_FOLDER }
                        MenuItem(stringResource(R.string.ungroup)) { vm.ungroup(folder.id); onDismiss() }
                        MenuItem(stringResource(R.string.remove_favourite)) { vm.removeFolder(folder.id); onDismiss() }
                    }
                }
                MenuTarget.Home -> {
                    val context = LocalContext.current
                    MenuItem(stringResource(R.string.edit_favourites), onClick = onEditFavourites)
                    MenuItem(stringResource(R.string.wallpaper)) { SystemActions.setWallpaper(context); onDismiss() }
                    MenuItem(stringResource(R.string.settings_name)) {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppActions(
    key: String,
    state: HomeUiState,
    vm: LauncherViewModel,
    onEditFavourites: () -> Unit,
    onDismiss: () -> Unit,
    go: (Page) -> Unit,
) {
    val folder = state.folderOf(key)
    val done = { action: () -> Unit -> action(); onDismiss() }
    SheetTitle(state.label(key))
    if (!state.isFavourite(key)) MenuItem(stringResource(R.string.add_favourite)) { done { vm.addFavourite(key) } }
    folder?.let { MenuItem(stringResource(R.string.move_out_of, it.name)) { done { vm.moveOutOfFolder(key) } } }
    MenuItem(stringResource(if (folder != null) R.string.move_to_folder else R.string.add_to_folder)) { go(Page.PICK_FOLDER) }
    if (state.isFavourite(key)) MenuItem(stringResource(R.string.remove_favourite)) { done { vm.removeFavourite(key) } }
    MenuItem(stringResource(R.string.edit_favourites), onClick = onEditFavourites)
    MenuItem(stringResource(R.string.rename)) { go(Page.RENAME) }
    if (key in state.hidden) MenuItem(stringResource(R.string.unhide)) { done { vm.unhide(key) } }
    else MenuItem(stringResource(R.string.hide)) { done { vm.hide(key) } }
    MenuItem(stringResource(R.string.app_info)) { done { vm.openAppInfo(key) } }
    MenuItem(stringResource(R.string.uninstall)) { done { vm.uninstall(key) } }
}

@Composable
private fun FolderPicker(key: String, state: HomeUiState, vm: LauncherViewModel, onDismiss: () -> Unit, onNew: () -> Unit) {
    val current = state.folderOf(key)?.id
    SheetTitle(stringResource(R.string.put_in, state.label(key)))
    state.folders.filter { it.id != current }.forEach { folder ->
        MenuItem(folder.name) { vm.moveToFolder(key, folder.id); onDismiss() }
    }
    MenuItem(stringResource(R.string.new_folder), onClick = onNew)
}

@Composable
private fun NameForm(
    title: String,
    initial: String,
    action: String,
    resetLabel: String? = null,
    onReset: () -> Unit = {},
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    SheetTitle(title)
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        label = { Text(stringResource(R.string.name)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave(value.text) }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).focusRequester(focus),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
        if (resetLabel != null && resetLabel != initial) {
            TextButton(onClick = onReset) { Text(stringResource(R.string.reset_name, resetLabel)) }
        }
        TextButton(onClick = { onSave(value.text) }) { Text(action) }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
