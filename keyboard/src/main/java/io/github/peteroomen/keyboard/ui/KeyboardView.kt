package io.github.peteroomen.keyboard.ui

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ColorFilter
import io.github.peteroomen.keyboard.R
import io.github.peteroomen.keyboard.model.Action
import io.github.peteroomen.keyboard.model.ActionKey
import io.github.peteroomen.keyboard.model.CharKey
import io.github.peteroomen.keyboard.model.Clip
import io.github.peteroomen.keyboard.model.EmojiCategory
import io.github.peteroomen.keyboard.model.EnterAction
import io.github.peteroomen.keyboard.model.FieldKind
import io.github.peteroomen.keyboard.model.Geometry
import io.github.peteroomen.keyboard.model.Layouts
import io.github.peteroomen.keyboard.model.Page
import io.github.peteroomen.keyboard.model.Placed
import io.github.peteroomen.keyboard.model.Shift
import io.github.peteroomen.keyboard.model.Strip
import io.github.peteroomen.keyboard.model.Text as TextLogic
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Everything the keyboard shows that the service changes. */
class KeyboardState {
    var page by mutableStateOf(Page.LETTERS)
    var field by mutableStateOf(FieldKind.TEXT)
    var shift by mutableStateOf(Shift.OFF)
    var enter by mutableStateOf(EnterAction.RETURN)
    var look by mutableStateOf(Look(dark = true, blur = true, accent = Color(0xFF9CCAFF)))
    /** The emoji panel is showing in place of the keys */
    var emoji by mutableStateOf(false)
    var emojiCategories by mutableStateOf<List<EmojiCategory>>(emptyList())
    var recentEmoji by mutableStateOf<List<String>>(emptyList())
    /** Suggestions for the word being typed, shown in the toolbar in place of its buttons */
    var suggestions by mutableStateOf<Strip?>(null)
    /** Recent copies, newest first, offered in the toolbar */
    var clips by mutableStateOf<List<Clip>>(emptyList())

    /** What a character key types right now: capitals while shifted. */
    fun label(key: CharKey) = if (page == Page.LETTERS && shift != Shift.OFF) TextLogic.shifted(key.text) else key.text
    fun alternates(key: CharKey) = if (page == Page.LETTERS && shift != Shift.OFF) key.alternates.map(TextLogic::shifted) else key.alternates
}

enum class Feedback { KEY, DELETE, SPACE, RETURN, TICK }

/** What the keys do: the service turns these into edits in the app being typed in. */
interface KeyboardActions {
    /** Types [text]; [x], [y] is where the key was touched, in keyboard pixels (NaN if not from a touch). */
    fun type(text: String, x: Float = Float.NaN, y: Float = Float.NaN)
    fun backspace()
    fun space()
    fun enter()
    fun moveCursor(steps: Int)
    fun shiftTap()
    /** Shift was held while letters were typed, and let go: back to lowercase. */
    fun shiftHeldAndReleased()
    fun page(page: Page)
    /** The keys as laid out now: for the suggestion engine, which works from where they are. */
    fun keysPlaced(page: Page, keys: List<Placed>, width: Float, height: Float, keyWidth: Float, rowHeight: Float)
    fun hide()
    fun pickKeyboard()
    fun openSettings()
    fun showEmoji(show: Boolean)
    fun typeEmoji(emoji: String)
    fun paste(clip: Clip)
    fun pickSuggestion(suggestion: String)
    fun forgetClip(clip: Clip)
    fun feedback(kind: Feedback)
}

internal val StripHeight = 44.dp
internal val RowHeight = 58.dp
private val SidePadding = 3.dp
private val GapX = 3.dp
private val GapY = 5.dp
internal val KeyCorner = 10.dp

private const val LongPressMs = 350L
private const val PickerPressMs = 600L
private const val RepeatStartMs = 400L
private const val RepeatEveryMs = 50L

/** The accents row over a held key: its options, where each sits, and which is picked. */
private class AltPopup(val key: Placed, val options: List<String>, val cells: List<Float>, val top: Float, val cellWidth: Float, val cellHeight: Float) {
    var selected by mutableStateOf(0)
    fun pick(x: Float) {
        selected = cells.indices.minByOrNull { abs(cells[it] + cellWidth / 2 - x) } ?: 0
    }
}

