package io.github.peteroomen.keyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.keyboard.R
import io.github.peteroomen.keyboard.model.Clip
import io.github.peteroomen.keyboard.model.ClipHistory
import io.github.peteroomen.keyboard.model.EmojiCategory
import kotlinx.coroutines.launch

private const val RecentName = "Recent"

/** The tab glyph for each category: one of its own emoji, as Gboard's tabs are pictures of their category. */
private val TabGlyphs = mapOf(
    "Smileys and people" to "😀",
    "Animals and nature" to "🐻",
    "Food and drink" to "🍔",
    "Travel and places" to "🚗",
    "Activities" to "⚽",
    "Objects" to "💡",
    "Symbols" to "🔣",
    "Flags" to "🏳️",
)

/**
 * Emoji in place of the keys, like Gboard's: every category in one scrolling grid, with tabs along
 * the bottom between ABC (back to the keys) and backspace. Recently typed emoji come first; they're
 * fixed while the panel is open, so the grid doesn't jump under the finger.
 */
@Composable
fun EmojiPanel(state: KeyboardState, look: Look, actions: KeyboardActions, modifier: Modifier = Modifier) {
    val act by rememberUpdatedState(actions)
    val recents = remember { state.recentEmoji }
    val sections = remember(state.emojiCategories) {
        (if (recents.isNotEmpty()) listOf(EmojiCategory(RecentName, recents)) else emptyList()) + state.emojiCategories
    }
    // Where each section's heading sits in the grid
    val starts = remember(sections) { sections.runningFold(0) { at, s -> at + 1 + s.emoji.size }.dropLast(1) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val current by remember(starts) { derivedStateOf { starts.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0) } }

    Column(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            state = grid,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            sections.forEach { section ->
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    Text(
                        section.name,
                        color = look.hint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                items(section.emoji.size, contentType = { "emoji" }) { i ->
                    val emoji = section.emoji[i]
                    Box(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                act.feedback(Feedback.KEY)
                                act.typeEmoji(emoji)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 26.sp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(RowHeight)) {
            PanelKey(look, Modifier.weight(1.5f), onClick = { act.showEmoji(false) }) {
                Text("ABC", color = look.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.weight(7f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                sections.forEachIndexed { i, section ->
                    val selected = i == current
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { scope.launch { grid.scrollToItem(starts[i]) } }
                            .drawBehind {
                                // The open category: a short accent bar under its tab
                                if (selected) {
                                    val w = 16.dp.toPx()
                                    drawLine(look.accent, Offset(size.width / 2 - w / 2, size.height - 8.dp.toPx()), Offset(size.width / 2 + w / 2, size.height - 8.dp.toPx()), strokeWidth = 2.5.dp.toPx())
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val glyph = TabGlyphs[section.name]
                        Box(Modifier.graphicsLayer { alpha = if (selected) 1f else 0.5f }) {
                            if (glyph == null) Icon(R.drawable.ic_recent, look.text, 20.dp) else Text(glyph, fontSize = 18.sp)
                        }
                    }
                }
            }
            PanelKey(
                look,
                Modifier.weight(1.5f).pointerInput(Unit) {
                    // Like the keys' backspace: once on touch, then repeating while held
                    awaitEachGesture {
                        awaitFirstDown()
                        act.feedback(Feedback.DELETE)
                        act.backspace()
                        var wait = 400L
                        while (withTimeoutOrNull(wait) { waitForUpOrCancellation(); true } == null) {
                            act.backspace()
                            wait = 50L
                        }
                    }
                },
            ) { Icon(R.drawable.ic_backspace, look.text, 22.dp) }
        }
    }
}

/** A function key in the emoji panel's bottom row, drawn like the keyboard's. */
@Composable
private fun PanelKey(look: Look, modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .padding(horizontal = 3.dp, vertical = 5.dp)
            .keyGlass(look, look.functionFill, held = false)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(KeyCorner)).clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Recent copies as glass chips in the toolbar, newest first: tap one to paste it, hold it to
 * forget it. Something copied in the last minute is in the accent colour, ready to paste.
 */
@Composable
fun ClipChips(clips: List<Clip>, look: Look, actions: KeyboardActions, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    LazyRow(
        modifier
            .fillMaxHeight()
            // Chips running past either end fade out rather than being cut off
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 16.dp.toPx() / size.width
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, fade to Color.Black, 1f - fade to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(clips, key = { "${it.at}:${it.text.hashCode()}" }) { clip ->
            val fresh = now - clip.at < ClipHistory.FRESH_MS
            val ink = if (fresh) look.onAccent else look.text
            Row(
                Modifier
                    .height(32.dp)
                    .widthIn(max = 168.dp)
                    .keyGlass(look, if (fresh) look.accent else look.keyFill, held = false, corner = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = { actions.paste(clip) }, onLongClick = { actions.forgetClip(clip) })
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(R.drawable.ic_paste, ink, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(ClipHistory.label(clip), color = ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
