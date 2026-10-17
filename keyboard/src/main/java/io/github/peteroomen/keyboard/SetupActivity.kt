package io.github.peteroomen.keyboard

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Turning the keyboard on and switching to it, a box to try it in, and its few settings. */
class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)) {
                Surface(Modifier.fillMaxSize()) { Setup() }
            }
        }
    }
}

@Composable
private fun Setup() {
    val context = LocalContext.current
    val prefs = remember { KeyboardPrefs(context) }
    val imm = remember { context.getSystemService(InputMethodManager::class.java) }
    val id = remember { ComponentName(context, GlassKeyboardService::class.java).flattenToShortString() }
    // Checked again whenever this screen gets focus back: after Settings, or after the keyboard picker closes
    val focused = LocalWindowInfo.current.isWindowFocused
    val enabled = remember(focused) { imm.enabledInputMethodList.any { it.id == id } }
    val chosen = remember(focused) {
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        current != null && ComponentName.unflattenFromString(current)?.flattenToShortString() == id
    }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var look by remember { mutableStateOf(prefs.look) }
    var trial by remember { mutableStateOf("") }

    Column(
        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)

        Step(1, stringResource(R.string.step_enable), done = enabled) {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        Step(2, stringResource(R.string.step_switch), done = chosen, available = enabled) {
            imm.showInputMethodPicker()
        }

        OutlinedTextField(
            value = trial,
            onValueChange = { trial = it },
            label = { Text(stringResource(R.string.try_it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
        Toggle(stringResource(R.string.vibrate), vibrate) { vibrate = it; prefs.vibrate = it }
        Toggle(stringResource(R.string.sound), sound) { sound = it; prefs.sound = it }
        Text(stringResource(R.string.look), style = MaterialTheme.typography.titleSmall)
        listOf(
            LookChoice.SYSTEM to R.string.look_system,
            LookChoice.LIGHT to R.string.look_light,
            LookChoice.DARK to R.string.look_dark,
        ).forEach { (choice, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { look = choice; prefs.look = choice },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = look == choice, onClick = { look = choice; prefs.look = choice })
                Text(stringResource(label))
            }
        }
        Text(stringResource(R.string.blur_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Step(number: Int, text: String, done: Boolean, available: Boolean = true, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (done) "✓" else "$number", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        if (done) {
            OutlinedButton(onClick = onClick, enabled = available, modifier = Modifier.weight(1f)) { Text(text) }
        } else {
            Button(onClick = onClick, enabled = available, modifier = Modifier.weight(1f)) { Text(text) }
        }
    }
}

@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
