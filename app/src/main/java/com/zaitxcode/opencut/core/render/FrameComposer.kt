package com.zaitxcode.opencut.core.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.Clip

/**
 * Draws the layer stack of a project onto the current framebuffer.
 *
 * The composer is shared by the on screen preview and by the exporter so that what
 * the user sees is exactly what gets written to the file.
 *
 * Two programs are kept alive: one samples the external texture a video decoder
 * produces, the other samples ordinary 2D textures used by images, stickers and
 * text. OpenGL ES 2.0 does not allow one sampler type to serve both.
 */
class FrameComposer {

    /** A linked program together with the locations of every uniform it uses. */
    private class Program(vertexSource: String, fragmentSource: String) {
        val id: Int = GlUtil.linkProgram(vertexSource, fragmentSource)
        val position: Int = GLES20.glGetAttribLocation(id, "aPosition")
        val texCoord: Int = GLES20.glGetAttribLocation(id, "aTexCoord")
        val mvp: Int = GLES20.glGetUniformLocation(id, "uMvpMatrix")
        val texMatrix: Int = GLES20.glGetUniformLocation(id, "uTexMatrix")
        val texture: Int = GLES20.glGetUniformLocation(id, "uTexture")
        val alpha: Int = GLES20.glGetUniformLocation(id, "uAlpha")
        val lut: Int = GLES20.glGetUniformLocation(id, "uLut")
        val hasLut: Int = GLES20.glGetUniformLocation(id, "uHasLut")
        val lutIntensity: Int = GLES20.glGetUniformLocation(id, "uLutIntensity")

        private val cache = HashMap<String, Int>()

        fun uniform(name: String): Int = cache.getOrPut(name) {
            GLES20.glGetUniformLocation(id, name)
        }
    }

    private var externalProgram: Program? = null
    private var plainProgram: Program? = null

