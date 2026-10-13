package io.github.peteroomen.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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

/** Each icon starts swinging out this long after the one before it, clockwise from the top. */
private const val StaggerMs = 16L
/** How far round (degrees, anticlockwise) an icon starts before it swings into place. */
private const val SwoopDegrees = 40.0
/** A little overshoot, so icons spring past their spot and settle back. */
private val SwoopSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 380f)
private val SelectSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 600f)

/**
 * Up to eight favourites in a ring around the finger; the one it's on grows and gets a ring. A
 * folder's apps fan out in an arc beyond it while the finger is on it.
 *
 * Nothing just appears: the disc grows out from the finger, the icons swoop out from the centre
 * one after another, curving into place, and a folder's apps fan out from the folder.
 */
@Composable
fun PieMenu(state: HomeUiState, pie: PieState, slots: List<Fav>) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    val c = pie.center
    val hit = pie.hit
    val radiusPx = with(density) { PieRadius.toPx() }

    val disc = remember { Animatable(0f) }
    val ring = remember { List(Pie.SLOTS) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        launch { disc.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
        ring.forEachIndexed { i, a -> launch { delay(i * StaggerMs); a.animateTo(1f, SwoopSpring) } }
    }

    fun pointAt(angleDegrees: Double, radius: Dp): Offset {
        val r = with(density) { radius.toPx() }
        val a = Math.toRadians(angleDegrees)
        return Offset(c.x + r * cos(a).toFloat(), c.y - r * sin(a).toFloat())
    }

    /** [at] and [reveal] are read while drawing, so the swoop doesn't recompose every frame. */
    @Composable
    fun Placed(at: () -> Offset, size: Dp, selected: Boolean, reveal: () -> Float, content: @Composable () -> Unit) {
        val half = with(density) { size.toPx() / 2 }
        val grow by animateFloatAsState(if (selected) 1.25f else 1f, SelectSpring, label = "pie select")
        Box(
            Modifier
                .offset { at().let { IntOffset((it.x - half).roundToInt(), (it.y - half).roundToInt()) } }
                .size(size)
                .graphicsLayer {
                    val p = reveal()
                    val scale = grow * (0.35f + 0.65f * p)
                    scaleX = scale
                    scaleY = scale
                    alpha = p.coerceIn(0f, 1f)
                },
        ) {
            content()
            if (selected) Box(Modifier.fillMaxSize().border(2.dp, palette.accent, palette.iconShape))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = disc.value.coerceIn(0f, 1f) }
                .background(palette.scrim.copy(alpha = palette.scrim.alpha * if (palette.glass) 0.25f else 0.5f)),
        )
        Box(
            Modifier
                .offset { IntOffset((c.x - radiusPx).roundToInt(), (c.y - radiusPx).roundToInt()) }
                .size(PieRadius * 2)
                .graphicsLayer {
                    val p = disc.value
                    scaleX = 0.4f + 0.6f * p
                    scaleY = 0.4f + 0.6f * p
                    alpha = p.coerceIn(0f, 1f)
                }
                .panel(CircleShape, palette.pill),
        )
        for (slot in 0 until Pie.SLOTS) {
            val fav = slots.getOrNull(slot)
            val p = ring[slot]
            Placed(
                // Out from the centre along a curve: it starts a little round and swings into its slot
                at = { pointAt(Pie.angleOf(slot) + (1 - p.value) * SwoopDegrees, RingRadius * p.value) },
                size = PieIconSize,
                selected = hit.slot == slot && hit.folderIndex == null,
                reveal = { p.value },
            ) {
                when (fav) {
                    is Fav.App -> AppIcon(fav.key, state.appearance.iconStyle, PieIconSize, badge = state.hasDot(fav.key))
                    is Fav.Folder -> FolderIcon(fav, state, PieIconSize, open = hit.slot == slot)
                    null -> Box(Modifier.fillMaxSize().border(1.dp, palette.muted.copy(alpha = 0.35f), palette.iconShape))
                }
            }
        }

        // The folder under the finger fans its apps out beyond it, from the folder outwards
        val openFolder = hit.slot?.let { slots.getOrNull(it) } as? Fav.Folder
        if (openFolder != null) key(hit.slot, openFolder.apps.size) {
            val count = openFolder.apps.size
            val fan = remember { List(count) { Animatable(0f) } }
            LaunchedEffect(Unit) {
                fan.forEachIndexed { i, a ->
                    // The middle of the arc first, then outwards to both ends
                    val order = abs(i - (count - 1) / 2.0)
                    launch { delay((order * StaggerMs * 1.5).toLong()); a.animateTo(1f, SwoopSpring) }
                }
            }
            val from = Pie.angleOf(hit.slot)
            val angles = Pie.arcAngles(hit.slot, count)
            openFolder.apps.forEachIndexed { i, key ->
                val p = fan[i]
                Placed(
                    at = { pointAt(from + (angles[i] - from) * p.value, RingRadius + (ArcRadius - RingRadius) * p.value) },
                    size = ArcIconSize,
                    selected = hit.folderIndex == i,
                    reveal = { p.value },
                ) {
                    AppIcon(key, state.appearance.iconStyle, ArcIconSize, badge = state.hasDot(key))
                }
            }
        }

        // Centre: let go here for the home menu
        Box(
            Modifier
                .offset { IntOffset((c.x - 6.dp.toPx()).roundToInt(), (c.y - 6.dp.toPx()).roundToInt()) }
                .size(12.dp)
                .graphicsLayer { alpha = disc.value.coerceIn(0f, 1f) }
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
                .width(labelWidth)
                .graphicsLayer { alpha = disc.value.coerceIn(0f, 1f) },
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
