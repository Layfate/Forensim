package com.forensim.app.feature.ar

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.view.Surface
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The back camera's live image as a GL texture, drawn full-screen behind the 3D specimen (the
 * camera-view fallback). CameraX writes into a SurfaceTexture we own, so the camera and the 3D
 * model are one opaque picture: nothing depends on see-through layers, which some phones
 * (MIUI) don't composite.
 *
 * Threads: [surfaceProvider] runs on the main thread; [onGlCreated] and [draw] on the GL thread.
 */
class CameraFeed(context: android.content.Context) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    // Created detached; attached to the GL context when it exists (it can be recreated).
    private val texture = SurfaceTexture(false)
    private var surface: Surface? = null
    @Volatile private var bufferWidth = 0
    @Volatile private var bufferHeight = 0
    /** Clockwise degrees to turn the camera buffer upright on screen (90 on most phones in portrait). */
    @Volatile private var rotationDegrees = 0
    @Volatile private var hasFrame = false
    private var loggedMatrix = false

    private var textureId = 0
    private var program = 0
    private var aPos = 0
    private var aUv = 0
    private var uTexMatrix = 0
    private val texMatrix = FloatArray(16)
    private val positions = buffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uvs = buffer(FloatArray(8))
    private val corners = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f) // screen (s, t), t up
    private val uv = FloatArray(8)

    init {
        texture.setOnFrameAvailableListener { hasFrame = true }
    }

    /** Give this to CameraX's Preview: the camera draws into our texture. */
    val surfaceProvider = Preview.SurfaceProvider { request ->
        val size = request.resolution
        bufferWidth = size.width
        bufferHeight = size.height
        texture.setDefaultBufferSize(size.width, size.height)
        val s = Surface(texture)
        surface = s
        request.setTransformationInfoListener(mainExecutor) { info -> rotationDegrees = info.rotationDegrees }
        request.provideSurface(s, mainExecutor) { s.release() }
    }

    fun onGlCreated() {
        runCatching { texture.detachFromGLContext() } // from an earlier GL context, if any
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        texture.attachToGLContext(textureId)
        program = buildProgram(VERTEX, FRAGMENT)
        aPos = GLES30.glGetAttribLocation(program, "aPos")
        aUv = GLES30.glGetAttribLocation(program, "aUv")
        uTexMatrix = GLES30.glGetUniformLocation(program, "uTexMatrix")
    }

    /** Draws the latest camera image filling a [viewWidth] x [viewHeight] view (cropped, never stretched). */
    fun draw(viewWidth: Int, viewHeight: Int) {
        if (!hasFrame || bufferWidth == 0) return
        try {
            texture.updateTexImage()
            texture.getTransformMatrix(texMatrix)
        } catch (e: RuntimeException) {
            return // surface released (screen closing)
        }
        val rot = Math.floorMod(rotationDegrees, 360)
        // The buffer's shape as it appears on screen once turned upright.
        val turned = rot == 90 || rot == 270
        // Some phones (e.g. Xiaomi) already put the sensor's turn into the texture's own transform:
        // its matrix then swaps x and y (off-diagonal terms). Turning again would show the room
        // sideways, so only turn here when the texture didn't.
        val textureTurns = kotlin.math.abs(texMatrix[1]) + kotlin.math.abs(texMatrix[4]) >
            kotlin.math.abs(texMatrix[0]) + kotlin.math.abs(texMatrix[5])
        if (!loggedMatrix) {
            loggedMatrix = true
            android.util.Log.i("ForensimAR", "Camera feed ${bufferWidth}x$bufferHeight, rotation $rot, texture turns: $textureTurns")
        }
        val turn = if (textureTurns) 0 else rot
        val bufAspect = if (turned) bufferHeight.toFloat() / bufferWidth else bufferWidth.toFloat() / bufferHeight
        val viewAspect = viewWidth.toFloat() / viewHeight.coerceAtLeast(1)
        // Fill the view: crop the camera image on the side that's too long.
        val sx = if (viewAspect < bufAspect) viewAspect / bufAspect else 1f
        val sy = if (viewAspect < bufAspect) 1f else bufAspect / viewAspect
        for (i in 0 until 4) {
            val s = 0.5f + (corners[i * 2] - 0.5f) * sx
            val t = 0.5f + (corners[i * 2 + 1] - 0.5f) * sy
            // Screen point -> buffer point for a buffer turned [rot] degrees clockwise.
            val (u, v) = when (turn) {
                90 -> (1f - t) to s
                180 -> (1f - s) to (1f - t)
                270 -> t to (1f - s)
                else -> s to t
            }
            uv[i * 2] = u; uv[i * 2 + 1] = v
        }
        uvs.position(0); uvs.put(uv); uvs.position(0)
        positions.position(0)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(program)
        // The 3D engine leaves its last vertex array / buffer bound; with one bound, the plain
        // (client-side) arrays below are refused and nothing is drawn, i.e. a black background.
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 0, positions)
        GLES30.glVertexAttribPointer(aUv, 2, GLES30.GL_FLOAT, false, 0, uvs)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glEnableVertexAttribArray(aUv)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aUv)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    /** Call when the screen closes, after the camera has been unbound. */
    fun release() {
        surface?.release()
        texture.release()
    }

    private companion object {
        fun buffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }

        fun buildProgram(vs: String, fs: String): Int {
            fun compile(kind: Int, src: String) = GLES30.glCreateShader(kind).also {
                GLES30.glShaderSource(it, src); GLES30.glCompileShader(it)
            }
            return GLES30.glCreateProgram().also { p ->
                GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
                GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
                GLES30.glLinkProgram(p)
            }
        }

        const val VERTEX = """
attribute vec2 aPos;
attribute vec2 aUv;
uniform mat4 uTexMatrix;
varying vec2 vUv;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vUv = (uTexMatrix * vec4(aUv, 0.0, 1.0)).xy;
}
"""
        const val FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vUv;
uniform samplerExternalOES uCamera;
void main() { gl_FragColor = texture2D(uCamera, vUv); }
"""
    }
}
