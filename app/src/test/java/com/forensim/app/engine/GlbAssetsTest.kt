package com.forensim.app.engine

import com.forensim.app.engine.renderer.GlbModel
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.Victim
import com.forensim.app.engine.scene.VictimPose
import com.forensim.app.engine.scene.VictimSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled `.glb` models parse, and line up with the collision boxes the game uses
 * (art/README.md): furniture and evidence sit on y = 0 with the footprint centred, and each room
 * shell covers its room.
 */
class GlbAssetsTest {
    private val root = listOf(File("src/main/assets/models"), File("app/src/main/assets/models")).first { it.isDirectory }

    private fun load(rel: String) = GlbModel.parse(File(root, rel).readBytes())

    @Test
    fun furnitureMatchesLayoutBoxes() {
        // Pieces whose model is deliberately larger than the box (monitor on the office desks).
        val taller = setOf("desk_a", "desk_b")
        // The apartment partition's box overlaps the bed by 0.1 m; its model stops 0.15 m short of
        // the box's east end so the two never intersect (the box itself is unchanged).
        val shortenedEast = mapOf("partition" to 0.15f)
        for (loc in CaseLocations.all) for (f in loc.template.furniture) {
            val b = load("furniture/${f.id}.glb").bounds
            val msg = "${loc.id}/${f.id}"
            val cut = shortenedEast[f.id] ?: 0f
            assertEquals("$msg west end", -f.w / 2, b[0], 0.01f)
            assertEquals("$msg east end", f.w / 2 - cut, b[3], 0.01f)
            assertEquals("$msg depth", f.d, b[5] - b[2], 0.01f)
            assertEquals("$msg bottom", 0f, b[1], 0.002f)
            if (f.id !in taller) assertEquals("$msg height", f.h, b[4] - b[1], 0.01f)
            assertEquals("$msg centred z", 0f, (b[2] + b[5]) / 2, 0.01f)
        }
    }

    @Test
    fun everyEvidenceTypeHasATexturedOrColouredModel() {
        for (t in EvidenceType.entries) {
            val m = load("evidence/${t.key}.glb")
            assertTrue("${t.key} has geometry", m.primitives.isNotEmpty() && m.primitives.all { it.indices.size % 3 == 0 })
            val b = m.bounds
            assertTrue("${t.key} rests on the surface", b[1] >= -0.001f && b[1] < 0.01f)
            assertTrue("${t.key} is no bigger than 0.7 m", b[3] - b[0] < 0.7f && b[5] - b[2] < 0.7f)
            if (t != EvidenceType.FIREARM) { // lies on its side; see art/README.md
                assertEquals("${t.key} width", t.size.first, b[3] - b[0], 0.01f)
                assertEquals("${t.key} depth", t.size.third, b[5] - b[2], 0.01f)
            }
        }
        // Flat evidence carries its look in an embedded texture.
        for (key in listOf("latent_print", "bloodstain", "shoe_print", "questioned_document", "tool_mark")) {
            val m = load("evidence/$key.glb")
            assertTrue("$key texture", m.images.isNotEmpty() && m.primitives.any { it.material.baseColorImage >= 0 })
        }
    }

    @Test
    fun roomShellsCoverTheirRooms() {
        for (loc in CaseLocations.all) {
            val room = loc.template.room
            val b = load("environments/${loc.id}.glb").bounds
            assertTrue("${loc.id} x", b[0] <= 0f && b[3] >= room.width)
            assertTrue("${loc.id} z", b[2] <= 0f && b[5] >= room.depth)
            assertTrue("${loc.id} height", b[4] >= room.height)
        }
    }

    @Test
    fun victimBodiesFitThePlacersBox() {
        // VictimPlacer's footprint: half length = length / 2 + 0.06, half width = 0.31 + 0.1,
        // head towards +x, centred on the origin, lying on the floor.
        for (sex in VictimSex.entries) for (pose in VictimPose.entries) {
            val v = Victim(sex, pose)
            val key = "victim_${sex.key}_${pose.key}"
            val m = load("victim/$key.glb")
            val b = m.bounds
            assertTrue("$key has a textured mesh", m.images.isNotEmpty() && m.primitives.all { it.material.baseColorImage >= 0 })
            assertTrue("$key is a real model, not a few primitives", m.primitives.sumOf { it.indices.size / 3 } > 5000)
            assertEquals("$key lies on the floor", 0f, b[1], 0.003f)
            assertTrue("$key is no taller than the body box", b[4] <= 0.26f)
            val halfLength = v.length / 2 + 0.06f
            assertTrue("$key head end ${b[3]}", b[3] <= halfLength && b[3] > v.length / 2 - 0.05f)
            assertTrue("$key feet end ${b[0]}", b[0] >= -halfLength && b[0] < -v.length / 2 + 0.1f)
            assertTrue("$key width", b[2] >= -0.41f && b[5] <= 0.41f)
        }
    }
}