    private val quad: java.nio.FloatBuffer = GlUtil.floatBuffer(
        floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
    )
    private val quadTex: java.nio.FloatBuffer = GlUtil.floatBuffer(
        floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f),
    )

    private val mvp = FloatArray(16)
    private val model = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val identityTexMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    private var lutTexture = 0

    /** Creates the GL programs; must be called on the GL thread. */
    fun init() {
        externalProgram = Program(Shaders.TEXTURE_VERTEX, Shaders.TEXTURE_FRAGMENT)
        plainProgram = Program(Shaders.TEXTURE_VERTEX, Shaders.TEXTURE_FRAGMENT_2D)
        lutTexture = GlUtil.createLutTexture()
    }

    /** Clears the framebuffer with the canvas background colour. */
    fun clear(backgroundColor: Int) {
        GLES20.glClearColor(
            ((backgroundColor shr 16) and 0xFF) / 255f,
            ((backgroundColor shr 8) and 0xFF) / 255f,
            (backgroundColor and 0xFF) / 255f,
            1f,
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    /**
     * Draws one layer.
     *
     * The source is fitted inside the canvas preserving its aspect ratio, then the
     * layer transform (position, scale, rotation) is applied and the colour grading
     * uniforms are uploaded.
     */
    fun drawLayer(
        layer: RenderLayer,
        texture: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        playheadUs: Long,
        lutTexture: Int = 0,
        lutIntensity: Float = 0f,
    ) {
        val external = layer.kind == LayerKind.VIDEO
        val program = (if (external) externalProgram else plainProgram) ?: return

        val sourceWidth = if (layer.sourceWidth > 0) layer.sourceWidth else canvasWidth
        val sourceHeight = if (layer.sourceHeight > 0) layer.sourceHeight else canvasHeight

        val canvasAspect = canvasWidth.toFloat() / canvasHeight
        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        var drawWidth = canvasWidth.toFloat()
        var drawHeight = canvasHeight.toFloat()
        if (sourceAspect > canvasAspect) {
            drawHeight = canvasWidth / sourceAspect
        } else {
            drawWidth = canvasHeight * sourceAspect
        }
        drawWidth *= layer.clip.scale.x
        drawHeight *= layer.clip.scale.y

        Matrix.setIdentityM(model, 0)
        // Position is a normalised offset from the centre of the canvas: 0,0 keeps
        // the layer centred, which is what the editor's position panel shows.
        Matrix.translateM(
            model,
            0,
            (0.5f + layer.clip.transform.x) * canvasWidth,
            (0.5f + layer.clip.transform.y) * canvasHeight,
            0f,
        )
        Matrix.rotateM(model, 0, layer.clip.rotation, 0f, 0f, 1f)
        // The quad already spans -1..1 and is centred on the origin, so scaling by
        // half of the target size is enough; translating by -size/2 as well would
        // push the layer one size to the left and down.
        Matrix.scaleM(model, 0, drawWidth / 2f, drawHeight / 2f, 1f)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)

        GLES20.glUseProgram(program.id)
        GLES20.glUniformMatrix4fv(program.mvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(program.texMatrix, 1, false, identityTexMatrix, 0)
        GLES20.glUniform1f(program.alpha, layer.clip.alpha)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(
            if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D,
            texture,
        )
        GLES20.glUniform1i(program.texture, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        val lut = if (lutTexture != 0) lutTexture else this.lutTexture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lut)
        GLES20.glUniform1i(program.lut, 1)
        GLES20.glUniform1f(program.hasLut, if (lutTexture != 0) 1f else 0f)
        GLES20.glUniform1f(
            program.lutIntensity,
            if (lutTexture != 0) lutIntensity.coerceIn(0f, 1f) else 0f,
        )

        uploadAdjustments(program, layer.adjustment)
        GLES20.glUniform1f(
            program.uniform("uTime"),
            (playheadUs % 1_000_000) / 1_000_000f,
        )

        quad.position(0)
        GLES20.glVertexAttribPointer(program.position, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(program.position)
        quadTex.position(0)
        GLES20.glVertexAttribPointer(program.texCoord, 2, GLES20.GL_FLOAT, false, 0, quadTex)
        GLES20.glEnableVertexAttribArray(program.texCoord)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(program.position)
        GLES20.glDisableVertexAttribArray(program.texCoord)
    }

    private fun uploadAdjustments(program: Program, adjust: Adjustment) {
        GLES20.glUniform1f(program.uniform("uBrightness"), adjust.brightness)
        GLES20.glUniform1f(program.uniform("uContrast"), adjust.contrast)
        GLES20.glUniform1f(program.uniform("uSaturation"), adjust.saturation)
        GLES20.glUniform1f(program.uniform("uSharpen"), adjust.sharpen)
        GLES20.glUniform1f(program.uniform("uHighlight"), adjust.highlight)
        GLES20.glUniform1f(program.uniform("uShadow"), adjust.shadow)
        GLES20.glUniform1f(program.uniform("uTemperature"), adjust.temperature)
        GLES20.glUniform1f(program.uniform("uTone"), adjust.tone)
        GLES20.glUniform1f(program.uniform("uFade"), adjust.fade)
        GLES20.glUniform1f(program.uniform("uLightSensation"), adjust.lightSensation)
        GLES20.glUniform1f(program.uniform("uVignette"), adjust.vignette)
        GLES20.glUniform1f(program.uniform("uGrain"), adjust.grain)
        GLES20.glUniform1f(program.uniform("uHue"), adjust.hue)
        GLES20.glUniform1f(program.uniform("uHslSaturation"), adjust.hslSaturation)
        GLES20.glUniform1f(program.uniform("uHslLightness"), adjust.hslLightness)
        GLES20.glUniform1f(program.uniform("uChromaIntensity"), 0f)
        GLES20.glUniform3f(program.uniform("uChromaColor"), 0f, 1f, 0f)
    }

    /**
     * Prepares the orthographic projection and letterboxes the canvas inside the
     * viewport so a 9:16 project is never stretched on a wider surface.
     */
    fun setViewport(canvasWidth: Int, canvasHeight: Int, viewportWidth: Int, viewportHeight: Int) {
        Matrix.orthoM(
            viewProjection,
            0,
            0f,
            canvasWidth.toFloat(),
            0f,
            canvasHeight.toFloat(),
            -1f,
            1f,
        )
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            return
        }
        val canvasAspect = canvasWidth.toFloat() / canvasHeight
        val viewAspect = viewportWidth.toFloat() / viewportHeight
        var width = viewportWidth
        var height = viewportHeight
        if (viewAspect > canvasAspect) {
            width = (viewportHeight * canvasAspect).toInt().coerceAtLeast(1)
        } else {
            height = (viewportWidth / canvasAspect).toInt().coerceAtLeast(1)
        }
        GLES20.glViewport(
            (viewportWidth - width) / 2,
            (viewportHeight - height) / 2,
            width,
            height,
        )
    }

    /** Enables alpha blending for layer composition. */
    fun enableBlend() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** Disables alpha blending. */
    fun disableBlend() {
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}
