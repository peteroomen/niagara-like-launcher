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
 * The liquid part of liquid glass: near a panel's rim, the wallpaper is sampled from slightly
 * further in, so it looks stretched and bent like the edge of a lens. The effect is strongest at
 * the rim and fades out towards the middle, where the frosted glass takes over.
 *
 * Works on rounded rectangles (circles are rounded rectangles with radius = half the size), using
 * a signed distance to the edge. Android 13+ only (AGSL runtime shaders).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GlassEdge(sharp: ImageBitmap) {

    private val image = BitmapShader(sharp.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
    }
    private val shader = RuntimeShader(AGSL).apply { setInputShader("image", image) }
    // Compiled on the phone; null if that fails, and glass falls back to frosted
    private val clear: RuntimeShader? = runCatching { RuntimeShader(CLEAR_AGSL).apply { setInputShader("image", image) } }.getOrNull()

    /**
     * Clear glass, after Apple's: the whole panel is a lens. The wallpaper shows through barely
     * blurred and a little magnified; towards the rim it's pulled inwards (a rounded bevel), with
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

    /**
     * Sets up the shader for one panel. [panelOrigin] and [imageOrigin] are in screen pixels;
     * [imageScale] is screen pixels per pixel of the sharp image.
     */
    fun shaderFor(size: Size, radius: Float, fade: Float, strength: Float, panelOrigin: Offset, imageOrigin: Offset, imageScale: Float): Shader {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("radius", radius.coerceAtMost(minOf(size.width, size.height) / 2))
        shader.setFloatUniform("fade", fade)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("panelOrigin", panelOrigin.x, panelOrigin.y)
        shader.setFloatUniform("imageOrigin", imageOrigin.x, imageOrigin.y)
        shader.setFloatUniform("imageScale", imageScale)
        return shader
    }

    private companion object {
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
                c.g = wallpaperAt(base - n * bend * 1.08).g;
                c.b = wallpaperAt(base - n * bend * 1.16).b;

                half lum = dot(c, half3(0.2126, 0.7152, 0.0722));
                c = mix(half3(lum), c, 1.2);

                // Light catching the rim, brightest where it faces the top-left or bottom-right
                float facing = abs(dot(n, float2(-0.7071, -0.7071)));
                float rim = (1.0 - smoothstep(0.0, 3.0, inside)) * (0.3 + 0.7 * facing);
                float glow = (1.0 - smoothstep(0.0, bevel * 0.8, inside)) * 0.07;
                c = c * 0.95 + 0.05 + half(rim * 0.55 + glow);
                return half4(clamp(c, 0.0, 1.0), 1.0);
            }
        """

        const val AGSL = """
            uniform shader image;
            uniform float2 size;
            uniform float radius;
            uniform float fade;
            uniform float strength;
            uniform float2 panelOrigin;
            uniform float2 imageOrigin;
            uniform float imageScale;

            // Signed distance from p (relative to the centre) to a rounded rectangle's edge; negative inside
            float edgeDistance(float2 p, float2 halfSize, float r) {
                float2 q = abs(p) - halfSize + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            half4 main(float2 coord) {
                float2 halfSize = size * 0.5;
                float2 p = coord - halfSize;
                float inside = -edgeDistance(p, halfSize, radius);
                // 1 at the rim, easing to 0 at the fade depth
                float t = clamp(1.0 - inside / fade, 0.0, 1.0);
                t = t * t;

                // Outward direction of the edge, from the distance field's slope
                float2 n = float2(
                    edgeDistance(p + float2(1.0, 0.0), halfSize, radius) - edgeDistance(p - float2(1.0, 0.0), halfSize, radius),
                    edgeDistance(p + float2(0.0, 1.0), halfSize, radius) - edgeDistance(p - float2(0.0, 1.0), halfSize, radius));
                n = n / max(length(n), 0.0001);

                // Sample from further in: the edge magnifies what's just inside it
                float2 screen = panelOrigin + coord - n * strength * t;
                half4 c = image.eval((screen - imageOrigin) / imageScale);

                // A little more vivid, a touch darker, like the frosted body
                half lum = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
                c.rgb = mix(half3(lum), c.rgb, 1.4) * 0.92;
                return half4(c.rgb, 1.0) * half(t);
            }
        """
    }
}
