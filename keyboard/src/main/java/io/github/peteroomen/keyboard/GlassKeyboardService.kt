package io.github.peteroomen.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Paint
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
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
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
import io.github.peteroomen.keyboard.engine.Engine
import io.github.peteroomen.keyboard.engine.Found
import io.github.peteroomen.keyboard.model.CharKey
import io.github.peteroomen.keyboard.model.Clip
import io.github.peteroomen.keyboard.model.ClipHistory
import io.github.peteroomen.keyboard.model.Emoji
import io.github.peteroomen.keyboard.model.EngineKey
import io.github.peteroomen.keyboard.model.EnterAction
import io.github.peteroomen.keyboard.model.FieldKind
import io.github.peteroomen.keyboard.model.Fields
import io.github.peteroomen.keyboard.model.Layouts
import io.github.peteroomen.keyboard.model.Page
import io.github.peteroomen.keyboard.model.Placed
import io.github.peteroomen.keyboard.model.Prediction
import io.github.peteroomen.keyboard.model.Shift
import io.github.peteroomen.keyboard.model.ShiftLogic
import io.github.peteroomen.keyboard.model.Text
import io.github.peteroomen.keyboard.model.Words
import io.github.peteroomen.keyboard.ui.Feedback
import io.github.peteroomen.keyboard.ui.KeyboardActions
import io.github.peteroomen.keyboard.ui.KeyboardScreen
import io.github.peteroomen.keyboard.ui.KeyboardState
import io.github.peteroomen.keyboard.ui.Look
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    // The keyboard in use is allowed to read the clipboard; every copy is offered in the toolbar
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { readClipboard() }
    private lateinit var prefs: KeyboardPrefs
    private var view: View? = null
    private var blurOn = true
    /** The app being typed in, for its colour */
    private var appPackage: String? = null
    private val appAccents = HashMap<String, Int?>()
    private val blurListener = Consumer<Boolean> { on ->
        blurOn = on
        refreshLook()
    }

    /** When shift was last tapped, to tell a double tap (caps lock) */
    private var lastShiftTap = 0L
    /** When space was last typed by us, for the double-space full stop */
    private var lastSpace = 0L

    /** The word under way: composing text in the app, provisional until it's finished */
    private val word = StringBuilder()
    /** Where each of its letters was touched, in keyboard pixels (-1 where not known) */
    private val touchXs = ArrayList<Int>()
    private val touchYs = ArrayList<Int>()

    /**
     * The suggestion engine (AOSP LatinIME's), on its own thread: it isn't thread-safe and a query
     * takes a few milliseconds. Null until it's open, or if it can't run; then the spell checker
     * is used instead.
     */
    private val engineThread = Executors.newSingleThreadExecutor()
    @Volatile private var engine: Engine? = null
    /** Bumped for every query, so an answer that's been overtaken is dropped */
    private var engineRequest = 0
    /** The word the strip's suggestions are the engine's answer for (not just the word shown while it thinks) */
    private var answered: String? = null

    /** Runs [work] on the engine's thread, unless the keyboard is shutting down. */
    private fun onEngineThread(work: () -> Unit) {
        if (!engineThread.isShutdown) engineThread.execute(work)
    }
    /** This field gets composing text and suggestions (plain text, not passwords or addresses) */
    private var composes = false
    private var suggests = false
    /** When we last changed the text, to tell our own selection updates from the person moving the cursor */
    private var lastEditAt = 0L
    /**
     * The last autocorrect, which a backspace straight after undoes: the text it left before the
     * cursor, and what goes back (the typed word, and the punctuation that ended it; not a space)
     */
    private var correction: Pair<String, String>? = null

    private var spell: SpellCheckerSession? = null
    private var spellRequest = 0
    private val spellWords = HashMap<Int, String>()
    private val spellListener = object : SpellCheckerSession.SpellCheckerSessionListener {
        @Deprecated("Deprecated in Java")
        override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) = Unit
        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
            val sentence = results?.firstOrNull() ?: return
            if (sentence.suggestionsCount == 0) return
            val info = sentence.getSuggestionsInfoAt(0)
            val asked = spellWords.remove(info.sequence) ?: return
            // Only the answer for the word still being typed matters
            if (asked != word.toString()) return
            val attrs = info.suggestionsAttributes
            val found = (0 until info.suggestionsCount.coerceAtLeast(0)).map { info.getSuggestionAt(it) }
            state.suggestions = Words.strip(
                asked,
                found,
                looksLikeTypo = attrs and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0,
                recommended = attrs and SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS != 0,
            )
        }
    }

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
        state.recentEmoji = prefs.recentEmoji
        // The emoji list, less any this phone's font can't draw (checking takes a moment, so off the main thread)
        Thread {
            val paint = Paint()
            val categories = Emoji.parse(assets.open("emoji.txt").bufferedReader().use { it.readText() }) { paint.hasGlyph(it) }
            mainExecutor.execute { state.emojiCategories = categories }
        }.start()
        clipboard.addPrimaryClipChangedListener(clipListener)
        onEngineThread { engine = Engine.open(this) }
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
        appPackage = info.packageName
        state.emojiKey = prefs.emojiKey
        state.keyPopup = prefs.keyPopup
        refreshLook()
        // Whatever was left provisional is kept as it is, so the next letter can't replace it
        currentInputConnection?.finishComposingText()
        forgetWord()
        composes = Words.composes(info.inputType)
        suggests = Words.suggests(info.inputType) && prefs.suggestions
        if (suggests && spell == null) spell = openSpellChecker()
        state.field = Fields.kind(info.inputType)
        state.enter = Fields.enterAction(info.imeOptions, info.inputType)
        if (!restarting) {
            state.page = Layouts.firstPage(state.field)
            state.shift = Shift.OFF
            state.emoji = false
        }
        // Catches copies made while the keyboard wasn't running, and lets old ones expire
        readClipboard()
        autoCapitalise()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The cursor moved away from the word under way (a tap elsewhere in the text): leave the word
        // as it is. Updates just after our own edits are those edits arriving, not a move.
        val ours = SystemClock.uptimeMillis() - lastEditAt < 250
        val offWord = candidatesStart < 0 || newSelStart != newSelEnd || newSelEnd != candidatesEnd
        if (word.isNotEmpty() && !ours && offWord) {
            currentInputConnection?.finishComposingText()
            forgetWord()
        }
        if (!ours) {
            correction = null
            if (word.isEmpty()) state.suggestions = null
        }
        autoCapitalise()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        forgetWord()
        state.suggestions = null
        correction = null
        onEngineThread { engine?.flush() }
    }

    override fun onDestroy() {
        onEngineThread {
            engine?.close()
            engine = null
        }
        engineThread.shutdown()
        spell?.close()
        clipboard.removePrimaryClipChangedListener(clipListener)
        getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(blurListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    /**
     * Colours for the current light or dark choice and whether blur is on; the tint is the window's
     * own background. The accent is the app's own colour if it has one (and that's chosen), else
     * the system's.
     */
    private fun refreshLook() {
        val dark = prefs.dark(this)
        val app = appPackage?.takeIf { prefs.accent == AccentChoice.APP && it != packageName }
            ?.let { appAccents.getOrPut(it) { AppColour.of(this, it) } }
        val accent = Color(app ?: getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600))
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

    /** Android's spell checker (on a Pixel, usually Gboard's), in the language chosen for it in Settings. */
    private fun openSpellChecker(): SpellCheckerSession? {
        val params = SpellCheckerSession.SpellCheckerSessionParams.Builder()
            .setShouldReferToSpellCheckerLanguageSettings(true)
            .setSupportedAttributes(
                SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY or
                    SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or
                    SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS,
            )
            .build()
        return runCatching {
            getSystemService(TextServicesManager::class.java)?.newSpellCheckerSession(params, mainExecutor, spellListener)
        }.getOrNull()
    }

    /** The text before the word under way, as context for the engine. */
    private fun textBeforeWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(64 + word.length, 0)?.toString() ?: ""
        return before.dropLast(word.length)
    }

    /** Suggestions for the word under way: from the engine, or the spell checker if it can't run. */
    private fun askForSuggestions() {
        if (!suggests || word.isEmpty()) return
        val asking = word.toString()
        // The strip shows the word straight away, keeping the last suggestions until new ones come
        state.suggestions = Words.typing(state.suggestions, asking)
        val engine = engine ?: return askSpellChecker(asking)
        val request = ++engineRequest
        val xs = touchXs.toIntArray()
        val ys = touchYs.toIntArray()
        val context = Prediction.context(textBeforeWord())
        val threshold = prefs.autocorrect.threshold
        onEngineThread {
            val found = engine.suggest(asking, xs, ys, context, threshold)
            mainExecutor.execute {
                if (request != engineRequest || word.toString() != asking) return@execute
                state.suggestions = engineStrip(asking, found)
                answered = asking
            }
        }
    }

    private fun engineStrip(typed: String, found: Found) =
        Words.strip(typed, found.words, looksLikeTypo = found.autocorrect != null, recommended = found.autocorrect != null)

    /**
     * The fix a space or punctuation puts in for [typed], if any. Typing fast, the space often
     * comes before the engine has answered for the last letter; then it's asked here and waited
     * for (it takes a few milliseconds), so every word gets checked, as in Gboard.
     */
    private fun fixFor(typed: String): String? {
        if (prefs.autocorrect == AutocorrectLevel.OFF) return null
        val strip = state.suggestions?.takeIf { it.typed == typed }
        val engine = engine ?: return strip?.autocorrect // the spell checker's answer, if it's come
        if (!suggests) return null
        if (answered == typed) return strip?.autocorrect
        engineRequest++ // whatever is on its way is overtaken
        val xs = touchXs.toIntArray()
        val ys = touchYs.toIntArray()
        val context = Prediction.context(textBeforeWord())
        val threshold = prefs.autocorrect.threshold
        val found = runCatching {
            engineThread.submit(Callable { engine.suggest(typed, xs, ys, context, threshold) }).get(FIX_WAIT_MS, TimeUnit.MILLISECONDS)
        }.getOrNull() ?: return null
        return engineStrip(typed, found).autocorrect
    }

    /** After a word and a space: the likeliest next words, in the strip. */
    private fun askForPredictions() {
        val engine = engine ?: return
        if (!suggests) return
        val request = ++engineRequest
        val context = Prediction.context(textBeforeWord())
        onEngineThread {
            val next = engine.predict(context)
            mainExecutor.execute {
                if (request != engineRequest || word.isNotEmpty()) return@execute
                state.suggestions = if (next.isEmpty()) null else Words.predictions(next)
            }
        }
    }

    /** Teaches the engine a finished word, with the text that came before it. */
    private fun learn(finished: String, before: String, deliberate: Boolean) {
        val engine = engine ?: return
        if (!suggests) return
        val context = Prediction.context(before)
        onEngineThread { engine.learn(finished, context, deliberate) }
    }

    private fun askSpellChecker(asking: String) {
        val session = spell ?: return
        spellRequest++
        spellWords[spellRequest] = asking
        session.getSentenceSuggestions(arrayOf(TextInfo(asking, 0, asking.length, 0, spellRequest)), 3)
    }

    private fun forgetWord() {
        word.clear()
        touchXs.clear()
        touchYs.clear()
        engineRequest++
        spellWords.clear()
        answered = null
        state.suggestions = null
    }

    /**
     * Ends the word under way: as typed, or, if [autocorrect] and the spell checker was sure of a
     * fix, with the fix. Returns the fix if it made one.
     */
    private fun finishWord(autocorrect: Boolean): String? {
        if (word.isEmpty()) return null
        val ic = currentInputConnection
        val typed = word.toString()
        val before = textBeforeWord()
        val fix = if (autocorrect) fixFor(typed) else null
        if (fix != null) ic?.commitText(fix, 1) else ic?.finishComposingText()
        lastEditAt = SystemClock.uptimeMillis()
        forgetWord()
        learn(fix ?: typed, before, deliberate = false)
        return fix
    }

    override fun type(text: String, x: Float, y: Float) {
        val ic = currentInputConnection ?: return
        lastEditAt = SystemClock.uptimeMillis()
        correction = null
        val c = text.singleOrNull()
        if (composes && c != null && Words.isWordChar(c, word.isNotEmpty())) {
            word.append(text)
            touchXs += if (x.isNaN()) -1 else x.toInt()
            touchYs += if (y.isNaN()) -1 else y.toInt()
            ic.setComposingText(word, 1)
            askForSuggestions()
        } else {
            val typed = word.toString()
            val fix = finishWord(autocorrect = c != null && Words.endsSentenceOrClause(c))
            ic.commitText(text, 1)
            if (fix != null) correction = fix + text to typed + text
        }
        state.shift = ShiftLogic.afterTyping(state.shift)
        autoCapitalise()
    }

    override fun pickSuggestion(suggestion: String) {
        val ic = currentInputConnection ?: return
        lastEditAt = SystemClock.uptimeMillis()
        val typed = word.toString()
        val before = textBeforeWord()
        ic.commitText("$suggestion ", 1)
        forgetWord()
        correction = null
        lastSpace = 0L
        // Keeping the word as typed says it's a real word: it's learned as one straight away
        learn(suggestion, before, deliberate = suggestion == typed)
        askForPredictions()
        autoCapitalise()
    }

    override fun backspace() {
        val ic = currentInputConnection ?: return
        lastEditAt = SystemClock.uptimeMillis()
        if (rawKeys) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        if (word.isNotEmpty()) {
            word.setLength(word.length - Text.lastCharacterLength(word))
            while (touchXs.size > word.length) {
                touchXs.removeAt(touchXs.lastIndex)
                touchYs.removeAt(touchYs.lastIndex)
            }
            ic.setComposingText(word, 1)
            if (word.isEmpty()) {
                forgetWord()
                state.suggestions = null
            } else {
                askForSuggestions()
            }
            autoCapitalise()
            return
        }
        // Straight after an autocorrect: back to what was typed
        val undo = correction
        correction = null
        if (undo != null) {
            val (fixed, restored) = undo
            if (ic.getTextBeforeCursor(fixed.length, 0)?.toString() == fixed) {
                ic.deleteSurroundingText(fixed.length, 0)
                ic.commitText(restored, 1)
                // Undoing a fix says the word was meant: learned as a real word straight away
                val meant = restored.takeWhile { Words.isWordChar(it, inWord = true) }
                val before = ic.getTextBeforeCursor(64 + restored.length, 0)?.toString()?.dropLast(restored.length) ?: ""
                learn(meant, before, deliberate = true)
                state.suggestions = null
                autoCapitalise()
                return
            }
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
        val typed = word.toString()
        val fix = finishWord(autocorrect = true)
        lastEditAt = SystemClock.uptimeMillis()
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
        correction = fix?.let { "$it " to typed }
        // Like Gboard: a space after symbols goes back to the letters
        if (state.page == Page.SYMBOLS || state.page == Page.MORE_SYMBOLS) state.page = Page.LETTERS
        if (composes) askForPredictions() else state.suggestions = null
        autoCapitalise()
    }

    override fun enter() {
        val ic = currentInputConnection ?: return
        finishWord(autocorrect = false)
        correction = null
        state.suggestions = null
        if (state.enter == EnterAction.RETURN) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            ic.performEditorAction(Fields.editorAction(state.enter))
        }
    }

    override fun moveCursor(steps: Int) {
        finishWord(autocorrect = false)
        correction = null
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

    override fun keysPlaced(page: Page, keys: List<Placed>, width: Float, height: Float, keyWidth: Float, rowHeight: Float) {
        if (page != Page.LETTERS) return
        // The letter keys, and their cells (the touch areas the engine works from)
        val letters = keys.mapNotNull { p ->
            val c = (p.key as? CharKey)?.text?.singleOrNull()?.takeIf { it.isLetter() } ?: return@mapNotNull null
            EngineKey(c.code, p.cell)
        }
        onEngineThread { engine?.setKeys(letters, width.toInt(), height.toInt(), keyWidth.toInt(), rowHeight.toInt()) }
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

    override fun showEmoji(show: Boolean) {
        state.emoji = show
        if (!show) autoCapitalise()
    }

    override fun typeEmoji(emoji: String) {
        finishWord(autocorrect = false)
        correction = null
        currentInputConnection?.commitText(emoji, 1) ?: return
        val recents = Emoji.used(state.recentEmoji, emoji)
        state.recentEmoji = recents
        prefs.recentEmoji = recents
    }

    override fun paste(clip: Clip) {
        finishWord(autocorrect = false)
        correction = null
        currentInputConnection?.commitText(clip.text, 1)
        autoCapitalise()
    }

    override fun forgetClip(clip: Clip) {
        state.clips = state.clips - clip
    }

    /** The current copy, added to the recent ones (kept in memory only, and only for an hour). */
    private fun readClipboard() {
        val now = System.currentTimeMillis()
        val clip = runCatching { clipboard.primaryClip }.getOrNull()
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        state.clips = if (text == null) {
            ClipHistory.current(state.clips, now)
        } else {
            val description = clip.description
            val sensitive = description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            // When it was copied, so a copy from an hour ago isn't offered as new. Read either as a
            // wall-clock time or as time since boot, whichever it plausibly is.
            val stamp = description.timestamp
            val at = when {
                stamp > EPOCH_2020 -> stamp
                stamp > 0 -> now - (SystemClock.elapsedRealtime() - stamp)
                else -> now
            }.coerceAtMost(now)
            ClipHistory.add(state.clips, Clip(text, sensitive, at), now)
        }
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

    private companion object {
        const val EPOCH_2020 = 1_577_836_800_000L
        /** The longest a space waits for the engine's verdict on the word before it */
        const val FIX_WAIT_MS = 250L
    }
}
