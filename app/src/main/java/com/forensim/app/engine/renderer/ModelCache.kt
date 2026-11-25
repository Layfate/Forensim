package com.forensim.app.engine.renderer

import android.content.res.AssetManager
import android.util.Log
import com.forensim.app.engine.scene.EvidenceType
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Parsed `.glb` models shared by every 3D view, so re-entering a scene does not read and parse
 * the files again. Each view still uploads its own GPU copy (GL contexts are per view).
 */
object ModelCache {
    private val cache = ConcurrentHashMap<String, Result<GlbModel>>()

    /** The model at [path] under assets/, or null when it is missing or unreadable. */
    fun get(assets: AssetManager?, path: String): GlbModel? {
        assets ?: return null
        return cache.getOrPut(path) {
            try {
                Result.success(GlbModel.parse(assets.open(path).use { it.readBytes() }))
            } catch (e: IOException) {
                Result.failure(e) // not shipped: callers draw primitives instead
            } catch (e: RuntimeException) {
                Log.e("Forensim", "Could not read $path", e)
                Result.failure(e)
            }
        }.getOrNull()
    }
}

/** Where each asset lives under assets/ (see art/README.md for the conventions). */
object ModelPaths {
    fun environment(locationId: String) = "models/environments/$locationId.glb"
    fun furniture(id: String) = "models/furniture/$id.glb"
    fun evidence(type: EvidenceType) = "models/evidence/${type.key}.glb"
    const val SCENE_LOG = "models/furniture/scene_log.glb"
    const val TAPE_POST = "models/furniture/tape_post.glb"
    /** The victim's body, lying as the professor chose (art/scripts/fz_victim.py). */
    fun victim(v: com.forensim.app.engine.scene.Victim) = "models/victim/victim_${v.sex.key}_${v.pose.key}.glb"
}
