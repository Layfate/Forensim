package com.forensim.app.engine.renderer

import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.Victim
import com.forensim.app.engine.scene.VictimPose
import com.forensim.app.engine.scene.VictimSex

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.forensim.app.engine.math.Aabb
import com.forensim.app.engine.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal OpenGL ES 3.0 renderer. Draws lit, coloured primitives and textured `.glb` models
 * (assets/models, see art/README.md) with a room light, a camera "flashlight" cone and distance
 * fog. Models are read from [assets]; without it (or when a file is missing) callers fall back to
 * primitives.
 */
abstract class GlesRenderer(private val assets: AssetManager? = null) : GLSurfaceView.Renderer {
    private var program = 0
    private var uMap = 0
    private var uUseMap = 0
    private var uOpacity = 0
    private var uEmissiveColor = 0
    private var uAlphaCutoff = 0
    /** GPU copies of loaded models for the current GL context (null = file missing or unreadable). */
    private val gpuModels = HashMap<String, GpuModel?>()
    private val transparent = ArrayList<QueuedPrim>()
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uEye = 0
    private var uViewDir = 0
    private var uEmissive = 0
    private var uAmbient = 0
    private var uFlashlight = 0
    private var uRoomLight = 0
    private var uTex = 0
    private var uTexMode = 0
    private var uTexRepeat = 0
    private var tapeTexture = 0
    private lateinit var cube: Mesh
    private lateinit var cylinder: Mesh
    private lateinit var sphere: Mesh

    protected val projection = FloatArray(16)
    protected val view = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var lastNanos = 0L
    protected var aspect = 1f

    var clearColor = floatArrayOf(0.03f, 0.05f, 0.08f)
    /** 0 for a see-through view drawn over something else (the camera-view fallback). */
    var clearAlpha = 1f
    var ambient = 0.32f
    var flashlight = 1f
    var roomLight = Vec3(0f, 2.5f, 0f)
    private var eye = Vec3.ZERO
    private var viewDir = Vec3(0f, 0f, -1f)

