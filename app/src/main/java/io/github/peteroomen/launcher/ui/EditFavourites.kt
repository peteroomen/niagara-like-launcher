package io.github.peteroomen.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.model.FavOps

/**
 * Tick the apps you want as favourites. Current favourites are listed first; the order is fixed
 * while the screen is open so rows don't jump as you tick them.
 */
@Composable
fun EditFavouritesDialog(state: HomeUiState, onSetFavourite: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val favourites = FavOps.allKeys(state.favs).toSet()
    val order = remember {
        val favs = FavOps.allKeys(state.favs)
        favs + state.apps.map { it.key }.filterNot { it in favs.toSet() }
    }
    val startedAsFavourite = remember { favourites }
    val shown = order.filter { query.isBlank() || state.label(it).contains(query.trim(), ignoreCase = true) }

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
                    LazyColumn(Modifier.fillMaxSize()) {
                        val (top, rest) = shown.partition { it in startedAsFavourite }
                        if (top.isNotEmpty()) item { Section(stringResource(R.string.in_favourites)) }
                        items(top, key = { it }) { key -> CheckRow(key, state, key in favourites, onSetFavourite) }
                        if (rest.isNotEmpty()) item { Section(stringResource(R.string.other_apps)) }
                        items(rest, key = { it }) { key -> CheckRow(key, state, key in favourites, onSetFavourite) }
                    }
                }
            }
        }
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
private fun CheckRow(key: String, state: HomeUiState, checked: Boolean, onSetFavourite: (String, Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onSetFavourite(key, !checked) }
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(key, state.appearance.iconStyle, ListIconSize, badge = false)
        Text(state.label(key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