/** One finger on the keys. */
private class Press(var placed: Placed, var downAt: Long, val downX: Float) {
    /** Where the finger is now, to tell the engine where the key was touched */
    var x = downX
    var y = 0f
    /** Already acted on (typed by a following key, used for the keyboard picker): nothing on release */
    var done = false
    var popup: AltPopup? = null
    var nextRepeat = 0L
    var sliding = false
    var slideAnchor = 0f
    var typedWhileHeld = false
}

@Composable
fun KeyboardScreen(state: KeyboardState, actions: KeyboardActions) {
    val look = state.look
    val density = LocalDensity.current
    val act by rememberUpdatedState(actions)
    BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
        val side = with(density) { SidePadding.toPx() }
        val rowHeight = with(density) { RowHeight.toPx() }
        val stripHeight = with(density) { StripHeight.toPx() }
        val keysWidth = constraints.maxWidth - side * 2
        val layout = remember(state.page, state.field) { Layouts.build(state.page, state.field) }
        val placed = remember(layout, keysWidth, rowHeight) { Geometry.place(layout, keysWidth, rowHeight) }
        val pressed = remember(placed) { mutableStateListOf<Placed>() }
        var preview by remember(placed) { mutableStateOf<Placed?>(null) }
        var popup by remember(placed) { mutableStateOf<AltPopup?>(null) }
        val unit = keysWidth / Layouts.ROW_WIDTH
        LaunchedEffect(placed) {
            act.keysPlaced(state.page, placed, keysWidth, rowHeight * layout.rows.size, unit, rowHeight)
        }

        Column(
            Modifier.fillMaxWidth().drawBehind {
                // The glass's top edge: a hairline catching the light, and a soft glow under it
                drawRect(Brush.verticalGradient(0f to look.glow, 1f to Color.Transparent, endY = 28.dp.toPx()))
                drawRect(look.edge, size = size.copy(height = 1.dp.toPx()))
            },
        ) {
            Strip(state, look, act)
            if (state.emoji) {
                EmojiPanel(state, look, act, Modifier.padding(horizontal = SidePadding).fillMaxWidth().height(RowHeight * layout.rows.size))
            } else Box(
                Modifier
                    .padding(horizontal = SidePadding)
                    .fillMaxWidth()
                    .height(RowHeight * layout.rows.size)
                    .pointerInput(placed) {
                        val slop = 10.dp.toPx()
                        val step = 9.dp.toPx()
                        val gapY = GapY.toPx()

                        fun openPopup(press: Press) {
                            val key = press.placed.key as CharKey
                            val options = state.alternates(key)
                            val cellWidth = minOf(unit, 44.dp.toPx())
                            val cellHeight = rowHeight * 0.85f
                            val cell = press.placed.cell
                            val start = cell.centerX - cellWidth / 2
                            val width = options.size * cellWidth
                            // The first option sits over the key; the rest run right, or left near the right edge
                            var cells = if (start + width <= keysWidth) {
                                options.indices.map { start + it * cellWidth }
                            } else {
                                options.indices.map { start - it * cellWidth }
                            }
                            val shift = when {
                                cells.min() < 0f -> -cells.min()
                                cells.max() + cellWidth > keysWidth -> keysWidth - cellWidth - cells.max()
                                else -> 0f
                            }
                            cells = cells.map { it + shift }
                            val top = (stripHeight + cell.top + gapY + rowHeight * 0.2f - cellHeight).coerceAtLeast(0f)
                            press.popup = AltPopup(press.placed, options, cells, top, cellWidth, cellHeight)
                            popup = press.popup
                            preview = null
                            act.feedback(Feedback.TICK)
                        }

                        fun commit(press: Press) {
                            val key = press.placed.key
                            when {
                                press.popup != null -> act.type(press.popup!!.let { it.options[it.selected] })
                                key is CharKey -> act.type(state.label(key), press.x, press.y)
                                key is ActionKey -> when (key.action) {
                                    Action.SPACE -> if (!press.sliding) act.space()
                                    Action.ENTER -> act.enter()
                                    Action.SYMBOLS -> act.page(Page.SYMBOLS)
                                    Action.MORE_SYMBOLS -> act.page(Page.MORE_SYMBOLS)
                                    Action.LETTERS -> act.page(Page.LETTERS)
                                    Action.SHIFT -> if (press.typedWhileHeld) act.shiftHeldAndReleased()
                                    Action.BACKSPACE -> Unit
                                }
                            }
                        }

                        fun deadline(press: Press): Long? {
                            if (press.done) return null
                            val key = press.placed.key
                            return when {
                                key is CharKey && key.alternates.isNotEmpty() && press.popup == null -> press.downAt + LongPressMs
                                key is ActionKey && key.action == Action.BACKSPACE -> press.nextRepeat
                                key is ActionKey && key.action == Action.SPACE && !press.sliding -> press.downAt + PickerPressMs
                                else -> null
                            }
                        }

                        fun tick(press: Press, now: Long) {
                            val due = deadline(press) ?: return
                            if (now < due) return
                            val key = press.placed.key
                            when {
                                key is CharKey -> openPopup(press)
                                key is ActionKey && key.action == Action.BACKSPACE -> {
                                    act.backspace()
                                    press.nextRepeat = now + RepeatEveryMs
                                }
                                key is ActionKey && key.action == Action.SPACE -> {
                                    press.done = true
                                    act.pickKeyboard()
                                }
                            }
                        }

                        awaitPointerEventScope {
                            val presses = LinkedHashMap<PointerId, Press>()
                            while (true) {
                                val due = presses.values.mapNotNull(::deadline).minOrNull()
                                val event = if (due == null) {
                                    awaitPointerEvent()
                                } else {
                                    withTimeoutOrNull((due - SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                                }
                                val now = SystemClock.uptimeMillis()
                                presses.values.toList().forEach { tick(it, now) }
                                if (event == null) continue
                                for (change in event.changes) {
                                    val x = change.position.x
                                    val y = change.position.y
                                    val press = presses[change.id]
                                    when {
                                        change.pressed && !change.previousPressed -> {
                                            val hit = Geometry.hit(placed, x, y) ?: continue
                                            // Fast typing: a new finger types the key the last one is still on
                                            presses.values.filter { !it.done && it.popup == null && it.placed.key is CharKey }.forEach {
                                                commit(it)
                                                it.done = true
                                                pressed.remove(it.placed)
                                                if (preview == it.placed) preview = null
                                            }
                                            val p = Press(hit, now, x).also { it.y = y }
                                            presses[change.id] = p
                                            pressed.add(hit)
                                            val key = hit.key
                                            when {
                                                key is CharKey -> {
                                                    act.feedback(Feedback.KEY)
                                                    if (key.width <= 1.5f) preview = hit
                                                }
                                                key is ActionKey && key.action == Action.SHIFT -> {
                                                    act.feedback(Feedback.KEY)
                                                    act.shiftTap()
                                                }
                                                key is ActionKey && key.action == Action.BACKSPACE -> {
                                                    act.feedback(Feedback.DELETE)
                                                    act.backspace()
                                                    p.nextRepeat = now + RepeatStartMs
                                                }
                                                key is ActionKey && key.action == Action.SPACE -> act.feedback(Feedback.SPACE)
                                                key is ActionKey && key.action == Action.ENTER -> act.feedback(Feedback.RETURN)
                                                else -> act.feedback(Feedback.KEY)
                                            }
                                        }
                                        change.pressed && press != null && !press.done -> {
                                            press.x = x
                                            press.y = y
                                            val key = press.placed.key
                                            val popupNow = press.popup
                                            when {
                                                popupNow != null -> popupNow.pick(x)
                                                key is CharKey -> {
                                                    // Sliding to the next key before letting go types that one instead
                                                    val hit = Geometry.hit(placed, x, y)
                                                    if (hit != null && hit != press.placed && hit.key is CharKey) {
                                                        pressed.remove(press.placed)
                                                        pressed.add(hit)
                                                        press.placed = hit
                                                        press.downAt = now
                                                        preview = hit.takeIf { hit.key.width <= 1.5f }
                                                    }
                                                }
                                                key is ActionKey && key.action == Action.SPACE -> {
                                                    // Slide along the space bar to move the cursor
                                                    if (!press.sliding && abs(x - press.downX) > slop) {
                                                        press.sliding = true
                                                        press.slideAnchor = x
                                                    }
                                                    while (press.sliding && abs(x - press.slideAnchor) >= step) {
                                                        val dir = sign(x - press.slideAnchor)
                                                        act.moveCursor(dir.toInt())
                                                        act.feedback(Feedback.TICK)
                                                        press.slideAnchor += dir * step
                                                    }
                                                }
                                            }
                                        }
                                        !change.pressed && change.previousPressed && press != null -> {
                                            presses.remove(change.id)
                                            pressed.remove(press.placed)
                                            if (preview == press.placed) preview = null
                                            if (press.popup != null && popup == press.popup) popup = null
                                            if (!press.done) {
                                                commit(press)
                                                if (press.placed.key is CharKey) {
                                                    presses.values.filter { (it.placed.key as? ActionKey)?.action == Action.SHIFT }.forEach { it.typedWhileHeld = true }
                                                }
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                            }
                        }
                    },
            ) {
                for (p in placed) {
                    KeyCap(p, state, look, held = p in pressed, bottomRow = p.row == layout.rows.lastIndex)
                }
            }
        }

        // Over everything: the bubble over a held key, or the row of accents
        preview?.let { p ->
            val key = p.key as CharKey
            val keyWidth = p.cell.width - with(density) { GapX.toPx() } * 2
            val width = keyWidth * 1.3f
            val height = rowHeight
            val left = (side + p.cell.centerX - width / 2).coerceIn(0f, constraints.maxWidth - width)
            val top = (stripHeight + p.cell.top + rowHeight * 0.3f - height).coerceAtLeast(0f)
            Bubble(look, left, top, width, height) {
                Text(state.label(key), color = look.text, fontSize = 30.sp)
            }
        }
        popup?.let { alt ->
            for ((i, option) in alt.options.withIndex()) {
                val selected = i == alt.selected
                Bubble(look, side + alt.cells[i], alt.top, alt.cellWidth, alt.cellHeight, fill = if (selected) look.accent else look.bubble, rimmed = i == 0) {
                    Text(option, color = if (selected) look.onAccent else look.text, fontSize = 22.sp)
                }
            }
        }
    }
}

/**
 * The toolbar above the keys, laid out like Gboard's: emoji, settings and switch keyboard on the
 * left, recent copies to paste in the middle, hide on the right. It also gives the bubbles over
 * the top row room to rise into.
 */
@Composable
private fun Strip(state: KeyboardState, look: Look, actions: KeyboardActions) {
    val suggestions = state.suggestions
    if (suggestions != null && !state.emoji) {
        Row(Modifier.fillMaxWidth().height(StripHeight).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Emoji stay one tap away while suggestions fill the toolbar
            ToolbarButton(R.drawable.ic_emoji, look) { actions.showEmoji(true) }
            Box(Modifier.weight(1f)) { SuggestionStrip(suggestions, look) { actions.pickSuggestion(it) } }
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(StripHeight).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolbarButton(R.drawable.ic_emoji, look, on = state.emoji) { actions.showEmoji(!state.emoji) }
        ToolbarButton(R.drawable.ic_settings, look) { actions.openSettings() }
        ToolbarButton(R.drawable.ic_keyboard, look) { actions.pickKeyboard() }
        ClipChips(state.clips, look, actions, Modifier.weight(1f))
        ToolbarButton(R.drawable.ic_keyboard_hide, look) { actions.hide() }
    }
}

@Composable
private fun ToolbarButton(@DrawableRes icon: Int, look: Look, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(StripHeight).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, if (on) look.accent else look.hint, 22.dp)
    }
}

@Composable
private fun KeyCap(p: Placed, state: KeyboardState, look: Look, held: Boolean, bottomRow: Boolean) {
    val density = LocalDensity.current
    val key = p.key
    val action = (key as? ActionKey)?.action
    val accent = action == Action.ENTER || (action == Action.SHIFT && state.shift == Shift.LOCKED)
    val fill = when {
        accent -> look.accent
        // Like Gboard, the punctuation beside the space bar is coloured as a function key
        key is CharKey && bottomRow && state.page != Page.NUMBERS -> look.functionFill
        key is CharKey || action == Action.SPACE -> look.keyFill
        else -> look.functionFill
    }
    val ink = if (accent) look.onAccent else look.text
    val gapX = with(density) { GapX.toPx() }
    val gapY = with(density) { GapY.toPx() }
    Box(
        Modifier
            .offset { IntOffset((p.cell.left + gapX).roundToInt(), (p.cell.top + gapY).roundToInt()) }
            .size(with(density) { (p.cell.width - gapX * 2).toDp() }, with(density) { (p.cell.height - gapY * 2).toDp() })
            .keyGlass(look, fill, held),
        contentAlignment = Alignment.Center,
    ) {
        when {
            key is CharKey -> {
                val big = state.page == Page.LETTERS || state.page == Page.NUMBERS
                Text(state.label(key), color = ink, fontSize = if (big) 24.sp else 21.sp)
                // The number a held top-row letter gives, in its corner
                val digit = key.alternates.firstOrNull()?.takeIf { state.page == Page.LETTERS && it.length == 1 && it[0].isDigit() }
                if (digit != null) {
                    Text(digit, color = look.hint, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp))
                }
            }
            action == Action.SHIFT -> ShiftGlyph(state.shift, ink)
            action == Action.BACKSPACE -> Icon(R.drawable.ic_backspace, ink, 22.dp)
            action == Action.ENTER -> Icon(enterIcon(state.enter), ink, 22.dp)
            action == Action.SPACE -> Unit
            else -> Text(
                when (action) {
                    Action.SYMBOLS -> if (state.page == Page.NUMBERS) "!?#" else "?123"
                    Action.MORE_SYMBOLS -> "=\\<"
                    else -> if (state.field == FieldKind.NUMBER || state.field == FieldKind.PHONE) "123" else "ABC"
                },
                color = ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@DrawableRes
private fun enterIcon(action: EnterAction) = when (action) {
    EnterAction.SEARCH -> R.drawable.ic_search
    EnterAction.SEND -> R.drawable.ic_send
    EnterAction.GO, EnterAction.NEXT -> R.drawable.ic_arrow_forward
    EnterAction.PREVIOUS -> R.drawable.ic_arrow_back
    EnterAction.DONE -> R.drawable.ic_check
    EnterAction.RETURN -> R.drawable.ic_return
}

/** A flat key: its fill and a hairline edge, darker or lighter while held. */
internal fun Modifier.keyGlass(look: Look, fill: Color, held: Boolean, corner: Dp = KeyCorner) = drawBehind {
    val radius = CornerRadius(corner.toPx())
    drawRoundRect(fill, cornerRadius = radius)
    if (held) drawRoundRect(look.pressed, cornerRadius = radius)
    val edge = 1.dp.toPx()
    drawRoundRect(
        look.keyEdge,
        topLeft = Offset(edge / 2, edge / 2),
        size = size.copy(width = size.width - edge, height = size.height - edge),
        cornerRadius = CornerRadius(radius.x - edge / 2),
        style = Stroke(edge),
    )
}

@Composable
private fun Bubble(look: Look, left: Float, top: Float, width: Float, height: Float, fill: Color = look.bubble, rimmed: Boolean = true, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .drawBehind {
                val corner = CornerRadius(12.dp.toPx())
                drawRoundRect(fill, cornerRadius = corner)
                if (rimmed) drawRoundRect(look.keyEdge, cornerRadius = corner, style = Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun Icon(@DrawableRes id: Int, tint: Color, size: Dp) {
    Image(painterResource(id), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
}

/** Shift: an outlined arrow when off, filled for one capital, filled and underlined for caps lock. */
@Composable
private fun ShiftGlyph(shift: Shift, ink: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val arrow = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.92f, h * 0.55f)
            lineTo(w * 0.68f, h * 0.55f)
            lineTo(w * 0.68f, h * 0.78f)
            lineTo(w * 0.32f, h * 0.78f)
            lineTo(w * 0.32f, h * 0.55f)
            lineTo(w * 0.08f, h * 0.55f)
            close()
        }
        drawPath(arrow, ink, style = if (shift == Shift.OFF) Stroke(1.6.dp.toPx()) else Fill)
        if (shift == Shift.LOCKED) {
            drawRect(ink, topLeft = Offset(w * 0.32f, h * 0.86f), size = size.copy(width = w * 0.36f, height = 1.6.dp.toPx()))
        }
    }
}
