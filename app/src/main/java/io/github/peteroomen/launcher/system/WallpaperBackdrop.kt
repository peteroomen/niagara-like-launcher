package io.github.peteroomen.launcher.system

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
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

    /**
     * The frosted copy: about 1.1dp per pixel across a phone screen, so three box passes of radius 3
     * come to a blur of about 4dp, as tuned in the prototype.
     */
    private const val WIDTH = 360
    private const val RADIUS = 3
    private const val SHARP_WIDTH = 720

    /** [LIVE]: a live wallpaper, which no app can see, and no picked photo to use instead. */
    enum class Source { SYSTEM, PICKED, LIVE, NONE }

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
        isLive(context) -> Source.LIVE
        else -> Source.NONE
    }

    private fun isLive(context: Context) = WallpaperManager.getInstance(context).wallpaperInfo != null

    /**
     * Two copies of the wallpaper for glass: [frosted] is blurred for frosted panels; [sharp] is
     * larger and only lightly softened, for clear glass to show through.
     */
    class Images(val frosted: ImageBitmap, val sharp: ImageBitmap)

    /** The wallpaper copies, or null if we can't see it (no access, or a live wallpaper). */
    fun load(context: Context): Images? {
        val drawable = systemWallpaper(context) ?: picked(context) ?: return null
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        return Images(
            frosted = scaledAndBlurred(drawable, WIDTH, RADIUS, passes = 3),
            sharp = scaledAndBlurred(drawable, SHARP_WIDTH, radius = 1, passes = 2), // a light blur, as seen through clear glass
        )
    }

    private fun scaledAndBlurred(drawable: Drawable, width: Int, radius: Int, passes: Int): ImageBitmap {
        val height = (width.toLong() * drawable.intrinsicHeight / drawable.intrinsicWidth).toInt().coerceAtLeast(1)
        val bitmap = drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        BoxBlur.blur(pixels, width, height, radius, passes)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap.asImageBitmap()
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

    /**
     * The home screen wallpaper's image. Not for a live wallpaper: Android hands back its built-in
     * default wallpaper then, and glass would show a picture that isn't on screen.
     */
    @Suppress("MissingPermission")
    private fun systemWallpaper(context: Context): Drawable? =
        try {
            if (isLive(context)) null else WallpaperManager.getInstance(context).drawable
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    private fun picked(context: Context): Drawable? {
        val file = pickedFile(context)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 }) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }
}
