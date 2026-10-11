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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.launcher.data.IconStyle
import io.github.peteroomen.launcher.system.SystemActions
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

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
        val appearance = state.appearance
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_names), Modifier.weight(1f))
            Switch(checked = appearance.showNames, onCheckedChange = { vm.setAppearance(appearance.copy(showNames = it)) })
        }
        Choice(stringResource(R.string.icons_themed), appearance.iconStyle == IconStyle.THEMED) {
            vm.setAppearance(appearance.copy(iconStyle = IconStyle.THEMED))
        }
        Choice(stringResource(R.string.icons_colour), appearance.iconStyle == IconStyle.COLOUR) {
            vm.setAppearance(appearance.copy(iconStyle = IconStyle.COLOUR))
        }
        Text(stringResource(R.string.icon_size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        listOf(R.string.size_small to 36, R.string.size_medium to 44, R.string.size_large to 52).forEach { (label, size) ->
            Choice(stringResource(label), appearance.iconSizeDp == size) { vm.setAppearance(appearance.copy(iconSizeDp = size)) }
        }

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

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}
