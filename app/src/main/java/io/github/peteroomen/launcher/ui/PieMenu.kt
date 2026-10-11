package io.github.peteroomen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.model.Pie
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where the pie is centred, and where the finger is now. */
data class PieState(val center: Offset, val finger: Offset)

val PieRadius = 124.dp
val PieDeadZone = 36.dp
private val RingRadius = 84.dp
private val PieIconSize = 50.dp

/** Eight favourites in a ring around the finger; the one it's pointing at grows and gets a ring. */
@Composable
fun PieMenu(state: HomeUiState, pie: PieState, slots: List<String>, selected: Int?) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    val c = pie.center
    val radiusPx = with(density) { PieRadius.toPx() }
    val ringPx = with(density) { RingRadius.toPx() }
    val iconPx = with(density) { PieIconSize.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(palette.scrim.copy(alpha = palette.scrim.alpha * 0.5f)))
        Box(
            Modifier
                .offset { IntOffset((c.x - radiusPx).roundToInt(), (c.y - radiusPx).roundToInt()) }
                .size(PieRadius * 2)
                .background(palette.pill, CircleShape),
        )
        for (slot in 0 until Pie.SLOTS) {
            val angle = Math.toRadians(Pie.angleOf(slot))
            val x = c.x + ringPx * cos(angle).toFloat()
            val y = c.y - ringPx * sin(angle).toFloat()
            val key = slots.getOrNull(slot)
            val isSelected = slot == selected
            Box(
                Modifier
                    .offset { IntOffset((x - iconPx / 2).roundToInt(), (y - iconPx / 2).roundToInt()) }
                    .size(PieIconSize)
                    .graphicsLayer {
                        val scale = if (isSelected) 1.25f else 1f
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                if (key != null) {
                    AppIcon(key, state.appearance.iconStyle, PieIconSize, badge = state.hasDot(key))
                    if (isSelected) Box(Modifier.fillMaxSize().border(2.dp, palette.accent, palette.iconShape))
                } else {
                    Box(Modifier.fillMaxSize().border(1.dp, palette.muted.copy(alpha = 0.35f), palette.iconShape))
                }
            }
        }
        // Centre: let go here for the home menu
        Box(
            Modifier
                .offset { IntOffset((c.x - 6.dp.toPx()).roundToInt(), (c.y - 6.dp.toPx()).roundToInt()) }
                .size(12.dp)
                .background(if (selected == null) palette.accent else palette.muted, CircleShape),
        )
        val label = selected?.let { slots.getOrNull(it) }?.let(state::label)
            ?: if (selected == null) stringResource(R.string.pie_centre_hint) else ""
        val labelWidth = 260.dp
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (c.x - labelWidth.toPx() / 2).roundToInt(),
                        (c.y - radiusPx - 44.dp.toPx()).roundToInt(),
                    )
                }
                .width(labelWidth),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = palette.style(18.sp, FontWeight.Medium),
            )
        }
    }
}
