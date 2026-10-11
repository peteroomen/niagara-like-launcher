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

    private val shader = RuntimeShader(AGSL).apply {
        setInputShader(
            "image",
            BitmapShader(sharp.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            },
        )
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
