package io.github.peteroomen.launcher.system

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.peteroomen.launcher.model.BoxBlur
import java.io.File

/**
 * A small, pre-blurred copy of the wallpaper for glass panels to show through. Android only lets a
 * launcher read the wallpaper with "All files access"; without it, the launcher can use a photo the
 * person picked in the launcher (which it also sets as the wallpaper).
 */
object WallpaperBackdrop {

    /** Width of the blurred copy. Small is fine: it's drawn blurry anyway. */
    private const val WIDTH = 160
    private const val RADIUS = 5

    enum class Source { SYSTEM, PICKED, NONE }

    @Volatile private var ownChangeAt = 0L

    /**
     * True for a short while after the launcher sets the wallpaper itself. Every launcher screen
     * hears the change, so it's a time window rather than a flag one listener would use up.
     */
    fun isOwnChange() = SystemClock.elapsedRealtime() - ownChangeAt < 10_000

    fun pickedFile(context: Context) = File(context.filesDir, "picked_wallpaper")

    /** Where the blur would come from right now. */
    fun source(context: Context): Source = when {
        systemWallpaper(context) != null -> Source.SYSTEM
        pickedFile(context).exists() -> Source.PICKED
        else -> Source.NONE
    }

    /** The blurred wallpaper, or null if we can't see it (no access, or a live wallpaper). */
    fun load(context: Context): ImageBitmap? {
        val drawable = systemWallpaper(context) ?: picked(context) ?: return null
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val small = drawable.toBitmap(WIDTH, (WIDTH.toLong() * h / w).toInt().coerceAtLeast(1))
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        BoxBlur.blur(pixels, small.width, small.height, RADIUS)
        val blurred = small.copy(small.config ?: android.graphics.Bitmap.Config.ARGB_8888, true)
        blurred.setPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        return blurred.asImageBitmap()
    }

    /** Copies the picked photo for the glass, and sets it as the home screen wallpaper. */
    fun usePicked(context: Context, uri: Uri) {
        val file = pickedFile(context)
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        ownChangeAt = SystemClock.elapsedRealtime()
        file.inputStream().use { WallpaperManager.getInstance(context).setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
    }

    fun forgetPicked(context: Context) {
        pickedFile(context).delete()
    }

    @Suppress("MissingPermission")
    private fun systemWallpaper(context: Context): Drawable? =
        try {
            WallpaperManager.getInstance(context).drawable
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    private fun picked(context: Context): Drawable? {
        val file = pickedFile(context)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }
}
