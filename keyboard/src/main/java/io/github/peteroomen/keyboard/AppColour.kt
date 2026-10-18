package io.github.peteroomen.keyboard

import android.content.Context

/**
 * An app's own brand colour, read from its theme: its primary colour, or its accent if the
 * primary is a plain white, grey or black (as on apps with white toolbars). Null if it has
 * neither, or the theme can't be read.
 */
object AppColour {

    fun of(context: Context, packageName: String): Int? = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        // The launch screen's theme is often a splash; the app's own theme carries its colours
        val themeId = info.theme.takeIf { it != 0 } ?: return@runCatching null
        val app = context.createPackageContext(packageName, 0)
        val theme = app.resources.newTheme().apply { applyStyle(themeId, true) }
        val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary, android.R.attr.colorAccent))
        try {
            listOf(0, 1).firstNotNullOfOrNull { i -> attrs.getColor(i, 0).takeIf { isColourful(it) } }
        } finally {
            attrs.recycle()
        }
    }.getOrNull()

    /** Not white, grey or black: its strongest channel is well clear of its weakest. */
    fun isColourful(argb: Int): Boolean {
        if (argb ushr 24 < 0x80) return false
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) >= 48
    }
}
