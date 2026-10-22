package com.zaitxcode.opencut.ui.editor

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import android.view.TextureView
import com.zaitxcode.opencut.core.render.PreviewRenderer

/**
 * The OpenGL surface of the preview.
 *
 * A `TextureView` is used instead of a `GLSurfaceView`: the texture lives inside
 * the normal view hierarchy, so it is positioned, clipped and composited by the
 * same rules as every other part of the screen. A surface based view punches a
 * hole into the window, which is what used to hide the top bar.
 */
class GlTextureView(
    context: Context,
    private val renderer: PreviewRenderer,
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var thread: RenderThread? = null

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        start(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        thread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stop()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun start(surface: SurfaceTexture, width: Int, height: Int) {
        stop()
        val created = RenderThread(surface, width, height, renderer)
        thread = created
        created.start()
    }

    private fun stop() {
        thread?.shutdown()
        thread = null
    }

    /** Releases the graphics resources of the view. */
    fun release() {
        stop()
        renderer.release()
    }
}

/**
 * The thread that owns the EGL context and draws the frames.
 *
 * The context belongs to this thread only, which is the rule EGL expects: a
 * context is current on exactly one thread at a time.
 */
private class RenderThread(
    private val surfaceTexture: SurfaceTexture,
    width: Int,
    height: Int,
    private val renderer: PreviewRenderer,
) : Thread("opencut-preview") {

    @Volatile
    private var running = true

    @Volatile
    private var surfaceWidth = width

    @Volatile
    private var surfaceHeight = height

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun resize(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    fun shutdown() {
        running = false
        interrupt()
        try {
            join(1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun run() {
        if (!createContext()) return
        try {
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, surfaceWidth, surfaceHeight)
            while (running) {
                renderer.onDrawFrame(null)
                if (!EGL14.eglSwapBuffers(display, surface)) break
            }
        } catch (error: Throwable) {
            Log.w(TAG, "preview thread stopped", error)
        } finally {
            releaseContext()
        }
    }

    private fun createContext(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)) return false
        val config = configs[0] ?: return false
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        if (context == EGL14.EGL_NO_CONTEXT) return false
        surface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surfaceTexture,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        if (surface == EGL14.EGL_NO_SURFACE) return false
        return EGL14.eglMakeCurrent(display, surface, surface, context)
    }

    private fun releaseContext() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
        GLES20.glFlush()
    }

    private companion object {
        const val TAG = "OpenCut/Preview"
    }
}
