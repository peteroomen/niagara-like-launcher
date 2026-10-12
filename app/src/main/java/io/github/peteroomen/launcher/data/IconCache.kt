package io.github.peteroomen.launcher.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap

/** App icons as bitmaps: the normal icon, plus the monochrome layer used for themed icons (Android 13+). */
class IconCache(private val context: Context) {

    /**
     * [adaptive] icons are drawn unmasked (full square) so the launcher can cut them to its own icon
     * shape. Older non-adaptive icons come in any outline: [fullBleed] ones fill their square (so
     * they can be cut to shape too); the rest get set on a plate of the icon shape.
     */
    class Icons(val full: ImageBitmap, val mono: ImageBitmap?, val adaptive: Boolean, val fullBleed: Boolean = adaptive)

    private val cache = object : LruCache<String, Icons>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Icons) =
            value.full.width * value.full.height * 4 * (if (value.mono != null) 2 else 1)
    }

    fun cached(key: String, sizePx: Int): Icons? = cache.get("$key@$sizePx")

    fun clear() = cache.evictAll()

    fun load(info: LauncherActivityInfo, key: String, sizePx: Int): Icons {
        cached(key, sizePx)?.let { return it }
        val drawable = info.getIcon(context.resources.displayMetrics.densityDpi)
        val icons = if (drawable is AdaptiveIconDrawable) {
            val mono = if (Build.VERSION.SDK_INT >= 33) drawable.monochrome else null
            Icons(
                full = renderLayers(listOfNotNull(drawable.background, drawable.foreground), sizePx),
                mono = mono?.let { renderLayers(listOf(it), sizePx) },
                adaptive = true,
            )
        } else {
            val bitmap = drawable.toBitmap(sizePx, sizePx)
            Icons(bitmap.asImageBitmap(), mono = null, adaptive = false, fullBleed = fillsSquare(bitmap))
        }
        cache.put("$key@$sizePx", icons)
        return icons
    }

    /** True when the icon is opaque right into its corners, i.e. a full square picture. */
    private fun fillsSquare(bitmap: Bitmap): Boolean {
        val e = (bitmap.width * 0.04f).toInt().coerceAtLeast(1)
        val w = bitmap.width - 1 - e
        val h = bitmap.height - 1 - e
        return listOf(e to e, w to e, e to h, w to h).all { (x, y) -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 200 }
    }

    /** Adaptive icon layers are 108dp with the middle 72dp visible, so crop a quarter off each side. */
    private fun renderLayers(layers: List<Drawable>, size: Int): ImageBitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val inset = size / 4
        layers.forEach {
            it.setBounds(-inset, -inset, size + inset, size + inset)
            it.draw(canvas)
        }
        return bitmap.asImageBitmap()
    }
}
