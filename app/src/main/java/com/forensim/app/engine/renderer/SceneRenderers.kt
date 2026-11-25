package com.forensim.app.engine.renderer

import android.content.res.AssetManager
import android.opengl.Matrix
import com.forensim.app.engine.SimulationGame
import com.forensim.app.engine.math.Aabb
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.ObjectKind
import com.forensim.app.engine.scene.Victim
import com.forensim.app.engine.scene.VictimPose
import com.forensim.app.engine.scene.VictimSex
import kotlin.math.cos
import kotlin.math.sin

private val MARKER_YELLOW = floatArrayOf(0.98f, 0.8f, 0.12f)
private val CLIPBOARD = floatArrayOf(0.85f, 0.82f, 0.74f)

/**
 * Renders the playable scene and drives the game loop on the GL thread. With [assets], the room,
 * furniture, props and evidence are drawn from their `.glb` models; collision and interaction
 * still use the layout's boxes, which the models are built to match.
 */
class GameRenderer(private val game: SimulationGame, assets: AssetManager? = null) : GlesRenderer(assets) {
    private var time = 0f

    /**
     * Horizontal field of view in degrees (player setting). The vertical angle is derived from the
     * screen's aspect ratio so the view is equally wide on every phone and never looks squashed.
     */
    @Volatile var horizontalFovDegrees = DEFAULT_HORIZONTAL_FOV
        set(value) {
            if (field != value) { field = value; invalidateProjection() }
        }

    override fun fieldOfView(): Float {
        val half = Math.toRadians(horizontalFovDegrees / 2.0)
        val vertical = Math.toDegrees(2 * kotlin.math.atan(kotlin.math.tan(half) / aspect.coerceAtLeast(0.1f))).toFloat()
        return vertical.coerceIn(40f, 80f)
    }

    /**
     * The player's eye stays at least a body radius (0.28 m) from walls and furniture, and the fog
     * hides everything past ~21 m, so a tighter range doubles depth precision (no z-fighting on
     * rugs, decals and stacked parts).
     */
    override fun clipRange() = 0.1f to 30f

    companion object {
        const val DEFAULT_HORIZONTAL_FOV = 90f
    }
    private val statics = game.layout.objects.filter { it.kind != ObjectKind.EVIDENCE }
    /** Clipboard on top of the scene log, computed once instead of every frame. */
    private val clipboards = statics.filter { it.kind == ObjectKind.SCENE_LOG }.associate { o ->
        val b = o.bounds
        o to Aabb(Vec3(b.min.x + 0.07f, b.max.y, b.min.z + 0.05f), Vec3(b.max.x - 0.07f, b.max.y + 0.02f, b.max.z - 0.05f))
    }

    /** Room shell model, found by matching the layout's room and door to a bundled location. */
    private val environment: String? = game.layout.template.let { t ->
        CaseLocations.all.firstOrNull { it.template.room == t.room && it.template.door == t.door }?.let { ModelPaths.environment(it.id) }
    }

    init {
        val room = game.layout.template.room
        roomLight = Vec3(room.width / 2, room.height - 0.2f, room.depth / 2)
        ambient = 0.30f
    }

    override fun preloadModels(): List<String> = buildList {
        environment?.let(::add)
        statics.filter { it.kind == ObjectKind.FURNITURE }.forEach { add(ModelPaths.furniture(it.id)) }
        add(ModelPaths.SCENE_LOG)
        add(ModelPaths.TAPE_POST)
        game.layout.evidence.map { it.type }.distinct().forEach { add(ModelPaths.evidence(it)) }
        game.layout.victim?.let { add(ModelPaths.victim(it.victim)) }
    }

    override fun update(dt: Float) {
        time += dt
        game.step(dt)
        val eye = game.player.eye
        setCamera(eye, eye + game.player.forward)
    }

