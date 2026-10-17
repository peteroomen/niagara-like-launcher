package io.github.peteroomen.launcher.ui

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * Clear liquid glass: each panel is a lens over a lightly softened copy of the wallpaper. Works on
 * rounded rectangles (circles are rounded rectangles with radius = half the size), using a signed
 * distance to the edge. Android 13+ only (AGSL runtime shaders).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GlassEdge(sharp: ImageBitmap) {

    private val image = BitmapShader(sharp.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
    }
    // Compiled on the phone; null if that fails, and glass falls back to frosted
    private val clear: RuntimeShader? = runCatching { RuntimeShader(CLEAR_AGSL).apply { setInputShader("image", image) } }.getOrNull()
    private val bezel: RuntimeShader? = runCatching { RuntimeShader(BEZEL_AGSL).apply { setInputShader("image", image) } }.getOrNull()

    /**
     * Bezel glass: a thick pane with a flat facet cut round its rim. Through the facet everything
     * shifts inwards by the same amount (a flat cut, not a curve), with a hard step where it meets
     * the face, and looks a little darker and richer, as through thicker glass. The face is softly
     * blurred and magnified by about 5%. Lighting is drawn over it separately.
     */
    fun bezelShaderFor(
        size: Size,
        radius: Float,
        facet: Float,
        shift: Float,
        panelOrigin: Offset,
        imageOrigin: Offset,
        imageScale: Float,
        blur: Float,
        extendLeft: Float = 0f,
        extendTop: Float = 0f,
        extendRight: Float = 0f,
        extendBottom: Float = 0f,
    ): Shader? {
        val bezel = bezel ?: return null
        bezel.setFloatUniform("extend", extendLeft, extendTop, extendRight, extendBottom)
        bezel.setFloatUniform("size", size.width, size.height)
        bezel.setFloatUniform("radius", radius.coerceAtMost(minOf(size.width, size.height) / 2))
        bezel.setFloatUniform("facet", facet)
        bezel.setFloatUniform("shift", shift)
        bezel.setFloatUniform("blur", blur)
        bezel.setFloatUniform("panelOrigin", panelOrigin.x, panelOrigin.y)
        bezel.setFloatUniform("imageOrigin", imageOrigin.x, imageOrigin.y)
        bezel.setFloatUniform("imageScale", imageScale)
        return bezel
    }

    /**
     * Clear glass, after Apple's: the whole panel is a lens. The wallpaper shows through barely
     * blurred; towards the rim it's pulled inwards (a rounded bevel), with
     * red, green and blue bending by slightly different amounts for faint colour fringes. A thin rim
     * catches the light from the top-left and bottom-right, with a soft glow just inside it.
     */
    /**
     * [extendLeft], [extendTop], [extendRight] and [extendBottom] push edges out past the panel, so
     * they don't bend: for a sheet whose sides and bottom sit against the screen.
     */
    fun clearShaderFor(
        size: Size,
        radius: Float,
        bevel: Float,
        strength: Float,
        zoom: Float,
        panelOrigin: Offset,
        imageOrigin: Offset,
        imageScale: Float,
        extendLeft: Float = 0f,
        extendTop: Float = 0f,
        extendRight: Float = 0f,
        extendBottom: Float = 0f,
    ): Shader? {
        val clear = clear ?: return null
        clear.setFloatUniform("extend", extendLeft, extendTop, extendRight, extendBottom)
        clear.setFloatUniform("size", size.width, size.height)
        clear.setFloatUniform("radius", radius.coerceAtMost(minOf(size.width, size.height) / 2))
        clear.setFloatUniform("bevel", bevel)
        clear.setFloatUniform("strength", strength)
        clear.setFloatUniform("zoom", zoom)
        clear.setFloatUniform("panelOrigin", panelOrigin.x, panelOrigin.y)
        clear.setFloatUniform("imageOrigin", imageOrigin.x, imageOrigin.y)
        clear.setFloatUniform("imageScale", imageScale)
        return clear
    }

    private companion object {
        const val BEZEL_AGSL = """
            uniform shader image;
            uniform float2 size;
            uniform float radius;
            uniform float facet;
            uniform float shift;
            uniform float blur;
            uniform float2 panelOrigin;
            uniform float2 imageOrigin;
            uniform float imageScale;
            uniform float4 extend;

            float edgeDistance(float2 p, float2 halfSize, float r) {
                float2 q = abs(p) - halfSize + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            // A soft five-tap blur: the face reads as slightly frosted, thick glass
            half3 wallpaperAt(float2 local) {
                float2 at = (panelOrigin + local - imageOrigin) / imageScale;
                float d = blur / imageScale;
                return (image.eval(at).rgb * 2.0
                    + image.eval(at + float2(d, 0.0)).rgb + image.eval(at - float2(d, 0.0)).rgb
                    + image.eval(at + float2(0.0, d)).rgb + image.eval(at - float2(0.0, d)).rgb) / 6.0;
            }

            half4 main(float2 coord) {
                float2 off = float2(extend.x, extend.y);
                float2 halfSize = (size + float2(extend.x + extend.z, extend.y + extend.w)) * 0.5;
                float2 p = coord + off - halfSize;
                float inside = max(-edgeDistance(p, halfSize, radius), 0.0);
                float2 n = float2(
                    edgeDistance(p + float2(1.0, 0.0), halfSize, radius) - edgeDistance(p - float2(1.0, 0.0), halfSize, radius),
                    edgeDistance(p + float2(0.0, 1.0), halfSize, radius) - edgeDistance(p - float2(0.0, 1.0), halfSize, radius));
                n = n / max(length(n), 0.0001);

                // 1 on the facet, 0 on the face, with a one-pixel edge so the cut is crisp but not jagged
                float onFacet = 1.0 - smoothstep(facet - 0.75, facet + 0.75, inside);
                // The face magnified about 5% (thick glass), the facet a flat shift inwards
                float2 base = halfSize + p * mix(0.951, 1.0, onFacet) - off;
                float bend = shift * onFacet;

                half3 c;
                c.r = wallpaperAt(base - n * bend).r;
                c.g = wallpaperAt(base - n * bend * 1.06).g;
                c.b = wallpaperAt(base - n * bend * 1.12).b;

                // Through the facet: richer and a touch darker, as through more glass
                half lum = dot(c, half3(0.2126, 0.7152, 0.0722));
                c = mix(half3(lum), c, 1.0 + 0.3 * onFacet);
                c *= 1.0 - 0.1 * onFacet;
                return half4(clamp(c, 0.0, 1.0), 1.0);
            }
        """

        const val CLEAR_AGSL = """
            uniform shader image;
            uniform float2 size;
            uniform float radius;
            uniform float bevel;
            uniform float strength;
            uniform float zoom;
            uniform float2 panelOrigin;
            uniform float2 imageOrigin;
            uniform float imageScale;
            uniform float4 extend; // left, top, right, bottom: edges pushed out of sight

            float edgeDistance(float2 p, float2 halfSize, float r) {
                float2 q = abs(p) - halfSize + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            half3 wallpaperAt(float2 local) {
                return image.eval((panelOrigin + local - imageOrigin) / imageScale).rgb;
            }

            half4 main(float2 coord) {
                // The lens shape, which can be bigger than the panel on sides that shouldn't bend
                float2 shift = float2(extend.x, extend.y);
                float2 halfSize = (size + float2(extend.x + extend.z, extend.y + extend.w)) * 0.5;
                float2 p = coord + shift - halfSize;
                float inside = max(-edgeDistance(p, halfSize, radius), 0.0);
                float2 n = float2(
                    edgeDistance(p + float2(1.0, 0.0), halfSize, radius) - edgeDistance(p - float2(1.0, 0.0), halfSize, radius),
                    edgeDistance(p + float2(0.0, 1.0), halfSize, radius) - edgeDistance(p - float2(0.0, 1.0), halfSize, radius));
                n = n / max(length(n), 0.0001);

                // A rounded bevel: flat in the middle, curving away at the rim, bending light inwards
                float s = 1.0 - clamp(inside / bevel, 0.0, 1.0);
                float bend = strength * s * s;
                float2 base = halfSize + p / zoom - shift;

                // Each colour bends a little differently: faint fringes at the rim
                half3 c;
                c.r = wallpaperAt(base - n * bend).r;
                c.g = wallpaperAt(base - n * bend * 1.1).g;
                c.b = wallpaperAt(base - n * bend * 1.2).b;

                half lum = dot(c, half3(0.2126, 0.7152, 0.0722));
                c = mix(half3(lum), c, 1.2);

                // Light catching the rim, brightest where it faces the top-left or bottom-right
                float facing = abs(dot(n, float2(-0.7071, -0.7071)));
                float rim = (1.0 - smoothstep(0.0, 4.5, inside)) * (0.35 + 0.65 * facing);
                float glow = (1.0 - smoothstep(0.0, bevel * 0.8, inside)) * 0.12;
                c = c * 0.95 + 0.05 + half(rim * 0.8 + glow);
                return half4(clamp(c, 0.0, 1.0), 1.0);
            }
        """
    }
}
