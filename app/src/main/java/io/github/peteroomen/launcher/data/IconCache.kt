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
import androidx.core.graphics.drawable.toBitmap

/** App icons as bitmaps: the normal icon, plus the monochrome layer used for themed icons (Android 13+). */
class IconCache(private val context: Context) {

    class Icons(val full: ImageBitmap, val mono: ImageBitmap?)

    private val cache = object : LruCache<String, Icons>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Icons) =
            value.full.width * value.full.height * 4 * (if (value.mono != null) 2 else 1)
    }

    fun cached(key: String, sizePx: Int): Icons? = cache.get("$key@$sizePx")

    fun clear() = cache.evictAll()

    fun load(info: LauncherActivityInfo, key: String, sizePx: Int): Icons {
        cached(key, sizePx)?.let { return it }
        val drawable = info.getIcon(context.resources.displayMetrics.densityDpi)
        val mono = if (Build.VERSION.SDK_INT >= 33) (drawable as? AdaptiveIconDrawable)?.monochrome else null
        val icons = Icons(
            full = drawable.toBitmap(sizePx, sizePx).asImageBitmap(),
            mono = mono?.let { renderLayer(it, sizePx) },
        )
        cache.put("$key@$sizePx", icons)
        return icons
    }

    /** Adaptive icon layers are 108dp with the middle 72dp visible, so crop a quarter off each side. */
    private fun renderLayer(layer: Drawable, size: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = size / 4
        layer.setBounds(-inset, -inset, size + inset, size + inset)
        layer.draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }
}