    override fun draw() {
        val snap = game.renderSnapshot
        val pulse = 0.35f + 0.25f * sin(time * 6f)
        // The room model replaces the floor, ceiling and wall boxes (but not the block outside the door).
        val roomDrawn = environment != null && drawModel(environment, 0f, 0f, 0f)
        for (o in statics) {
            val b = o.bounds
            when (o.kind) {
                ObjectKind.SCENE_LOG -> {
                    val e = if (snap.focusedId == "scene_log") pulse * 0.6f else 0f
                    if (!drawModel(ModelPaths.SCENE_LOG, b.center.x, b.min.y, b.center.z, e)) {
                        drawBox(b, o.color, e)
                        clipboards[o]?.let { drawBox(it, CLIPBOARD) }
                    }
                }
                ObjectKind.TAPE -> if (!drawModel(ModelPaths.TAPE_POST, b.center.x, 0f, b.center.z)) {
                    drawCylinder(b, o.color)
                    // Weighted base so the post reads as a barrier stand.
                    drawCylinder(Aabb.onSurface(b.center.x, 0f, b.center.z, 0.22f, 0.04f, 0.22f), o.color)
                }
                ObjectKind.VICTIM ->
                    game.layout.victim?.let { v -> drawVictim(v.victim, v.center.x, v.center.z, v.yawDeg, if (snap.focusedId == "victim") pulse * 0.25f else 0f) }
                ObjectKind.FURNITURE -> if (!drawModel(ModelPaths.furniture(o.id), b.center.x, b.min.y, b.center.z)) drawBox(b, o.color)
                ObjectKind.FLOOR, ObjectKind.CEILING -> if (!roomDrawn) drawBox(b, o.color)
                ObjectKind.WALL -> if (!roomDrawn || o.id == "door_block") drawBox(b, o.color)
                else -> drawBox(b, o.color)
            }
        }
        for (t in game.layout.tapes) drawTape(t.a, t.b)
        for ((ev, inScene) in snap.evidence) {
            if (!inScene) continue
            val emissive = if (snap.focusedId == ev.id) pulse else 0.04f
            val b = ev.bounds
            if (!drawModel(ModelPaths.evidence(ev.type), b.center.x, b.min.y, b.center.z, emissive, ev.yawDeg)) drawEvidence(ev.type, b, emissive)
        }
        for (m in snap.markers) {
            drawBox(m.bounds, MARKER_YELLOW, 0.15f)
        }
    }
}

/**
 * Camera-independent 3D evidence viewer (plan section 6: the universal fallback when AR is not
 * available). Orbit with drag, zoom with pinch.
 */
class EvidenceViewerRenderer(private val type: EvidenceType, assets: AssetManager? = null) : GlesRenderer(assets) {
    private val path = ModelPaths.evidence(type)
    private val modelBounds: FloatArray? = ModelCache.get(assets, path)?.bounds

    /** Starting (and reset) distance: far enough to fit the whole item, e.g. the 62 cm bat. */
    val defaultDistance: Float = modelBounds?.let { b ->
        val extent = maxOf(b[3] - b[0], b[5] - b[2])
        (extent * 1.6f).coerceIn(0.35f, 1.3f)
    } ?: 0.6f

    @Volatile var yawDeg = 30f
    @Volatile var pitchDeg = 25f
    @Volatile var distance = defaultDistance
    private val m = FloatArray(16)

    override fun preloadModels() = listOf(path)

    init {
        clearColor = floatArrayOf(0.071f, 0.071f, 0.071f) // #121212
        ambient = 0.45f
        flashlight = 0.8f
        roomLight = Vec3(0.6f, 1.4f, 0.8f)
    }

    override fun fieldOfView() = 45f

    override fun update(dt: Float) {
        val p = Math.toRadians(pitchDeg.toDouble())
        val y = Math.toRadians(yawDeg.toDouble())
        val d = distance
        val eye = Vec3((d * cos(p) * sin(y)).toFloat(), (d * sin(p)).toFloat() + 0.03f, (d * cos(p) * cos(y)).toFloat())
        setCamera(eye, Vec3(0f, 0.03f, 0f))
    }