    /** Rolling average frame time in ms (for profiling, plan section 13). */
    @Volatile var frameMs = 0f; private set

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VERTEX, FRAGMENT)
        uMvp = GLES30.glGetUniformLocation(program, "uMVP")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uEye = GLES30.glGetUniformLocation(program, "uEye")
        uViewDir = GLES30.glGetUniformLocation(program, "uViewDir")
        uTex = GLES30.glGetUniformLocation(program, "uTex")
        uTexMode = GLES30.glGetUniformLocation(program, "uTexMode")
        uTexRepeat = GLES30.glGetUniformLocation(program, "uTexRepeat")
        tapeTexture = createTapeTexture()
        uEmissive = GLES30.glGetUniformLocation(program, "uEmissive")
        uAmbient = GLES30.glGetUniformLocation(program, "uAmbient")
        uFlashlight = GLES30.glGetUniformLocation(program, "uFlashlight")
        uRoomLight = GLES30.glGetUniformLocation(program, "uRoomLight")
        uMap = GLES30.glGetUniformLocation(program, "uMap")
        uUseMap = GLES30.glGetUniformLocation(program, "uUseMap")
        uOpacity = GLES30.glGetUniformLocation(program, "uOpacity")
        uEmissiveColor = GLES30.glGetUniformLocation(program, "uEmissiveColor")
        uAlphaCutoff = GLES30.glGetUniformLocation(program, "uAlphaCutoff")
        GLES30.glUseProgram(program)
        GLES30.glUniform1i(uMap, 1)
        resetMaterial()
        cube = Mesh(Meshes.cube())
        cylinder = Mesh(Meshes.cylinder(20))
        sphere = Mesh(Meshes.sphere(12, 18))
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        // A new GL context: GPU copies from an earlier one are gone. Upload what this view needs now
        // so the first frames do not stall.
        gpuModels.clear()
        preloadModels().forEach { gpuModel(it) }
        lastNanos = System.nanoTime()
    }

    /** Model paths (under assets/) to upload when the surface is created. */
    protected open fun preloadModels(): List<String> = emptyList()

    /** Size of the GL surface in pixels (for projecting points to the screen). */
    protected var surfaceWidth = 1; private set
    protected var surfaceHeight = 1; private set

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        surfaceWidth = width; surfaceHeight = height
        aspect = width.toFloat() / maxOf(1, height)
        updateProjection()
    }

    /** Vertical field of view in degrees, evaluated whenever the projection is rebuilt. */
    open fun fieldOfView() = 68f

    @Volatile private var projectionDirty = false

    /** Ask for the projection to be rebuilt on the GL thread (e.g. after a field-of-view change). */
    protected fun invalidateProjection() { projectionDirty = true }

    /**
     * Near and far clip distances in metres. Depth precision depends mostly on the near plane, so
     * views that never get very close to geometry should push it out to avoid z-fighting.
     */
    open fun clipRange(): Pair<Float, Float> = 0.05f to 60f

    private fun updateProjection() {
        projectionDirty = false
        val (near, far) = clipRange()
        Matrix.perspectiveM(projection, 0, fieldOfView(), aspect, near, far)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        frameMs = frameMs * 0.95f + dt * 1000f * 0.05f
        if (projectionDirty) updateProjection()
        update(dt)
        GLES30.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearAlpha)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawBackground()
        GLES30.glUseProgram(program)
        Matrix.multiplyMM(viewProj, 0, projection, 0, view, 0)
        GLES30.glUniform3f(uEye, eye.x, eye.y, eye.z)
        GLES30.glUniform3f(uViewDir, viewDir.x, viewDir.y, viewDir.z)
        GLES30.glUniform1f(uAmbient, ambient)
        GLES30.glUniform1f(uFlashlight, flashlight)
        GLES30.glUniform3f(uRoomLight, roomLight.x, roomLight.y, roomLight.z)
        draw()
        flushTransparent()
    }

    protected abstract fun update(dt: Float)
    protected abstract fun draw()

    /** Drawn after the clear and before the scene, with its own program (the AR camera image). */
    protected open fun drawBackground() {}

    /**
     * For views whose [view] and [projection] come from elsewhere (ARCore): the eye position and
     * looking direction the lighting uses.
     */
    protected fun setEye(eyePos: Vec3, lookDir: Vec3) {
        eye = eyePos
        viewDir = lookDir.normalized()
    }

    protected fun setCamera(eyePos: Vec3, target: Vec3) {
        eye = eyePos
        viewDir = (target - eyePos).normalized()
        Matrix.setLookAtM(view, 0, eyePos.x, eyePos.y, eyePos.z, target.x, target.y, target.z, 0f, 1f, 0f)
    }

    protected fun drawBox(box: Aabb, color: FloatArray, emissive: Float = 0f) {
        val c = box.center
        val s = box.size
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, c.x, c.y, c.z)
        Matrix.scaleM(model, 0, s.x, s.y, s.z)
        submit(cube, color, emissive)
    }

    /** Cylinder fitted to [box]; [alongZ] lays it on its side (e.g. a cartridge case). */
    protected fun drawCylinder(box: Aabb, color: FloatArray, emissive: Float = 0f, alongZ: Boolean = false, spinY: Float = 0f) {
        val c = box.center
        val s = box.size
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, c.x, c.y, c.z)
        if (spinY != 0f) Matrix.rotateM(model, 0, spinY, 0f, 1f, 0f)
        if (alongZ) {
            Matrix.rotateM(model, 0, 90f, 1f, 0f, 0f)
            Matrix.scaleM(model, 0, s.x, s.z, s.y)
        } else {
            Matrix.scaleM(model, 0, s.x, s.y, s.z)
        }
        submit(cylinder, color, emissive)
    }

    /**
     * Police tape strip from [a] to [b]: a thin band printed "CRIME SCENE · DO NOT CROSS" on both
     * faces, repeating every ~1.5 m. Works at any angle (doorway X, sagging perimeter lines).
     */
    protected fun drawTape(a: Vec3, b: Vec3, width: Float = 0.08f) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val horizontal = kotlin.math.sqrt(dx * dx + dz * dz)
        val length = kotlin.math.sqrt(horizontal * horizontal + dy * dy)
        if (length < 0.01f) return
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, (a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
        Matrix.rotateM(model, 0, Math.toDegrees(kotlin.math.atan2(-dz, dx).toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(kotlin.math.atan2(dy, horizontal).toDouble()).toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(model, 0, length, width, 0.004f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tapeTexture)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform1f(uTexMode, 1f)
        GLES30.glUniform1f(uTexRepeat, length / 1.5f)
        submit(cube, TAPE_YELLOW, 0.22f)
        GLES30.glUniform1f(uTexMode, 0f)
    }

    /**
     * Scene-scale model for an evidence item fitted to [b]. Built from a few primitives so each
     * type reads at a glance (blade + handle, grip + slide, scattered shards...).
     */
    protected fun drawEvidence(type: EvidenceType, b: Aabb, emissive: Float) {
        val c = b.center
        val s = b.size
        fun part(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float, color: FloatArray) =
            drawBox(Aabb(Vec3(b.min.x + s.x * x0, b.min.y + s.y * y0, b.min.z + s.z * z0),
                Vec3(b.min.x + s.x * x1, b.min.y + s.y * y1, b.min.z + s.z * z1)), color, emissive)
        when (type) {
            EvidenceType.CARTRIDGE_CASE, EvidenceType.BULLET -> drawCylinder(b, type.color, emissive, alongZ = true)
            EvidenceType.BLOODSTAIN -> {
                drawCylinder(b, type.color, emissive)
                drawCylinder(Aabb.onSurface(b.max.x + 0.04f, b.min.y, c.z - 0.05f, 0.05f, s.y, 0.04f), type.color, emissive)
                drawCylinder(Aabb.onSurface(b.min.x - 0.03f, b.min.y, c.z + 0.06f, 0.03f, s.y, 0.03f), type.color, emissive)
            }
            EvidenceType.KNIFE -> {
                part(0.2f, 0.8f, 0f, 0.55f, 0f, 0.38f, floatArrayOf(0.12f, 0.1f, 0.09f))      // handle
                part(0.35f, 0.65f, 0f, 0.25f, 0.38f, 1f, type.color)                            // blade
            }
            EvidenceType.FIREARM -> {
                part(0.1f, 0.9f, 0.55f, 1f, 0f, 1f, type.color)                                 // slide
                part(0.15f, 0.85f, 0f, 0.6f, 0f, 0.38f, floatArrayOf(0.24f, 0.2f, 0.17f))       // grip
            }
            EvidenceType.BLUNT_WEAPON -> drawCylinder(b, type.color, emissive, alongZ = true)
            EvidenceType.BROKEN_GLASS -> {
                val shards = listOf(0.1f to 0.2f, 0.55f to 0.1f, 0.3f to 0.6f, 0.75f to 0.55f, 0.45f to 0.85f)
                shards.forEachIndexed { i, (fx, fz) ->
                    val w = 0.04f + 0.012f * (i % 3)
                    drawBox(Aabb.onSurface(b.min.x + s.x * fx, b.min.y, b.min.z + s.z * fz, w, s.y, w * 0.7f), type.color, emissive + 0.1f)
                }
            }
            EvidenceType.SHOE_PRINT -> {
                part(0.05f, 0.95f, 0f, 1f, 0f, 0.62f, type.color)                               // sole
                part(0.15f, 0.85f, 0f, 1f, 0.7f, 1f, type.color)                                // heel
            }
            EvidenceType.DIGITAL_SCALE -> {
                drawBox(b, type.color, emissive)
                part(0.15f, 0.85f, 1f, 1.15f, 0.12f, 0.4f, floatArrayOf(0.55f, 0.75f, 0.55f))  // display
            }
            else -> drawBox(b, type.color, emissive)
        }
    }

    // ------------------------------------------------------------------ victim body

    private val bodyBase = FloatArray(16)

    /**
     * A person lying on the floor, centred on ([x], [z]) with the head towards [yawDeg] (0 = +x,
     * 90 = -z). Drawn from the sculpted, textured body model for the victim's sex and position
     * (models/victim, see art/README.md); without it, from lit primitives. Male and female differ
     * in build, clothes and hair. Face up shows the face and the toes pointing up; face down shows
     * the head turned to one side, one arm raised beside it and the soles of the shoes.
     */
    protected fun drawVictim(victim: Victim, x: Float, z: Float, yawDeg: Float, emissive: Float = 0f) {
        if (drawModel(ModelPaths.victim(victim), x, 0f, z, emissive, yawDeg)) return
        Matrix.setIdentityM(bodyBase, 0)
        Matrix.translateM(bodyBase, 0, x, 0f, z)
        Matrix.rotateM(bodyBase, 0, yawDeg, 0f, 1f, 0f)
        val k = victim.length / 1.76f
        Matrix.scaleM(bodyBase, 0, k, 1f, k)
        val male = victim.sex == VictimSex.MALE
        val up = victim.pose == VictimPose.FACE_UP
        val skin = if (male) SKIN_MALE else SKIN_FEMALE
        val top = if (male) SHIRT else BLOUSE
        val bottom = if (male) TROUSERS else SKIRT
        val hair = if (male) HAIR_MALE else HAIR_FEMALE
        val shoes = if (male) SHOES_MALE else SHOES_FEMALE
        val e = emissive

        // Head, neck, ears.
        val headW = if (male) 0.17f else 0.16f
        ellipsoid(0.77f, 0.105f, 0f, 0.22f, 0.19f, headW, skin, e)
        limb(0.6f, 0f, 0.69f, 0f, 0.085f, 0.052f, skin, e)
        ellipsoid(0.77f, 0.1f, headW / 2, 0.05f, 0.06f, 0.025f, skin, e)
        ellipsoid(0.77f, 0.1f, -headW / 2, 0.05f, 0.06f, 0.025f, skin, e)
        if (up) {
            // Face on the upper side of the head: closed eyes, brows, nose and mouth.
            box(0.805f, 0.818f, 0.188f, 0.196f, 0.025f, 0.055f, FEATURE, e)
            box(0.805f, 0.818f, 0.188f, 0.196f, -0.055f, -0.025f, FEATURE, e)
            box(0.83f, 0.838f, 0.184f, 0.192f, 0.022f, 0.06f, hair, e)
            box(0.83f, 0.838f, 0.184f, 0.192f, -0.06f, -0.022f, hair, e)
            box(0.765f, 0.8f, 0.19f, 0.218f, -0.014f, 0.014f, skin, e)
            box(0.722f, 0.73f, 0.184f, 0.192f, -0.028f, 0.028f, if (male) FEATURE else LIPS, e)
            // Hair round the crown and the back of the head, which rests on the floor.
            ellipsoid(0.79f, 0.085f, 0f, 0.225f, 0.17f, headW + 0.012f, hair, e)
            if (!male) {
                // Long hair spread on the floor round the head.
                flat(0.83f, 0f, 0.3f, 0.36f, hair, e)
                flat(0.62f, 0.13f, 0.18f, 0.12f, hair, e)
                flat(0.62f, -0.13f, 0.18f, 0.12f, hair, e)
            }
        } else {
            // Back of the head facing up: covered in hair.
            ellipsoid(0.775f, 0.115f, 0f, 0.228f, 0.19f, headW + 0.012f, hair, e)
            if (!male) {
                // Long hair over the nape and the shoulders.
                box(0.5f, 0.7f, 0.17f, 0.215f, -0.1f, 0.1f, hair, e)
                flat(0.86f, 0f, 0.2f, 0.3f, hair, e)
            }
        }

        // Torso: shoulders, chest, waist, hips.
        val chestW = if (male) 0.42f else 0.35f
        val hipW = if (male) 0.35f else 0.4f
        ellipsoid(0.55f, 0.1f, chestW / 2 - 0.04f, 0.15f, 0.17f, 0.15f, top, e)
        ellipsoid(0.55f, 0.1f, -chestW / 2 + 0.04f, 0.15f, 0.17f, 0.15f, top, e)
        trunk(0.18f, 0.6f, 0.1f, chestW, 0.2f, top, e)
        trunk(0.0f, 0.22f, 0.095f, (chestW + hipW) / 2 - 0.04f, 0.18f, top, e)
        trunk(-0.13f, 0.04f, 0.09f, hipW, 0.18f, bottom, e)
        if (male) {
            box(0.0f, 0.04f, 0.0f, 0.187f, -0.16f, 0.16f, BELT, e)
        } else if (up) {
            ellipsoid(0.42f, 0.18f, 0.075f, 0.12f, 0.08f, 0.11f, top, e)
            ellipsoid(0.42f, 0.18f, -0.075f, 0.12f, 0.08f, 0.11f, top, e)
        }
        if (!male) trunk(-0.42f, 0.02f, 0.085f, 0.44f, 0.17f, SKIRT, e) // knee-length skirt

        // Legs: trousers (male) or bare legs below the skirt (female).
        val legs = if (male) bottom else skin
        for (side in floatArrayOf(1f, -1f)) {
            limb(-0.06f, 0.1f * side, -0.48f, 0.12f * side, 0.08f, 0.076f, legs, e)
            limb(-0.48f, 0.12f * side, -0.83f, 0.14f * side, 0.06f, 0.058f, legs, e)
            ellipsoid(-0.48f, 0.075f, 0.12f * side, 0.1f, 0.12f, 0.12f, legs, e) // knee
            if (up) {
                box(-0.9f, -0.82f, 0f, if (male) 0.15f else 0.13f, 0.14f * side - 0.05f, 0.14f * side + 0.05f, shoes, e) // toes up
            } else {
                box(-0.94f, -0.82f, 0f, 0.075f, 0.14f * side - 0.048f, 0.14f * side + 0.048f, shoes, e)
                box(-0.94f, -0.82f, 0.075f, 0.085f, 0.14f * side - 0.048f, 0.14f * side + 0.048f, SOLE, e) // sole showing
            }
        }

        // Arms. Face up: both at the sides. Face down: the right arm is raised beside the head.
        val sleeve = if (male) top else skin
        val armY = 0.065f
        fun arm(side: Float, raised: Boolean) {
            val sx = 0.54f
            val sz = (chestW / 2 + 0.02f) * side
            val ex = if (raised) 0.72f else 0.28f
            val ez = (if (raised) 0.36f else 0.31f) * side
            val wx = if (raised) 0.86f else 0.04f
            val wz = (if (raised) 0.25f else 0.34f) * side
            limb(sx, sz, ex, ez, armY, 0.05f, sleeve, e)
            limb(ex, ez, wx, wz, armY - 0.005f, 0.042f, skin, e)
            ellipsoid(wx + (wx - ex) * 0.22f, 0.035f, wz + (wz - ez) * 0.22f, 0.1f, 0.05f, 0.075f, skin, e) // hand
        }
        arm(1f, raised = !up)
        arm(-1f, raised = false)
    }

    /** Ellipsoid centred at local ([cx], [cy], [cz]) with diameters [sx] x [sy] x [sz]. */
    private fun ellipsoid(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, color: FloatArray, emissive: Float) {
        System.arraycopy(bodyBase, 0, model, 0, 16)
        Matrix.translateM(model, 0, cx, cy, cz)
        Matrix.scaleM(model, 0, sx, sy, sz)
        submit(sphere, color, emissive)
    }

    /** Local axis-aligned box. */
    private fun box(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float, color: FloatArray, emissive: Float) {
        System.arraycopy(bodyBase, 0, model, 0, 16)
        Matrix.translateM(model, 0, (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        Matrix.scaleM(model, 0, x1 - x0, y1 - y0, z1 - z0)
        submit(cube, color, emissive)
    }

    /** Thin disc lying on the floor (spread hair). */
    private fun flat(cx: Float, cz: Float, sx: Float, sz: Float, color: FloatArray, emissive: Float) {
        System.arraycopy(bodyBase, 0, model, 0, 16)
        Matrix.translateM(model, 0, cx, 0.008f, cz)
        Matrix.scaleM(model, 0, sx, 0.016f, sz)
        submit(cylinder, color, emissive)
    }

    /** Body section along x from [x0] to [x1]: an elliptical cylinder [width] wide and [height] tall, rounded at the ends. */
    private fun trunk(x0: Float, x1: Float, cy: Float, width: Float, height: Float, color: FloatArray, emissive: Float) {
        System.arraycopy(bodyBase, 0, model, 0, 16)
        Matrix.translateM(model, 0, (x0 + x1) / 2, cy, 0f)
        Matrix.rotateM(model, 0, -90f, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, height, x1 - x0, width)
        submit(cylinder, color, emissive)
        ellipsoid(x0, cy, 0f, 0.05f, height, width, color, emissive)
        ellipsoid(x1, cy, 0f, 0.05f, height, width, color, emissive)
    }

    /** Round limb lying flat from (x0, z0) to (x1, z1) at height [cy], radius [r], with rounded joints. */
    private fun limb(x0: Float, z0: Float, x1: Float, z1: Float, cy: Float, r: Float, color: FloatArray, emissive: Float) {
        val dx = x1 - x0
        val dz = z1 - z0
        val len = kotlin.math.sqrt(dx * dx + dz * dz)
        System.arraycopy(bodyBase, 0, model, 0, 16)
        Matrix.translateM(model, 0, (x0 + x1) / 2, cy, (z0 + z1) / 2)
        Matrix.rotateM(model, 0, Math.toDegrees(kotlin.math.atan2(-dz, dx).toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(model, 0, -90f, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, r * 2f, len, r * 2f)
        submit(cylinder, color, emissive)
        ellipsoid(x0, cy, z0, r * 2f, r * 2f, r * 2f, color, emissive)
        ellipsoid(x1, cy, z1, r * 2f, r * 2f, r * 2f, color, emissive)
    }

    /** Draw a cube with an arbitrary pre-built model matrix (used by the viewer for rotations). */
    protected fun drawCubeModel(m: FloatArray, color: FloatArray, emissive: Float = 0f) {
        System.arraycopy(m, 0, model, 0, 16)
        submit(cube, color, emissive)
    }

    /**
     * A see-through black disc ([m] places a unit cylinder): stacked a few times with growing
     * size it makes a soft contact shadow, so an object looks like it rests on a real surface.
     */
    protected fun drawShadowDisc(m: FloatArray, opacity: Float) {
        resetMaterial()
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glUniform1f(uOpacity, opacity)
        System.arraycopy(m, 0, model, 0, 16)
        submit(cylinder, floatArrayOf(0f, 0f, 0f), 0f)
        GLES30.glUniform1f(uOpacity, 1f)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    protected fun drawCylinderModel(m: FloatArray, color: FloatArray, emissive: Float = 0f) {
        System.arraycopy(m, 0, model, 0, 16)
        submit(cylinder, color, emissive)
    }

    // ------------------------------------------------------------------ .glb models

    /** True when [path] (under assets/) loaded; use it to choose between a model and primitives. */
    protected fun hasModel(path: String) = gpuModel(path) != null

    /**
     * Draws the model at [path] with its origin at ([x], [y], [z]) turned [yawDeg] about Y.
     * Returns false (drawing nothing) when the model is unavailable, so callers can fall back.
     * [emissive] brightens it the same way as for primitives (focus pulse).
     */
    protected fun drawModel(path: String, x: Float, y: Float, z: Float, emissive: Float = 0f, yawDeg: Float = 0f): Boolean {
        val gm = gpuModel(path) ?: return false
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f)
        submitModel(gm, model, emissive)
        return true
    }

    /** Draws the model at [path] with a pre-built model matrix [m]. */
    protected fun drawModelMatrix(path: String, m: FloatArray, emissive: Float = 0f): Boolean {
        val gm = gpuModel(path) ?: return false
        submitModel(gm, m, emissive)
        return true
    }

    /** Bounds (minX, minY, minZ, maxX, maxY, maxZ) of a loaded model, or null. */
    protected fun modelBounds(path: String): FloatArray? = ModelCache.get(assets, path)?.bounds

    private fun gpuModel(path: String): GpuModel? = gpuModels.getOrPut(path) {
        ModelCache.get(assets, path)?.let { cpu ->
            try {
                GpuModel(cpu)
            } catch (e: RuntimeException) {
                Log.e("ForenSim", "Could not upload $path", e)
                null
            }
        }
    }

    private fun submitModel(gm: GpuModel, m: FloatArray, emissive: Float) {
        for (p in gm.prims) {
            if (p.material.blend) {
                // Sort by the distance of the primitive's centre (in world space) from the eye.
                val c = p.center
                val wx = m[0] * c[0] + m[4] * c[1] + m[8] * c[2] + m[12]
                val wy = m[1] * c[0] + m[5] * c[1] + m[9] * c[2] + m[13]
                val wz = m[2] * c[0] + m[6] * c[1] + m[10] * c[2] + m[14]
                val dx = wx - eye.x; val dy = wy - eye.y; val dz = wz - eye.z
                transparent += QueuedPrim(p, m.copyOf(), emissive, dx * dx + dy * dy + dz * dz)
            } else {
                drawPrim(p, m, emissive)
            }
        }
    }

    private fun drawPrim(p: GpuPrim, m: FloatArray, emissive: Float) {
        val mat = p.material
        System.arraycopy(m, 0, model, 0, 16)
        if (p.texture != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, p.texture)
            GLES30.glUniform1f(uUseMap, 1f)
        }
        GLES30.glUniform1f(uOpacity, mat.baseColor[3])
        GLES30.glUniform3f(uEmissiveColor, mat.emissive[0], mat.emissive[1], mat.emissive[2])
        GLES30.glUniform1f(uAlphaCutoff, if (mat.blend) 0.01f else mat.alphaCutoff)
        if (mat.doubleSided) GLES30.glDisable(GLES30.GL_CULL_FACE)
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES30.glUniform3f(uColor, mat.baseColor[0], mat.baseColor[1], mat.baseColor[2])
        GLES30.glUniform1f(uEmissive, emissive)
        GLES30.glBindVertexArray(p.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        if (mat.doubleSided) GLES30.glEnable(GLES30.GL_CULL_FACE)
        resetMaterial()
    }

    /** Transparent parts (glass, bags, stains, prints) after everything opaque, far to near. */
    private fun flushTransparent() {
        if (transparent.isEmpty()) return
        transparent.sortByDescending { it.distSq }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        for (q in transparent) drawPrim(q.prim, q.matrix, q.emissive)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        transparent.clear()
    }

    private fun resetMaterial() {
        GLES30.glUniform1f(uUseMap, 0f)
        GLES30.glUniform1f(uOpacity, 1f)
        GLES30.glUniform3f(uEmissiveColor, 0f, 0f, 0f)
        GLES30.glUniform1f(uAlphaCutoff, 0f)
    }

    private class QueuedPrim(val prim: GpuPrim, val matrix: FloatArray, val emissive: Float, val distSq: Float)

    private class GpuPrim(val vao: Int, val indexCount: Int, val material: GlbModel.Material, val texture: Int, val center: FloatArray)

    private class GpuModel(cpu: GlbModel) {
        val prims: List<GpuPrim>

        init {
            val textures = HashMap<Int, Int>()
            fun texture(image: Int): Int = textures.getOrPut(image) { uploadTexture(cpu.images[image]) }
            prims = cpu.primitives.map { p ->
                val ids = IntArray(1)
                GLES30.glGenVertexArrays(1, ids, 0)
                val vao = ids[0]
                GLES30.glBindVertexArray(vao)
                val bufs = IntArray(2)
                GLES30.glGenBuffers(2, bufs, 0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
                val vb = ByteBuffer.allocateDirect(p.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(p.vertices)
                vb.position(0)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, p.vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)
                val stride = GlbModel.STRIDE * 4
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
                GLES30.glEnableVertexAttribArray(2)
                GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bufs[1])
                val ib = ByteBuffer.allocateDirect(p.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(p.indices)
                ib.position(0)
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.indices.size * 4, ib, GLES30.GL_STATIC_DRAW)
                GLES30.glBindVertexArray(0)
                val tex = p.material.baseColorImage.takeIf { it in cpu.images.indices }?.let(::texture) ?: 0
                // Centre of the primitive's vertices, for sorting transparent parts.
                val c = FloatArray(3)
                for (i in 0 until p.vertexCount) for (k in 0 until 3) c[k] += p.vertices[i * GlbModel.STRIDE + k]
                for (k in 0 until 3) c[k] /= p.vertexCount.coerceAtLeast(1)
                GpuPrim(vao, p.indices.size, p.material, tex, c)
            }
        }

        private fun uploadTexture(png: ByteArray): Int {
            // Straight (not premultiplied) alpha, to match the blend function and keep stain edges clean.
            val opts = BitmapFactory.Options().apply { inPremultiplied = false }
            val bmp = BitmapFactory.decodeByteArray(png, 0, png.size, opts) ?: return 0
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
            bmp.recycle()
            return ids[0]
        }
    }

    private fun submit(mesh: Mesh, color: FloatArray, emissive: Float) {
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES30.glUniform3f(uColor, color[0], color[1], color[2])
        GLES30.glUniform1f(uEmissive, emissive)
        mesh.draw()
    }

    private class Mesh(data: FloatArray) {
        private val vao: Int
        private val count = data.size / 6

        init {
            val ids = IntArray(1)
            GLES30.glGenVertexArrays(1, ids, 0)
            vao = ids[0]
            GLES30.glBindVertexArray(vao)
            val vbo = IntArray(1)
            GLES30.glGenBuffers(1, vbo, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(data)
            buf.position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
            GLES30.glBindVertexArray(0)
        }

        fun draw() {
            GLES30.glBindVertexArray(vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.e("ForenSim", "Shader error: " + GLES30.glGetShaderInfoLog(s))
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        return p
    }

    /** Yellow tape printed with black text, tiled along the strip (GL_REPEAT on s). */
    private fun createTapeTexture(): Int {
        val w = 1024
        val h = 128
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.rgb(246, 196, 0))
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(20, 20, 20)
            textSize = 62f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
            letterSpacing = 0.06f
        }
        canvas.drawRect(0f, 0f, w.toFloat(), 7f, ink)
        canvas.drawRect(0f, h - 7f, w.toFloat(), h.toFloat(), ink)
        val baseline = h / 2f - (ink.descent() + ink.ascent()) / 2f
        canvas.drawText("CRIME SCENE  \u2022  DO NOT CROSS  \u2022", w / 2f, baseline, ink)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        bmp.recycle()
        return ids[0]
    }

    companion object {
        private val TAPE_YELLOW = floatArrayOf(0.96f, 0.77f, 0f)
        private val SKIN_MALE = floatArrayOf(0.72f, 0.52f, 0.4f)
        private val SKIN_FEMALE = floatArrayOf(0.82f, 0.62f, 0.5f)
        private val SHIRT = floatArrayOf(0.2f, 0.3f, 0.5f)
        private val TROUSERS = floatArrayOf(0.17f, 0.18f, 0.21f)
        private val BELT = floatArrayOf(0.22f, 0.14f, 0.08f)
        private val BLOUSE = floatArrayOf(0.62f, 0.17f, 0.24f)
        private val SKIRT = floatArrayOf(0.13f, 0.14f, 0.22f)
        private val HAIR_MALE = floatArrayOf(0.09f, 0.07f, 0.05f)
        private val HAIR_FEMALE = floatArrayOf(0.27f, 0.16f, 0.08f)
        private val SHOES_MALE = floatArrayOf(0.07f, 0.06f, 0.06f)
        private val SHOES_FEMALE = floatArrayOf(0.3f, 0.08f, 0.1f)
        private val SOLE = floatArrayOf(0.2f, 0.2f, 0.2f)
        private val FEATURE = floatArrayOf(0.25f, 0.15f, 0.12f)
        private val LIPS = floatArrayOf(0.62f, 0.3f, 0.3f)

        private const val VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUV;
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vLocal;
out vec3 vLocalN;
out vec2 vUV;
void main() {
    vLocal = aPos;
    vLocalN = aNormal;
    vUV = aUV;
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = normalize(mat3(uModel) * aNormal);
    gl_Position = uMVP * vec4(aPos, 1.0);
}"""

        // highp: full-precision lighting, so the flashlight and fog have no banding up close.
        private const val FRAGMENT = """#version 300 es
precision highp float;
in vec3 vWorld;
in vec3 vNormal;
in vec3 vLocal;
in vec3 vLocalN;
in vec2 vUV;
uniform sampler2D uTex;
uniform float uTexMode;
uniform float uTexRepeat;
uniform sampler2D uMap;
uniform float uUseMap;
uniform float uOpacity;
uniform float uAlphaCutoff;
uniform vec3 uEmissiveColor;
uniform vec3 uColor;
uniform vec3 uEye;
uniform vec3 uViewDir;
uniform vec3 uRoomLight;
uniform float uEmissive;
uniform float uAmbient;
uniform float uFlashlight;
out vec4 fragColor;
void main() {
    vec3 base = uColor;
    float alpha = uOpacity;
    if (uTexMode > 0.5) {
        // Printed tape: u runs along the strip, mirrored on the back face so text reads correctly.
        float u = (vLocal.x + 0.5) * uTexRepeat;
        if (vLocalN.z < -0.5) u = -u;
        base = texture(uTex, vec2(u, 1.0 - (vLocal.y + 0.5))).rgb;
    }
    if (uUseMap > 0.5) {
        // Model base colour texture (glTF UVs: origin at the top-left, as uploaded).
        vec4 t = texture(uMap, vUV);
        base *= t.rgb;
        alpha *= t.a;
    }
    if (alpha < uAlphaCutoff) discard;
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n; // double-sided model parts (paper, plastic bag)
    vec3 toLight = normalize(uRoomLight - vWorld);
    float diffuse = max(dot(n, toLight), 0.0) * 0.5;
    vec3 toFrag = vWorld - uEye;
    float d = max(length(toFrag), 0.001);
    vec3 dirF = toFrag / d;
    float cone = smoothstep(0.82, 0.96, dot(dirF, uViewDir));
    float spot = cone * max(dot(n, -dirF), 0.0) * uFlashlight / (1.0 + 0.10 * d * d);
    vec3 c = base * (uAmbient + diffuse + spot * 1.15) + base * uEmissive + uEmissiveColor;
    float fog = clamp((d - 7.0) / 14.0, 0.0, 1.0);
    c = mix(c, vec3(0.03, 0.05, 0.08), fog);
    fragColor = vec4(c, alpha);
}"""
    }
}

/** Unit primitives centred on the origin, interleaved position + normal. */
object Meshes {
    fun cube(): FloatArray {
        val faces = listOf(
            floatArrayOf(0f, 0f, 1f) to listOf(-1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1),
            floatArrayOf(0f, 0f, -1f) to listOf(1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1),
            floatArrayOf(1f, 0f, 0f) to listOf(1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1),
            floatArrayOf(-1f, 0f, 0f) to listOf(-1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1),
            floatArrayOf(0f, 1f, 0f) to listOf(-1, 1, 1, 1, 1, 1, 1, 1, -1, -1, 1, -1),
            floatArrayOf(0f, -1f, 0f) to listOf(-1, -1, -1, 1, -1, -1, 1, -1, 1, -1, -1, 1),
        )
        val out = ArrayList<Float>()
        for ((n, q) in faces) {
            val v = (0 until 4).map { floatArrayOf(q[it * 3] * 0.5f, q[it * 3 + 1] * 0.5f, q[it * 3 + 2] * 0.5f) }
            for (i in intArrayOf(0, 1, 2, 0, 2, 3)) { out.addAll(v[i].toList()); out.addAll(n.toList()) }
        }
        return out.toFloatArray()
    }

    /** Sphere of diameter 1 centred on the origin, outward normals, counter-clockwise from outside. */
    fun sphere(stacks: Int, slices: Int): FloatArray {
        val out = ArrayList<Float>()
        fun p(i: Int, j: Int): FloatArray {
            val t = PI * i / stacks
            val f = 2 * PI * j / slices
            return floatArrayOf((sin(t) * cos(f)).toFloat() * 0.5f, cos(t).toFloat() * 0.5f, (sin(t) * sin(f)).toFloat() * 0.5f)
        }
        fun vtx(v: FloatArray) { out.addAll(listOf(v[0], v[1], v[2], v[0] * 2, v[1] * 2, v[2] * 2)) }
        for (i in 0 until stacks) for (j in 0 until slices) {
            val a = p(i, j); val b = p(i + 1, j); val c = p(i + 1, j + 1); val d = p(i, j + 1)
            vtx(b); vtx(d); vtx(c)
            vtx(b); vtx(a); vtx(d)
        }
        return out.toFloatArray()
    }

    /** Unit-height cylinder of diameter 1 along Y. */
    fun cylinder(segments: Int): FloatArray {
        val out = ArrayList<Float>()
        fun vtx(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) { out.addAll(listOf(x, y, z, nx, ny, nz)) }
        for (i in 0 until segments) {
            val a0 = (2 * PI * i / segments).toFloat()
            val a1 = (2 * PI * (i + 1) / segments).toFloat()
            val x0 = cos(a0) * 0.5f; val z0 = sin(a0) * 0.5f
            val x1 = cos(a1) * 0.5f; val z1 = sin(a1) * 0.5f
            // side (counter-clockwise seen from outside)
            vtx(x0, -0.5f, z0, x0 * 2, 0f, z0 * 2); vtx(x1, 0.5f, z1, x1 * 2, 0f, z1 * 2); vtx(x1, -0.5f, z1, x1 * 2, 0f, z1 * 2)
            vtx(x0, -0.5f, z0, x0 * 2, 0f, z0 * 2); vtx(x0, 0.5f, z0, x0 * 2, 0f, z0 * 2); vtx(x1, 0.5f, z1, x1 * 2, 0f, z1 * 2)
            // caps
            vtx(0f, 0.5f, 0f, 0f, 1f, 0f); vtx(x1, 0.5f, z1, 0f, 1f, 0f); vtx(x0, 0.5f, z0, 0f, 1f, 0f)
            vtx(0f, -0.5f, 0f, 0f, -1f, 0f); vtx(x0, -0.5f, z0, 0f, -1f, 0f); vtx(x1, -0.5f, z1, 0f, -1f, 0f)
        }
        return out.toFloatArray()
    }
}
