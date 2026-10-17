package io.github.peteroomen.keyboard

import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.peteroomen.keyboard.model.EnterAction
import io.github.peteroomen.keyboard.model.FieldKind
import io.github.peteroomen.keyboard.model.Fields
import io.github.peteroomen.keyboard.model.Layouts
import io.github.peteroomen.keyboard.model.Page
import io.github.peteroomen.keyboard.model.Shift
import io.github.peteroomen.keyboard.model.ShiftLogic
import io.github.peteroomen.keyboard.model.Text
import io.github.peteroomen.keyboard.ui.Feedback
import io.github.peteroomen.keyboard.ui.KeyboardActions
import io.github.peteroomen.keyboard.ui.KeyboardScreen
import io.github.peteroomen.keyboard.ui.KeyboardState
import io.github.peteroomen.keyboard.ui.Look
import java.util.function.Consumer

/**
 * The keyboard. Its window is see-through and asks the system to blur whatever is behind it (the
 * app being typed in), so the keys sit on frosted glass over the app. Compose draws the keys, so
 * the service is its own lifecycle and saved-state owner, as an activity would be.
 */
class GlassKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, KeyboardActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val state = KeyboardState()
    private lateinit var prefs: KeyboardPrefs
    private var view: View? = null
    private var blurOn = true
    private val blurListener = Consumer<Boolean> { on ->
        blurOn = on
        refreshLook()
    }

    /** When shift was last tapped, to tell a double tap (caps lock) */
    private var lastShiftTap = 0L
    /** When space was last typed by us, for the double-space full stop */
    private var lastSpace = 0L

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        prefs = KeyboardPrefs(this)
        window.window?.let { w ->
            w.setFormat(PixelFormat.TRANSLUCENT)
            @Suppress("DEPRECATION") // still honoured for the IME's own navigation bar area
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            w.isNavigationBarContrastEnforced = false
            w.setBackgroundBlurRadius(resources.getDimensionPixelSize(R.dimen.blur_radius))
        }
        val wm = getSystemService(WindowManager::class.java)
        blurOn = wm.isCrossWindowBlurEnabled
        wm.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
        refreshLook()
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { KeyboardScreen(state, this@GlassKeyboardService) }
        }.also { view = it }
    }

    /** Never the full-screen editor (landscape): the keyboard is glass over the app, not a page of its own. */
    override fun onEvaluateFullscreenMode() = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshLook()
        state.field = Fields.kind(info.inputType)
        state.enter = Fields.enterAction(info.imeOptions, info.inputType)
        if (!restarting) {
            state.page = Layouts.firstPage(state.field)
            state.shift = Shift.OFF
        }
        autoCapitalise()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        autoCapitalise()
    }

    override fun onDestroy() {
        getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(blurListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    /** Colours for the current light or dark choice and whether blur is on; the tint is the window's own background. */
    private fun refreshLook() {
        val dark = prefs.dark(this)
        val accent = Color(getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600))
        val look = Look(dark, blurOn, accent)
        state.look = look
        window.window?.setBackgroundDrawable(ColorDrawable(look.tint.toArgb()))
    }

    /** A capital at the start of a sentence, where the field asks for it. Caps lock is left alone. */
    private fun autoCapitalise() {
        if (state.shift == Shift.LOCKED || state.page != Page.LETTERS) return
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        state.shift = if (caps != 0) Shift.ONCE else Shift.OFF
    }

    private val rawKeys get() = (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL

    override fun type(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        state.shift = ShiftLogic.afterTyping(state.shift)
        autoCapitalise()
    }

    override fun backspace() {
        val ic = currentInputConnection ?: return
        if (rawKeys) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(16, 0) ?: ""
            if (before.isEmpty()) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) // lets the app see it, e.g. to step back a field
            else ic.deleteSurroundingText(Text.lastCharacterLength(before), 0)
        }
        autoCapitalise()
    }

    override fun space() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(2, 0) ?: ""
        val textField = state.field == FieldKind.TEXT
        if (textField && now - lastSpace < 600 && Text.doubleSpacePeriod(before)) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpace = 0L
        } else {
            ic.commitText(" ", 1)
            lastSpace = now
        }
        autoCapitalise()
    }

    override fun enter() {
        val ic = currentInputConnection ?: return
        if (state.enter == EnterAction.RETURN) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            ic.performEditorAction(Fields.editorAction(state.enter))
        }
    }

    override fun moveCursor(steps: Int) {
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(key) }
    }

    override fun shiftTap() {
        val now = SystemClock.uptimeMillis()
        state.shift = ShiftLogic.tap(state.shift, doubleTap = now - lastShiftTap < 400)
        lastShiftTap = now
    }

    override fun shiftHeldAndReleased() {
        if (state.shift != Shift.LOCKED) state.shift = Shift.OFF
    }

    override fun page(page: Page) {
        // Number fields go back to the number pad, not the letters
        val numeric = state.field == FieldKind.NUMBER || state.field == FieldKind.PHONE
        state.page = if (page == Page.LETTERS && numeric) Page.NUMBERS else page
        if (state.page == Page.LETTERS) autoCapitalise()
    }

    override fun hide() {
        requestHideSelf(0)
    }

    override fun pickKeyboard() {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    override fun openSettings() {
        requestHideSelf(0)
        startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun feedback(kind: Feedback) {
        if (prefs.vibrate) {
            view?.performHapticFeedback(
                if (kind == Feedback.TICK) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
        if (prefs.sound && kind != Feedback.TICK) {
            val audio = getSystemService(AudioManager::class.java)
            if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                audio.playSoundEffect(
                    when (kind) {
                        Feedback.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                        Feedback.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                        Feedback.RETURN -> AudioManager.FX_KEYPRESS_RETURN
                        else -> AudioManager.FX_KEYPRESS_STANDARD
                    },
                    -1f,
                )
            }
        }
    }
}