    override fun draw() {
        // Pedestal
        drawCylinder(Aabb(Vec3(-0.22f, -0.04f, -0.22f), Vec3(0.22f, 0f, 0.22f)), floatArrayOf(0.16f, 0.22f, 0.33f))
        // Scale bar (10 cm, alternating 1 cm blocks) along the front edge for size reference.
        for (i in 0 until 10) {
            val x0 = -0.05f + i * 0.01f
            val c = if (i % 2 == 0) floatArrayOf(0.95f, 0.95f, 0.95f) else floatArrayOf(0.1f, 0.1f, 0.1f)
            drawBox(Aabb(Vec3(x0, 0f, 0.12f), Vec3(x0 + 0.01f, 0.003f, 0.135f)), c)
        }
        val (w, _, dd) = type.size
        if (modelBounds != null) {
            // The detailed model, centred on the pedestal. A latent print sits on a pane of glass.
            if (type == EvidenceType.LATENT_PRINT) {
                drawBox(Aabb.onSurface(0f, 0f, 0f, 0.3f, 0.01f, 0.3f), floatArrayOf(0.55f, 0.65f, 0.75f))
                if (drawModel(path, 0f, 0.01f, 0f, 0.05f)) return
            } else {
                val cx = (modelBounds[0] + modelBounds[3]) / 2
                val cz = (modelBounds[2] + modelBounds[5]) / 2
                if (drawModel(path, -cx, -modelBounds[1], -cz, 0.05f)) return
            }
        }
        when (type) {
            EvidenceType.CARTRIDGE_CASE -> {
                // Case body, rim and primer, enlarged x1 (true scale vs. the bar).
                drawCylinder(Aabb.onSurface(0f, 0f, 0f, 0.024f, 0.024f, 0.09f), type.color, 0.05f, alongZ = true)
                drawCylinder(Aabb.onSurface(0f, -0.001f, 0.046f, 0.027f, 0.026f, 0.004f), floatArrayOf(0.75f, 0.58f, 0.22f), 0.05f, alongZ = true)
                drawCylinder(Aabb.onSurface(0f, 0.0075f, 0.0485f, 0.009f, 0.009f, 0.002f), floatArrayOf(0.7f, 0.7f, 0.72f), 0.1f, alongZ = true)
            }
            EvidenceType.BLOODSTAIN -> {
                drawCylinder(Aabb.onSurface(0f, 0f, 0f, w, 0.004f, dd), type.color)
                drawCylinder(Aabb.onSurface(0.12f, 0f, -0.07f, 0.04f, 0.004f, 0.03f), type.color)
                drawCylinder(Aabb.onSurface(-0.13f, 0f, 0.05f, 0.025f, 0.004f, 0.02f), type.color)
            }
            EvidenceType.LATENT_PRINT -> {
                drawBox(Aabb.onSurface(0f, 0f, 0f, 0.3f, 0.01f, 0.3f), floatArrayOf(0.55f, 0.65f, 0.75f)) // glass
                // Ridge pattern suggestion: concentric loops of thin strips.
                for (i in 0 until 7) {
                    val r = 0.012f + i * 0.007f
                    Matrix.setIdentityM(m, 0)
                    Matrix.translateM(m, 0, 0f, 0.0105f, 0f)
                    Matrix.scaleM(m, 0, r * 2, 0.001f, r * 2.6f)
                    drawCylinderModel(m, if (i % 2 == 0) floatArrayOf(0.2f, 0.22f, 0.26f) else floatArrayOf(0.55f, 0.65f, 0.75f))
                }
            }
            EvidenceType.QUESTIONED_DOCUMENT -> {
                drawBox(Aabb.onSurface(0f, 0f, 0f, w, 0.003f, dd), type.color)
                for (i in 0 until 9) {
                    val z = -0.1f + i * 0.022f
                    val len = if (i == 4) 0.09f else 0.15f
                    drawBox(Aabb(Vec3(-0.08f, 0.003f, z), Vec3(-0.08f + len, 0.0045f, z + 0.004f)), floatArrayOf(0.12f, 0.16f, 0.4f))
                }
            }
            EvidenceType.HAIR_FIBER -> {
                for (i in 0 until 3) {
                    Matrix.setIdentityM(m, 0)
                    Matrix.translateM(m, 0, -0.02f + i * 0.02f, 0.002f, 0f)
                    Matrix.rotateM(m, 0, 10f + i * 25f, 0f, 1f, 0f)
                    Matrix.scaleM(m, 0, 0.002f, 0.002f, 0.18f)
                    drawCubeModel(m, type.color)
                }
            }
            else -> drawEvidence(type, Aabb.onSurface(0f, 0f, 0f, w, type.size.second, dd), 0.05f)
        }
    }
}

/**
 * Slowly turning preview of the victim's body on a patch of floor, for the case builder and the
 * case page. Drag to turn it. [victim] can change while it is shown.
 */
class VictimPreviewRenderer(@Volatile var victim: Victim, assets: AssetManager? = null) : GlesRenderer(assets) {
    @Volatile var yawDeg = 35f
    @Volatile var spin = true
    private var time = 0f

    init {
        clearColor = floatArrayOf(0.071f, 0.071f, 0.071f)
        ambient = 0.42f
        flashlight = 0.55f
        roomLight = Vec3(0.8f, 2.4f, 1.2f)
    }

    override fun fieldOfView() = 38f

    // Only four small models: have them all ready, so changing sex or position never stalls a frame.
    override fun preloadModels() = VictimSex.entries.flatMap { s -> VictimPose.entries.map { p -> ModelPaths.victim(Victim(s, p)) } }

    override fun update(dt: Float) {
        time += dt
        if (spin) yawDeg += dt * 14f
        val y = Math.toRadians(yawDeg.toDouble())
        val p = Math.toRadians(38.0)
        val d = 3.1f
        val eye = Vec3((d * cos(p) * sin(y)).toFloat(), (d * sin(p)).toFloat(), (d * cos(p) * cos(y)).toFloat())
        setCamera(eye, Vec3(0f, 0.05f, 0f))
    }

    override fun draw() {
        drawBox(Aabb(Vec3(-1.25f, -0.04f, -0.75f), Vec3(1.25f, 0f, 0.75f)), floatArrayOf(0.3f, 0.26f, 0.22f))
        drawVictim(victim, 0f, 0f, 0f)
    }
}
