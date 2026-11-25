package com.forensim.app.engine.renderer

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Picks a sharp drawing surface for the 3D views: 8-bit colour, a 24-bit depth buffer (no
 * flickering where surfaces meet, e.g. evidence lying on the floor) and 4x multisample
 * anti-aliasing (clean edges instead of jagged stair steps). Falls back step by step on
 * phones that can't do that, so the scene always opens.
 */
class HdConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        for ((depth, samples) in listOf(24 to 4, 24 to 2, 24 to 0, 16 to 4, 16 to 0)) {
            find(egl, display, depth, samples)?.let { return it }
        }
        throw IllegalStateException("No usable OpenGL ES 3 surface on this device")
    }

    private fun find(egl: EGL10, display: EGLDisplay, depth: Int, samples: Int): EGLConfig? {
        val attrs = buildList {
            addAll(listOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8))
            addAll(listOf(EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT))
            if (samples > 0) addAll(listOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples))
            add(EGL10.EGL_NONE)
        }.toIntArray()
        val count = IntArray(1)
        if (!egl.eglChooseConfig(display, attrs, null, 0, count) || count[0] <= 0) return null
        val configs = arrayOfNulls<EGLConfig>(count[0])
        if (!egl.eglChooseConfig(display, attrs, configs, configs.size, count)) return null
        // eglChooseConfig returns sizes "at least" the request; prefer exactly 8-bit colour.
        return configs.filterNotNull().firstOrNull { c ->
            listOf(EGL10.EGL_RED_SIZE, EGL10.EGL_GREEN_SIZE, EGL10.EGL_BLUE_SIZE).all { attr(egl, display, c, it) == 8 }
        } ?: configs.firstOrNull()
    }

    private fun attr(egl: EGL10, display: EGLDisplay, config: EGLConfig, name: Int): Int {
        val v = IntArray(1)
        return if (egl.eglGetConfigAttrib(display, config, name, v)) v[0] else 0
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT = 0x40
    }
}
