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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.launcher.HomeUiState
import io.github.peteroomen.launcher.R
import io.github.peteroomen.launcher.model.Fav
import io.github.peteroomen.launcher.model.Pie
import io.github.peteroomen.launcher.model.PieHit
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where the pie is centred, where the finger is now, and what it's on. */
data class PieState(val center: Offset, val finger: Offset, val hit: PieHit)

val PieRadius = 124.dp
val PieDeadZone = 36.dp
private val RingRadius = 84.dp
/** Past this distance over a folder, the finger picks from the folder's arc. */
val PieFolderRing = 120.dp
private val ArcRadius = 156.dp
/** How far out the pie reaches when it has folders, for keeping it on screen. */
val PieReachWithFolders = ArcRadius + 26.dp
private val PieIconSize = 50.dp
private val ArcIconSize = 42.dp

/**
 * Up to eight favourites in a ring around the finger; the one it's on grows and gets a ring. A
 * folder's apps fan out in an arc beyond it while the finger is on it.
 */
@Composable
fun PieMenu(state: HomeUiState, pie: PieState, slots: List<Fav>) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    val c = pie.center
    val hit = pie.hit
    val radiusPx = with(density) { PieRadius.toPx() }

    fun pointAt(angleDegrees: Double, radius: Dp): Offset {
        val r = with(density) { radius.toPx() }
        val a = Math.toRadians(angleDegrees)
        return Offset(c.x + r * cos(a).toFloat(), c.y - r * sin(a).toFloat())
    }

    @Composable
    fun Placed(at: Offset, size: Dp, selected: Boolean, content: @Composable () -> Unit) {
        val half = with(density) { size.toPx() / 2 }
        Box(
            Modifier
                .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
                .size(size)
                .graphicsLayer {
                    val scale = if (selected) 1.25f else 1f
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            content()
            if (selected) Box(Modifier.fillMaxSize().border(2.dp, palette.accent, palette.iconShape))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(palette.scrim.copy(alpha = palette.scrim.alpha * if (palette.glass) 0.25f else 0.5f)))
        Box(
            Modifier
                .offset { IntOffset((c.x - radiusPx).roundToInt(), (c.y - radiusPx).roundToInt()) }
                .size(PieRadius * 2)
                .panel(CircleShape, palette.pill, rim = 1.5.dp),
        )
        for (slot in 0 until Pie.SLOTS) {
            val fav = slots.getOrNull(slot)
            Placed(pointAt(Pie.angleOf(slot), RingRadius), PieIconSize, selected = hit.slot == slot && hit.folderIndex == null) {
                when (fav) {
                    is Fav.App -> AppIcon(fav.key, state.appearance.iconStyle, PieIconSize, badge = state.hasDot(fav.key))
                    is Fav.Folder -> FolderIcon(fav, state, PieIconSize, open = hit.slot == slot)
                    null -> Box(Modifier.fillMaxSize().border(1.dp, palette.muted.copy(alpha = 0.35f), palette.iconShape))
                }
            }
        }

        // The folder under the finger fans its apps out beyond it
        val openFolder = hit.slot?.let { slots.getOrNull(it) } as? Fav.Folder
        if (openFolder != null) {
            val angles = Pie.arcAngles(hit.slot, openFolder.apps.size)
            openFolder.apps.forEachIndexed { i, key ->
                Placed(pointAt(angles[i], ArcRadius), ArcIconSize, selected = hit.folderIndex == i) {
                    AppIcon(key, state.appearance.iconStyle, ArcIconSize, badge = state.hasDot(key))
                }
            }
        }

        // Centre: let go here for the home menu
        Box(
            Modifier
                .offset { IntOffset((c.x - 6.dp.toPx()).roundToInt(), (c.y - 6.dp.toPx()).roundToInt()) }
                .size(12.dp)
                .background(if (hit.slot == null) palette.accent else palette.muted, CircleShape),
        )

        val fav = hit.slot?.let { slots.getOrNull(it) }
        val label = when {
            hit.slot == null -> stringResource(R.string.pie_centre_hint)
            fav is Fav.Folder && hit.folderIndex != null -> state.label(fav.apps[hit.folderIndex])
            fav is Fav.Folder -> stringResource(R.string.pie_folder_hint, fav.name)
            fav is Fav.App -> state.label(fav.key)
            else -> ""
        }
        val labelWidth = 280.dp
        val labelTop = if (openFolder != null) ArcRadius + 48.dp else PieRadius + 44.dp
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (c.x - labelWidth.toPx() / 2).roundToInt(),
                        (c.y - labelTop.toPx()).roundToInt().coerceAtLeast(48.dp.roundToPx()),
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
