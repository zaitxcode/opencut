package com.zaitxcode.opencut.core.render

import android.opengl.GLES20
import com.zaitxcode.opencut.core.model.FilterSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Colour lookup tables used by the renderer.
 *
 * A LUT is a 32×32×32 RGB cube flattened into a 32×1024 strip texture:
 * `x = slice * 32 + red`, `y = green`. A 3D texture would be simpler, but
 * `sampler3D` does not exist in OpenGL ES 2.0 and the preview runs on an ES 2.0
 * context, so the strip keeps the renderer compatible with every device.
 *
 * The cubes are generated analytically instead of being shipped as binary
 * assets, which keeps the project free of third party material while still
 * allowing an arbitrary number of looks.
 */
object LutFactory {

    private const val SIZE = 32

    /** Analytic colour transforms available as LUTs. */
    enum class Look {
        WARM,
        COOL,
        VIVID,
        MONO,
        SEPIA,
        INVERSE,
        VINTAGE,
    }

    private val cache = HashMap<Look, Int>()

    /** Returns the GL texture of a look, creating it on first use. */
    fun textureFor(look: Look): Int = cache.getOrPut(look) { create(look) }

    /**
     * Creates a fresh texture instead of using the shared cache.
     *
     * The preview and the exporter use different EGL contexts, and a texture name is
     * only valid in the context that created it.
     */
    fun createTexture(look: Look): Int = create(look)

    /** Releases every cached texture. */
    fun releaseAll() {
        val ids = IntArray(cache.size)
        cache.values.forEachIndexed { index, id -> ids[index] = id }
        if (ids.isNotEmpty()) GLES20.glDeleteTextures(ids.size, ids, 0)
        cache.clear()
    }

    private fun create(look: Look): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)

        // Rows are green, columns are (blue slice * 32 + red).
        val data = ByteBuffer.allocate(SIZE * SIZE * SIZE * 4).order(ByteOrder.nativeOrder())
        for (g in 0 until SIZE) {
            for (b in 0 until SIZE) {
                for (r in 0 until SIZE) {
                    val red = r / (SIZE - 1f)
                    val green = g / (SIZE - 1f)
                    val blue = b / (SIZE - 1f)
                    val mapped = apply(look, red, green, blue)
                    data.put((mapped[0].coerceIn(0f, 1f) * 255).toInt().toByte())
                    data.put((mapped[1].coerceIn(0f, 1f) * 255).toInt().toByte())
                    data.put((mapped[2].coerceIn(0f, 1f) * 255).toInt().toByte())
                    data.put(0xFF.toByte())
                }
            }
        }
        data.rewind()
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            SIZE * SIZE,
            SIZE,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            data,
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    /** Applies the colour transform of a look to one RGB triple. */
    private fun apply(look: Look, r: Float, g: Float, b: Float): FloatArray = when (look) {
        Look.WARM -> floatArrayOf(
            lift(r, 0.10f, 0.04f),
            lift(g, 0.04f, 0.02f),
            lift(b, -0.02f, 0.06f),
        )

        Look.COOL -> floatArrayOf(
            lift(r, -0.03f, 0.05f),
            lift(g, 0.0f, 0.02f),
            lift(b, 0.10f, 0.0f),
        )

        Look.VIVID -> floatArrayOf(
            saturate(r, g, b, 1.35f),
            saturate(r, g, b, 1.35f),
            saturate(r, g, b, 1.35f),
        )

        Look.MONO -> {
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            floatArrayOf(luma, luma, luma)
        }

        Look.SEPIA -> floatArrayOf(
            0.393f * r + 0.769f * g + 0.189f * b,
            0.349f * r + 0.686f * g + 0.168f * b,
            0.272f * r + 0.534f * g + 0.131f * b,
        )

        Look.INVERSE -> floatArrayOf(1f - r, 1f - g, 1f - b)

        Look.VINTAGE -> {
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            floatArrayOf(
                (luma + (r - luma) * 0.75f + 0.06f).coerceIn(0f, 1f),
                (luma + (g - luma) * 0.75f + 0.02f).coerceIn(0f, 1f),
                (luma + (b - luma) * 0.75f - 0.02f).coerceIn(0f, 1f),
            )
        }
    }

    private fun lift(value: Float, add: Float, multiply: Float): Float =
        (value * (1f + multiply) + add).coerceIn(0f, 1f)

    private fun saturate(r: Float, g: Float, b: Float, factor: Float): Float {
        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
        return (luma + (value(r, g, b, luma) - luma) * factor).coerceIn(0f, 1f)
    }

    private fun value(r: Float, g: Float, b: Float, luma: Float): Float =
        luma + ((r - luma) + (g - luma) + (b - luma)) / 3f

    /** Maps a filter descriptor to a built-in look. */
    fun lookOf(filter: FilterSpec?): Look? {
        val id = filter?.id ?: return null
        return runCatching { Look.valueOf(id.uppercase()) }.getOrNull()
    }

    /** All looks with their display names. */
    fun catalogue(): List<Pair<Look, String>> = Look.entries.map {
        it to it.name.lowercase().replaceFirstChar(Char::uppercase)
    }

    /** Approximate gamma used by the curve helper. */
    fun gamma(value: Float): Float = value.toDouble().pow(1.0 / 2.2).toFloat()

    /** Cube root helper kept for the tone curve experiments. */
    fun root(value: Float): Float = cbrt(value.toDouble()).toFloat()
}
