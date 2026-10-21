package com.zaitxcode.opencut.core.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.Clip

/**
 * Draws the layer stack of a project onto the current framebuffer.
 *
 * The composer is shared by the on screen preview and by the exporter so that what
 * the user sees is exactly what gets written to the file.
 */
class FrameComposer {

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpHandle = 0
    private var texMatrixHandle = 0
    private var textureHandle = 0
    private var alphaHandle = 0
    private var lutHandle = 0
    private var hasLutHandle = 0
    private var lutIntensityHandle = 0
    private var lutTexture = 0

    private val quad: java.nio.FloatBuffer = GlUtil.floatBuffer(
        floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
    )
    private val quadTex: java.nio.FloatBuffer = GlUtil.floatBuffer(
        floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f),
    )

    private val mvp = FloatArray(16)
    private val model = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val texMatrix = FloatArray(16)

    private val uniformCache = HashMap<String, Int>()

    /** Creates the GL program; must be called on the GL thread. */
    fun init() {
        program = GlUtil.linkProgram(Shaders.TEXTURE_VERTEX, Shaders.TEXTURE_FRAGMENT)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
        lutHandle = GLES20.glGetUniformLocation(program, "uLut")
        hasLutHandle = GLES20.glGetUniformLocation(program, "uHasLut")
        lutIntensityHandle = GLES20.glGetUniformLocation(program, "uLutIntensity")
        lutTexture = GlUtil.createLutTexture()
        Matrix.setIdentityM(texMatrix, 0)
    }

    private fun uniform(name: String): Int = uniformCache.getOrPut(name) {
        GLES20.glGetUniformLocation(program, name)
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
        Matrix.translateM(
            model,
            0,
            layer.clip.transform.x * canvasWidth,
            layer.clip.transform.y * canvasHeight,
            0f,
        )
        Matrix.rotateM(model, 0, layer.clip.rotation, 0f, 0f, 1f)
        Matrix.translateM(model, 0, -drawWidth / 2f, -drawHeight / 2f, 0f)
        Matrix.scaleM(model, 0, drawWidth / 2f, drawHeight / 2f, 1f)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)
        GLES20.glUniform1f(alphaHandle, layer.clip.alpha)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, if (lutTexture != 0) lutTexture else this.lutTexture)
        GLES20.glUniform1i(lutHandle, 1)
        GLES20.glUniform1f(hasLutHandle, if (lutTexture != 0) 1f else 0f)
        GLES20.glUniform1f(lutIntensityHandle, if (lutTexture != 0) lutIntensity.coerceIn(0f, 1f) else 0f)

        uploadAdjustments(layer.adjustment)
        GLES20.glUniform1f(
            uniform("uTime"),
            (playheadUs % 1_000_000) / 1_000_000f,
        )

        quad.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(positionHandle)
        quadTex.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, quadTex)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun uploadAdjustments(adjust: Adjustment) {
        GLES20.glUniform1f(uniform("uBrightness"), adjust.brightness)
        GLES20.glUniform1f(uniform("uContrast"), adjust.contrast)
        GLES20.glUniform1f(uniform("uSaturation"), adjust.saturation)
        GLES20.glUniform1f(uniform("uSharpen"), adjust.sharpen)
        GLES20.glUniform1f(uniform("uHighlight"), adjust.highlight)
        GLES20.glUniform1f(uniform("uShadow"), adjust.shadow)
        GLES20.glUniform1f(uniform("uTemperature"), adjust.temperature)
        GLES20.glUniform1f(uniform("uTone"), adjust.tone)
        GLES20.glUniform1f(uniform("uFade"), adjust.fade)
        GLES20.glUniform1f(uniform("uLightSensation"), adjust.lightSensation)
        GLES20.glUniform1f(uniform("uVignette"), adjust.vignette)
        GLES20.glUniform1f(uniform("uGrain"), adjust.grain)
        GLES20.glUniform1f(uniform("uHue"), adjust.hue)
        GLES20.glUniform1f(uniform("uHslSaturation"), adjust.hslSaturation)
        GLES20.glUniform1f(uniform("uHslLightness"), adjust.hslLightness)
        GLES20.glUniform1f(uniform("uChromaIntensity"), 0f)
        GLES20.glUniform3f(uniform("uChromaColor"), 0f, 1f, 0f)
    }

    /** Prepares the orthographic projection for the given viewport. */
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
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
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
