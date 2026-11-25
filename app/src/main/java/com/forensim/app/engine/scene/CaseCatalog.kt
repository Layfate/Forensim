package com.forensim.app.engine.scene

import com.forensim.app.core.model.ManifestItem
import com.forensim.app.engine.TrainingPart
import kotlin.random.Random

/**
 * Case types a professor can generate. Each has an evidence pool (required items always appear)
 * and a neutral briefing that describes the call-out without naming the crime, so students have
 * to work out what happened from the evidence. The server mirrors the pools
 * (private.case_evidence_types) and holds the answer key for scoring.
 */
enum class CaseType(val key: String, val label: String, val pool: List<PoolEntry>, val briefing: String) {
    THEFT(
        "theft", "Theft",
        listOf(PoolEntry("latent_print", true), PoolEntry("questioned_document", true), PoolEntry("shoe_print"),
            PoolEntry("hair_fiber"), PoolEntry("tool_mark"), PoolEntry("wallet"), PoolEntry("jewelry_box"), PoolEntry("cigarette_butt"),
            PoolEntry("flashlight"), PoolEntry("handbag")),
        "The occupant came home to find valuables missing and called the police. Officers have secured the room. Process the scene and work out what happened.",
    ),
    BURGLARY(
        "burglary", "Burglary",
        listOf(PoolEntry("tool_mark", true), PoolEntry("broken_glass", true), PoolEntry("shoe_print"),
            PoolEntry("latent_print"), PoolEntry("hair_fiber"), PoolEntry("crowbar"), PoolEntry("screwdriver"), PoolEntry("glove"),
            PoolEntry("torn_fabric"), PoolEntry("bolt_cutters"), PoolEntry("flashlight")),
        "The occupant came home to find valuables missing and called the police. Officers have secured the room. Process the scene and work out what happened.",
    ),
    ASSAULT(
        "assault", "Assault",
        listOf(PoolEntry("bloodstain", true), PoolEntry("blunt_weapon", true), PoolEntry("hair_fiber"),
            PoolEntry("shoe_print"), PoolEntry("latent_print"), PoolEntry("hammer"), PoolEntry("bloody_cloth"), PoolEntry("shoe"),
            PoolEntry("cigarette_butt"), PoolEntry("broken_bottle"), PoolEntry("brass_knuckles")),
        "A person was found injured in this room and taken to hospital. The scene is secured. Process it and work out what happened.",
    ),
    HOMICIDE(
        "homicide", "Homicide",
        listOf(PoolEntry("bloodstain", true), PoolEntry("knife", true), PoolEntry("latent_print"),
            PoolEntry("hair_fiber"), PoolEntry("shoe_print"), PoolEntry("rope"), PoolEntry("bloody_cloth"), PoolEntry("drinking_glass"),
            PoolEntry("cigarette_butt"), PoolEntry("duct_tape"), PoolEntry("zip_ties")),
        "A person was found dead in this room. The scene has been secured for investigators. Process it and work out what happened.",
    ),
    SHOOTING(
        "shooting", "Shooting",
        listOf(PoolEntry("cartridge_case", true), PoolEntry("bullet", true), PoolEntry("firearm"),
            PoolEntry("bloodstain"), PoolEntry("latent_print"), PoolEntry("magazine"), PoolEntry("cell_phone"), PoolEntry("bloody_cloth"),
            PoolEntry("shotgun_shell"), PoolEntry("ammo_box")),
        "A person was found injured in this room and taken to hospital. The scene is secured. Process it and work out what happened.",
    ),
    DRUG_POSSESSION(
        "drug_possession", "Drug possession",
        listOf(PoolEntry("drug_packet", true), PoolEntry("digital_scale", true), PoolEntry("questioned_document"),
            PoolEntry("latent_print"), PoolEntry("hair_fiber"), PoolEntry("syringe"), PoolEntry("pill_bottle"), PoolEntry("cash_bundle"),
            PoolEntry("cell_phone"), PoolEntry("burnt_spoon"), PoolEntry("glass_pipe")),
        "Officers serving a search warrant secured this room after noticing suspicious items. Process the scene and work out what happened.",
    ),
    ROBBERY(
        "robbery", "Robbery",
        // A weapon plus a demand note: taking by threat, unlike theft (no weapon) or burglary (forced entry).
        listOf(PoolEntry("firearm", true), PoolEntry("questioned_document", true), PoolEntry("shoe_print"),
            PoolEntry("latent_print"), PoolEntry("hair_fiber"), PoolEntry("ski_mask"), PoolEntry("cash_bundle"), PoolEntry("glove"),
            PoolEntry("cell_phone"), PoolEntry("duct_tape"), PoolEntry("zip_ties"), PoolEntry("handbag")),
        "Staff reported money missing after a stranger left in a hurry. Officers have secured the room. Process the scene and work out what happened.",
    ),
    VANDALISM(
        "vandalism", "Vandalism",
        // Smashed glass and the object used, with no blood (assault) and no pry marks required (burglary).
        listOf(PoolEntry("broken_glass", true), PoolEntry("blunt_weapon", true), PoolEntry("shoe_print"),
            PoolEntry("latent_print"), PoolEntry("tool_mark"), PoolEntry("spray_can"), PoolEntry("brick"), PoolEntry("hammer"),
            PoolEntry("shoe"), PoolEntry("paint_marker"), PoolEntry("broken_bottle")),
        "Property in this room was found damaged and the owner called the police. Nobody was hurt. The scene is secured. Process it and work out what happened.",
    );

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * A room a case can be staged in. Layouts are bundled so every student builds the same scene.
 * A case stores the [version] it was made with; when a room gains spots (version 2 added the
 * extra spots for 7-item cases), older cases keep building from their original layout.
 * From [randomSince] on, evidence goes anywhere on the floor or the furniture [ScenarioTemplate.surfaces]
 * (picked by the case's seed) instead of on the fixed spots, so no two cases look alike.
 */
data class CaseLocation(
    val id: String,
    val label: String,
    val version: Int,
    val template: ScenarioTemplate,
    /** Spot ids added in each layout version after the first (version -> ids). */
    val addedSpots: Map<Int, Set<String>> = emptyMap(),
    /** First layout version that places evidence at random; null = always on the fixed spots. */
    val randomSince: Int? = null,
) {
    /** The layout a case made with [caseVersion] was generated in (same seed -> same scene). */
    fun templateFor(caseVersion: Int): ScenarioTemplate {
        val random = randomSince != null && caseVersion >= randomSince
        val spots = if (caseVersion >= version) template.spawnPoints
        else template.spawnPoints.filter { p -> addedSpots.none { (v, ids) -> v > caseVersion && p.id in ids } }
        return template.copy(spawnPoints = spots, randomPlacement = random)
    }
}

object CaseLocations {
    private fun sp(id: String, label: String, x: Float, z: Float, y: Float = 0f) =
        SpawnPoint(id, label, x, z, y, if (y == 0f) "floor" else "furniture")

    /**
     * A furniture top evidence may land on: the piece's box, minus [inset] all round and minus
     * [back] (north, -z) / [front] (south, +z) / [left] (west) / [right] (east) where something
     * stands on it, at height [y] (the box top unless the top is recessed, like a pool table).
     */
    private class Top(
        val id: String, val label: String, val inset: Float = 0.05f, val back: Float = 0f, val front: Float = 0f,
        val left: Float = 0f, val right: Float = 0f, val y: Float? = null, val surfaceId: String = id,
    )

    /** The template with random placement on: [tops] become its surfaces. */
    private fun ScenarioTemplate.randomOn(vararg tops: Top): ScenarioTemplate = copy(randomPlacement = true, surfaces = tops.map { t ->
        val f = furniture.first { it.id == t.id }
        TopSurface(t.surfaceId, t.label, f.x - f.w / 2 + t.inset + t.left, f.z - f.d / 2 + t.inset + t.back,
            f.x + f.w / 2 - t.inset - t.right, f.z + f.d / 2 - t.inset - t.front, t.y ?: f.h)
    })

    /** Spots added in version 2 (2026-11) for 7-item cases; version-1 cases never use them. */
    internal val ADDED_IN_V2 = setOf(
        "floor_tv", "floor_chest", "tv_top_left", "tv_top_right", "sofa_top",
        "floor_window", "floor_dining", "floor_corner", "desk_top_2", "dining_top_2", "bed_top_2",
        "floor_east", "floor_meeting_s", "floor_filing", "desk_a_top_2", "desk_b_top_2", "meeting_top_2", "meeting_top_3",
    )

    private fun objectives() = listOf(
        "Sign the scene entry log and put on gloves",
        "Find, mark and photograph every item (with and without scale)",
        "Collect and package each item correctly",
        "Decide what happened and which evidence shows it",
        "Submit your case report",
    )

    /**
     * Spots added in each room's latest layout (2026-11) so a case fits 7 evidence plus up to
     * [CaseFactory.MAX_RED_HERRINGS] red herrings. Appended last, so older layouts are unchanged.
     */
    private val RESIDENCE_MORE = listOf(
        sp("floor_mid_east", "floor between the tables", 4.2f, 5.6f),
        sp("floor_table_e", "floor beside the coffee table", 4.8f, 3.8f),
        sp("floor_west_wall", "floor by the west wall", 0.6f, 4.4f),
        sp("floor_south_east", "floor in the south-east corner", 6.4f, 7.4f),
        sp("floor_east_wall", "floor by the east wall", 6.5f, 5.6f),
        sp("sofa_top_left", "left end of the sofa", 0.75f, 2.2f, 0.8f),
        sp("sofa_top_right", "right end of the sofa", 2.05f, 2.2f, 0.8f),
        sp("table_top_left", "left end of the coffee table", 3.2f, 3.4f, 0.45f),
    )

    // Version 4 (2026-11): random placement. Versions 1-3 keep their fixed spots.
    val RESIDENCE = CaseLocation("residence", "Residence (living room)", 4, ScenarioTemplate(
        evidenceCount = 3,
        objectives = objectives(),
        room = RoomSpec(7f, 8f, 2.8f, "#3A4558", "#4A3F35"),
        door = DoorSpec("south", 3.5f, 1.2f),
        playerStart = PlayerStart(3.5f, 7.2f, 0f),
        sceneLog = Point2(2.4f, 7.6f),
        furniture = listOf(
            FurnitureSpec("sofa", 1.4f, 2.2f, 2.2f, 0.9f, 0.8f, "#56677F", name = "sofa"),
            FurnitureSpec("coffee_table", 3.6f, 3.4f, 1.2f, 0.7f, 0.45f, "#8FA3B8", "glass coffee table", name = "coffee table"),
            FurnitureSpec("bookshelf", 6.6f, 1.0f, 0.6f, 1.6f, 2.0f, "#6B4E3D", name = "bookshelf"),
            FurnitureSpec("side_table", 5.6f, 5.2f, 0.6f, 0.6f, 0.6f, "#6B4E3D", name = "side table"),
            FurnitureSpec("tv_cabinet", 3.6f, 0.4f, 1.8f, 0.5f, 0.6f, "#4A3A30", name = "TV cabinet"),
            FurnitureSpec("drawer_chest", 0.55f, 6.0f, 0.7f, 1.0f, 0.9f, "#7A5A44", name = "drawer chest"),
        ),
        spawnPoints = listOf(
            sp("floor_window", "floor below the window", 4.8f, 1.4f),
            sp("floor_center", "floor near the centre", 2.6f, 4.8f),
            sp("floor_sofa", "floor in front of the sofa", 1.4f, 3.4f),
            sp("floor_side", "floor by the side table", 5.2f, 6.6f),
            sp("floor_shelf", "floor by the bookshelf", 6.1f, 3.2f),
            sp("floor_entry", "floor near the entrance", 1.5f, 7.0f),
            sp("table_top", "glass coffee table", 3.6f, 3.4f, 0.45f),
            sp("side_table_top", "side table", 5.6f, 5.2f, 0.6f),
            sp("tv_top", "top of the TV cabinet", 3.6f, 0.4f, 0.6f),
            sp("chest_top", "top of the drawer chest", 0.55f, 6.0f, 0.9f),
            // Extra spots (2026-11) so cases can hold up to 7 items.
            sp("floor_tv", "floor between the sofa and TV", 2.2f, 1.2f),
            sp("floor_chest", "floor beside the drawer chest", 1.6f, 5.6f),
            sp("tv_top_left", "left end of the TV cabinet", 2.95f, 0.4f, 0.6f),
            sp("tv_top_right", "right end of the TV cabinet", 4.25f, 0.4f, 0.6f),
            sp("sofa_top", "sofa cushion", 1.4f, 2.2f, 0.8f),
        ) + RESIDENCE_MORE,
        evidencePool = emptyList(),
    ).randomOn(
        Top("coffee_table", "glass coffee table"), Top("side_table", "side table"),
        Top("tv_cabinet", "top of the TV cabinet"), Top("drawer_chest", "top of the drawer chest"),
    ), addedSpots = mapOf(2 to ADDED_IN_V2, 3 to RESIDENCE_MORE.ids()), randomSince = 4)

    private val APARTMENT_MORE = listOf(
        sp("floor_dining_s", "floor south of the dining table", 4.5f, 7.0f),
        sp("floor_dining_n", "floor north of the dining table", 4.5f, 4.2f),
        sp("floor_bed_foot", "floor at the foot of the bed", 7.6f, 4.2f),
        sp("floor_south_wall", "floor by the south wall", 5.0f, 8.8f),
        sp("floor_middle", "floor in the middle of the room", 2.6f, 3.8f),
        sp("floor_east_wall", "floor by the east wall", 8.4f, 5.4f),
        sp("bed_top_3", "foot of the bed", 7.4f, 2.7f, 0.6f),
        sp("dining_top_3", "far end of the glass dining table", 5.05f, 5.5f, 0.75f),
        sp("cabinet_top_2", "end of the cabinet", 8.5f, 8.0f, 1.2f),
        sp("desk_top_3", "left side of the desk", 0.7f, 1.0f, 0.75f),
    )

    val APARTMENT = CaseLocation("apartment", "Apartment", 4, ScenarioTemplate(
        evidenceCount = 4,
        objectives = objectives(),
        room = RoomSpec(9f, 10f, 2.8f, "#3B4A52", "#544636"),
        door = DoorSpec("south", 2.0f, 1.2f),
        playerStart = PlayerStart(2.0f, 9.2f, 0f),
        sceneLog = Point2(0.9f, 9.6f),
        furniture = listOf(
            FurnitureSpec("bed", 7.4f, 2.0f, 2.0f, 2.4f, 0.6f, "#4E5D73", name = "bed"),
            FurnitureSpec("desk", 1.2f, 1.0f, 1.6f, 0.8f, 0.75f, "#7A5A44", name = "desk"),
            FurnitureSpec("dining_table", 4.5f, 5.5f, 1.6f, 1.0f, 0.75f, "#9AAFC2", "glass dining table", name = "dining table"),
            FurnitureSpec("cabinet", 8.5f, 7.5f, 0.8f, 1.6f, 1.2f, "#6B4E3D", name = "cabinet"),
            FurnitureSpec("partition", 5.0f, 3.0f, 3.0f, 0.15f, 2.2f, "#3B4A52", name = "partition"),
        ),
        spawnPoints = listOf(
            sp("floor_bedside", "floor beside the bed", 5.8f, 1.4f),
            sp("floor_hall", "hallway floor", 2.2f, 6.8f),
            sp("floor_kitchen", "floor near the cabinet", 7.2f, 8.4f),
            sp("floor_desk", "floor under the desk chair", 2.4f, 2.0f),
            sp("floor_living", "floor by the dining area", 6.8f, 5.0f),
            sp("desk_top", "desk surface", 1.2f, 1.0f, 0.75f),
            sp("dining_top", "glass dining table", 4.5f, 5.5f, 0.75f),
            sp("cabinet_top", "top of the cabinet", 8.5f, 7.5f, 1.2f),
            sp("bed_top", "bedspread", 7.4f, 2.0f, 0.6f),
            sp("floor_window", "floor below the window", 4.4f, 0.8f),
            sp("floor_dining", "floor by the dining chairs", 3.0f, 5.5f),
            sp("floor_corner", "floor in the west corner", 0.8f, 4.0f),
            sp("desk_top_2", "right side of the desk", 1.7f, 1.0f, 0.75f),
            sp("dining_top_2", "end of the glass dining table", 3.95f, 5.5f, 0.75f),
            sp("bed_top_2", "pillow end of the bed", 7.4f, 1.3f, 0.6f),
        ) + APARTMENT_MORE,
        evidencePool = emptyList(),
    ).randomOn(
        Top("desk", "desk surface"), Top("dining_table", "glass dining table"),
        Top("cabinet", "top of the cabinet"), Top("bed", "bedspread", back = 0.12f),
    ), addedSpots = mapOf(2 to ADDED_IN_V2, 3 to APARTMENT_MORE.ids()), randomSince = 4)

    private val OFFICE_MORE = listOf(
        sp("floor_meeting_e", "floor east of the meeting table", 6.0f, 6.0f),
        sp("floor_south_west", "floor in the south-west", 2.0f, 7.4f),
        sp("floor_desk_b_front", "floor in front of desk B", 5.0f, 3.6f),
        sp("floor_door_side", "floor beside the door", 8.6f, 6.4f),
        sp("floor_desk_a_back", "floor behind desk A", 2.0f, 0.8f),
        sp("floor_desk_b_side", "floor beside desk B", 7.4f, 2.6f),
        sp("desk_a_top_3", "left side of desk A", 1.4f, 2.0f, 0.75f),
        sp("desk_b_top_3", "left side of desk B", 4.95f, 2.0f, 0.75f),
        sp("filing_top_2", "back of the filing cabinet", 9.5f, 1.95f, 1.4f),
    )

    val OFFICE = CaseLocation("office", "Office", 4, ScenarioTemplate(
        evidenceCount = 4,
        objectives = objectives(),
        room = RoomSpec(10f, 9f, 3.0f, "#424B5A", "#3F4148"),
        door = DoorSpec("south", 8.0f, 1.2f),
        playerStart = PlayerStart(8.0f, 8.2f, 0f),
        sceneLog = Point2(9.1f, 8.6f),
        furniture = listOf(
            FurnitureSpec("desk_a", 2.0f, 2.0f, 1.8f, 0.9f, 0.75f, "#7A5A44", name = "desk A"),
            FurnitureSpec("desk_b", 5.5f, 2.0f, 1.8f, 0.9f, 0.75f, "#7A5A44", name = "desk B"),
            FurnitureSpec("meeting_table", 4.0f, 6.0f, 2.2f, 1.2f, 0.75f, "#9AAFC2", "glass meeting table", name = "meeting table"),
            FurnitureSpec("filing", 9.5f, 1.5f, 0.8f, 1.2f, 1.4f, "#5D6B7E", name = "filing cabinet"),
            FurnitureSpec("plant", 0.6f, 8.2f, 0.6f, 0.6f, 1.1f, "#3E6B4A", name = "potted plant"),
        ),
        spawnPoints = listOf(
            sp("floor_desk_a", "floor beside desk A", 3.4f, 2.4f),
            sp("floor_desk_b", "floor behind desk B", 5.5f, 0.8f),
            sp("floor_center", "floor in the centre", 6.8f, 4.6f),
            sp("floor_west", "floor by the west wall", 0.8f, 5.0f),
            sp("floor_meeting", "floor by the meeting table", 2.0f, 4.2f),
            sp("desk_a_top", "desk A surface", 2.0f, 2.0f, 0.75f),
            sp("desk_b_top", "desk B surface", 5.5f, 2.0f, 0.75f),
            sp("meeting_top", "glass meeting table", 4.0f, 6.0f, 0.75f),
            sp("filing_top", "top of the filing cabinet", 9.5f, 1.5f, 1.4f),
            sp("floor_east", "floor by the east wall", 8.6f, 4.0f),
            sp("floor_meeting_s", "floor south of the meeting table", 4.0f, 7.4f),
            sp("floor_filing", "floor by the filing cabinet", 8.4f, 1.0f),
            sp("desk_a_top_2", "right side of desk A", 2.6f, 2.0f, 0.75f),
            sp("desk_b_top_2", "right side of desk B", 6.05f, 2.0f, 0.75f),
            sp("meeting_top_2", "west end of the meeting table", 3.35f, 6.0f, 0.75f),
            sp("meeting_top_3", "east end of the meeting table", 4.65f, 6.0f, 0.75f),
        ) + OFFICE_MORE,
        evidencePool = emptyList(),
    ).randomOn(
        // The monitor stands at the back of each desk.
        Top("desk_a", "desk A surface", back = 0.34f), Top("desk_b", "desk B surface", back = 0.34f),
        Top("meeting_table", "glass meeting table"), Top("filing", "top of the filing cabinet"),
    ), addedSpots = mapOf(2 to ADDED_IN_V2, 3 to OFFICE_MORE.ids()), randomSince = 4)

    private val STORE_MORE = listOf(
        sp("floor_back_wall", "floor by the back wall", 4.0f, 1.0f),
        sp("floor_shelf_b_end", "floor at the end of the east shelf", 7.4f, 3.4f),
        sp("floor_south_west", "floor in the south-west corner", 1.0f, 7.0f),
        sp("floor_till", "floor by the till", 6.6f, 7.2f),
        sp("floor_display_e", "floor beside the display table", 4.0f, 6.4f),
        sp("floor_fridge_end", "floor past the drinks fridge", 9.3f, 3.6f),
        sp("counter_top_3", "middle of the counter", 8.0f, 5.8f, 1.0f),
        sp("shelf_b_top_2", "top of the east shelf, far end", 6.3f, 3.0f, 1.4f),
    )

    val STORE = CaseLocation("store", "Convenience store", 3, ScenarioTemplate(
        evidenceCount = 4,
        objectives = objectives(),
        room = RoomSpec(10f, 8f, 3.0f, "#4A5560", "#5C5A52"),
        door = DoorSpec("south", 5.0f, 1.4f),
        playerStart = PlayerStart(5.0f, 7.2f, 0f),
        sceneLog = Point2(3.8f, 7.6f),
        furniture = listOf(
            FurnitureSpec("counter", 8.0f, 5.8f, 0.8f, 2.4f, 1.0f, "#7A5A44", "counter", name = "counter"),
            FurnitureSpec("shelf_a", 2.0f, 3.0f, 3.0f, 0.6f, 1.4f, "#5D6B7E", name = "west shelf"),
            FurnitureSpec("shelf_b", 5.5f, 3.0f, 2.4f, 0.6f, 1.4f, "#5D6B7E", name = "east shelf"),
            FurnitureSpec("fridge", 9.4f, 1.4f, 0.8f, 2.0f, 2.0f, "#C9D3DC", name = "drinks fridge"),
            FurnitureSpec("display", 2.6f, 5.6f, 1.4f, 0.8f, 0.8f, "#9AAFC2", "glass display table", name = "display table"),
        ),
        spawnPoints = listOf(
            sp("floor_aisle_1", "floor in the back aisle", 2.0f, 1.6f),
            sp("floor_aisle_2", "floor between the shelves", 4.0f, 4.2f),
            sp("floor_aisle_3", "floor by the back shelves", 6.0f, 1.6f),
            sp("floor_counter", "floor in front of the counter", 6.8f, 5.8f),
            sp("floor_entry", "floor near the entrance", 2.4f, 7.2f),
            sp("floor_fridge", "floor by the drinks fridge", 8.2f, 1.6f),
            sp("floor_west", "floor by the west wall", 0.8f, 5.0f),
            sp("floor_center", "floor in the middle of the shop", 5.4f, 5.6f),
            sp("counter_top", "counter top", 8.0f, 5.2f, 1.0f),
            sp("counter_top_2", "counter by the till", 8.0f, 6.4f, 1.0f),
            sp("display_top", "glass display table", 2.3f, 5.6f, 0.8f),
            sp("display_top_2", "end of the display table", 2.9f, 5.6f, 0.8f),
            sp("shelf_a_top", "top of the west shelf", 1.4f, 3.0f, 1.4f),
            sp("shelf_a_top_2", "top of the west shelf, middle", 2.6f, 3.0f, 1.4f),
            sp("shelf_b_top", "top of the east shelf", 5.5f, 3.0f, 1.4f),
        ) + STORE_MORE,
        evidencePool = emptyList(),
    ).randomOn(
        Top("counter", "counter top"), Top("display", "glass display table"),
        Top("shelf_a", "top of the west shelf"), Top("shelf_b", "top of the east shelf"),
    ), addedSpots = mapOf(2 to STORE_MORE.ids()), randomSince = 3)

    private val WAREHOUSE_MORE = listOf(
        sp("floor_bench_n", "floor north of the workbench", 6.5f, 3.8f),
        sp("floor_south_west", "floor by the south-west wall", 0.8f, 7.6f),
        sp("floor_aisle", "floor in the middle aisle", 8.0f, 4.2f),
        sp("floor_north_east_corner", "floor in the north-east corner", 11.0f, 1.0f),
        sp("floor_bench_w", "floor west of the workbench", 3.2f, 5.4f),
        sp("floor_south_wall", "floor by the south wall", 7.4f, 9.2f),
        sp("bench_top_3", "middle of the workbench", 6.0f, 6.0f, 0.9f),
        sp("crate_a_top_3", "near corner of the large crate", 1.6f, 1.6f, 1.0f),
    )

    val WAREHOUSE = CaseLocation("warehouse", "Warehouse", 3, ScenarioTemplate(
        evidenceCount = 4,
        objectives = objectives(),
        room = RoomSpec(12f, 10f, 3.6f, "#4F4A44", "#55534E"),
        door = DoorSpec("south", 2.0f, 1.6f),
        playerStart = PlayerStart(2.0f, 9.2f, 0f),
        sceneLog = Point2(3.2f, 9.6f),
        furniture = listOf(
            FurnitureSpec("crates_a", 2.0f, 2.0f, 1.6f, 1.6f, 1.0f, "#8A6A44", "wooden crate", name = "large crate"),
            FurnitureSpec("crates_b", 5.5f, 1.2f, 2.0f, 1.2f, 1.0f, "#8A6A44", "wooden crate", name = "low crates"),
            FurnitureSpec("rack", 10.6f, 4.0f, 0.8f, 4.0f, 2.2f, "#5D6B7E", name = "storage rack"),
            FurnitureSpec("workbench", 6.0f, 6.0f, 2.4f, 0.9f, 0.9f, "#7A5A44", "workbench", name = "workbench"),
            FurnitureSpec("pallet", 9.0f, 8.2f, 1.2f, 1.0f, 0.15f, "#9B7B52", "pallet", name = "pallet"),
        ),
        spawnPoints = listOf(
            sp("floor_center", "floor in the middle of the warehouse", 4.0f, 3.6f),
            sp("floor_north_east", "floor by the north-east wall", 8.4f, 2.0f),
            sp("floor_west", "floor by the west wall", 1.0f, 5.0f),
            sp("floor_entry", "floor near the roller door", 3.4f, 7.6f),
            sp("floor_rack", "floor in front of the rack", 8.8f, 6.0f),
            sp("floor_bench", "floor in front of the workbench", 6.0f, 7.6f),
            sp("floor_crates", "floor between the crates", 3.6f, 0.8f),
            sp("floor_corner", "floor in the south-east corner", 11.0f, 8.0f),
            sp("crate_a_top", "top of the large crate", 2.0f, 2.0f, 1.0f),
            sp("crate_a_top_2", "corner of the large crate", 2.4f, 2.4f, 1.0f),
            sp("crate_b_top", "top of the low crates", 5.0f, 1.2f, 1.0f),
            sp("crate_b_top_2", "far end of the low crates", 6.0f, 1.2f, 1.0f),
            sp("bench_top", "workbench", 5.4f, 6.0f, 0.9f),
            sp("bench_top_2", "end of the workbench", 6.6f, 6.0f, 0.9f),
            sp("pallet_top", "pallet", 9.0f, 8.2f, 0.15f),
        ) + WAREHOUSE_MORE,
        evidencePool = emptyList(),
    ).randomOn(
        Top("crates_a", "top of the large crate", inset = 0.1f), Top("crates_b", "top of the low crates", inset = 0.1f),
        Top("workbench", "workbench"), Top("pallet", "pallet"),
    ), addedSpots = mapOf(2 to WAREHOUSE_MORE.ids()), randomSince = 3)

    // ---------------------------------------------------------------------------- rooms added 2026-11
    // Random placement from their first version, so they have no fixed spots.

    val HOTEL_ROOM = CaseLocation("hotel_room", "Hotel room", 1, ScenarioTemplate(
        objectives = objectives(),
        room = RoomSpec(8f, 7f, 2.7f, "#5B5148", "#4E5A66"),
        door = DoorSpec("south", 6.6f, 1.0f),
        playerStart = PlayerStart(6.6f, 6.2f, 0f),
        sceneLog = Point2(5.5f, 6.6f),
        furniture = listOf(
            FurnitureSpec("hotel_bed", 3.6f, 1.25f, 2.0f, 2.3f, 0.6f, "#E4E1DA", name = "bed"),
            FurnitureSpec("nightstand_l", 2.2f, 0.4f, 0.5f, 0.45f, 0.55f, "#5A3E2B", name = "left bedside table"),
            FurnitureSpec("nightstand_r", 5.0f, 0.4f, 0.5f, 0.45f, 0.55f, "#5A3E2B", name = "right bedside table"),
            FurnitureSpec("writing_desk", 0.35f, 4.2f, 0.6f, 1.3f, 0.75f, "#5A3E2B", name = "writing desk"),
            FurnitureSpec("tv_dresser", 7.7f, 2.4f, 0.55f, 1.6f, 0.8f, "#5A3E2B", name = "dresser"),
            FurnitureSpec("armchair", 6.6f, 4.3f, 0.8f, 0.8f, 0.85f, "#8C6D4F", name = "armchair"),
            FurnitureSpec("service_cart", 4.6f, 4.6f, 0.9f, 0.55f, 0.85f, "#D8D4CC", name = "room-service trolley"),
        ),
        spawnPoints = emptyList(),
        evidencePool = emptyList(),
    ).randomOn(
        Top("hotel_bed", "bed", back = 0.35f), Top("nightstand_l", "left bedside table"), Top("nightstand_r", "right bedside table"),
        Top("writing_desk", "writing desk"), Top("tv_dresser", "top of the dresser"), Top("service_cart", "room-service trolley"),
    ), randomSince = 1)

    val KITCHEN = CaseLocation("kitchen", "Kitchen", 1, ScenarioTemplate(
        objectives = objectives(),
        room = RoomSpec(7f, 6f, 2.7f, "#5E6E60", "#8A8478"),
        door = DoorSpec("south", 1.2f, 1.0f),
        playerStart = PlayerStart(1.2f, 5.3f, 0f),
        sceneLog = Point2(2.3f, 5.6f),
        furniture = listOf(
            FurnitureSpec("kitchen_counter", 3.3f, 0.33f, 4.6f, 0.65f, 0.92f, "#D9D4C7", name = "kitchen counter"),
            FurnitureSpec("kitchen_fridge", 6.45f, 0.4f, 0.8f, 0.7f, 1.85f, "#D5D8DC", name = "fridge"),
            FurnitureSpec("kitchen_island", 3.4f, 2.9f, 1.8f, 0.9f, 0.92f, "#E2DED3", name = "kitchen island"),
            FurnitureSpec("kitchen_table", 5.4f, 4.4f, 1.2f, 0.8f, 0.75f, "#8A6446", name = "kitchen table"),
            FurnitureSpec("kitchen_chair_n", 5.4f, 3.75f, 0.45f, 0.45f, 0.9f, "#8A6446", name = "kitchen chair"),
            FurnitureSpec("kitchen_chair_s", 5.4f, 5.05f, 0.45f, 0.45f, 0.9f, "#8A6446", name = "kitchen chair"),
            FurnitureSpec("pantry", 0.3f, 2.6f, 0.6f, 0.9f, 2.1f, "#E2DED3", name = "pantry cupboard"),
        ),
        spawnPoints = emptyList(),
        evidencePool = emptyList(),
    ).randomOn(
        // The sink (x 2.4..3.0) and the hob (x 4.4..5.0) are set into the counter.
        Top("kitchen_counter", "kitchen counter, left of the sink", right = 3.25f, surfaceId = "counter_left"),
        Top("kitchen_counter", "kitchen counter, by the hob", left = 2.05f, right = 1.25f, surfaceId = "counter_mid"),
        Top("kitchen_island", "kitchen island", front = 0.25f), Top("kitchen_table", "kitchen table"),
    ), randomSince = 1)

    val GARAGE = CaseLocation("garage", "Garage", 1, ScenarioTemplate(
        objectives = objectives(),
        room = RoomSpec(8f, 9f, 3.0f, "#6B6E70", "#6E6C68"),
        door = DoorSpec("south", 1.0f, 1.0f),
        playerStart = PlayerStart(1.0f, 8.2f, 0f),
        sceneLog = Point2(2.1f, 8.6f),
        furniture = listOf(
            FurnitureSpec("car", 4.6f, 4.6f, 1.8f, 4.3f, 1.45f, "#2F4A6E", name = "car"),
            FurnitureSpec("garage_bench", 0.4f, 4.0f, 0.7f, 2.0f, 0.92f, "#8A6A44", name = "workbench"),
            FurnitureSpec("tool_chest", 0.4f, 6.0f, 0.6f, 0.6f, 1.0f, "#B03A2E", name = "tool chest"),
            FurnitureSpec("storage_shelf", 7.65f, 5.0f, 0.5f, 2.2f, 1.9f, "#5D6B7E", name = "storage shelves"),
            FurnitureSpec("tire_stack", 7.5f, 1.0f, 0.7f, 0.7f, 0.85f, "#1E1F22", name = "stack of tyres"),
            FurnitureSpec("chest_freezer", 1.2f, 0.6f, 1.3f, 0.7f, 0.88f, "#E8E8E4", name = "chest freezer"),
        ),
        spawnPoints = emptyList(),
        evidencePool = emptyList(),
    ).randomOn(
        Top("garage_bench", "workbench"), Top("tool_chest", "top of the tool chest"), Top("chest_freezer", "lid of the chest freezer"),
    ), randomSince = 1)

    val BAR = CaseLocation("bar", "Bar", 1, ScenarioTemplate(
        objectives = objectives(),
        room = RoomSpec(11f, 8f, 3.2f, "#3E312A", "#4A3426"),
        door = DoorSpec("south", 9.4f, 1.2f),
        playerStart = PlayerStart(9.4f, 7.2f, 0f),
        sceneLog = Point2(8.2f, 7.6f),
        furniture = listOf(
            FurnitureSpec("bar_counter", 4.5f, 1.6f, 5.0f, 0.7f, 1.1f, "#5A3A24", name = "bar"),
            FurnitureSpec("back_bar", 4.5f, 0.3f, 4.6f, 0.5f, 1.0f, "#3F2A1C", name = "back bar"),
            FurnitureSpec("bar_stools", 4.5f, 2.25f, 3.8f, 0.4f, 0.75f, "#2B2B2E", name = "bar stools"),
            FurnitureSpec("pool_table", 4.0f, 5.0f, 2.5f, 1.4f, 0.82f, "#1F6B45", name = "pool table"),
            FurnitureSpec("pub_table_a", 8.6f, 2.0f, 0.8f, 0.8f, 1.05f, "#5A3A24", name = "high table near the bar"),
            FurnitureSpec("pub_table_b", 8.6f, 4.4f, 0.8f, 0.8f, 1.05f, "#5A3A24", name = "high table by the window"),
            FurnitureSpec("jukebox", 0.5f, 4.5f, 0.6f, 0.9f, 1.55f, "#7A1F2B", name = "jukebox"),
        ),
        spawnPoints = emptyList(),
        evidencePool = emptyList(),
    ).randomOn(
        Top("bar_counter", "bar top"), Top("back_bar", "back bar"),
        Top("pool_table", "pool table felt", inset = 0.14f, y = 0.78f),
        Top("pub_table_a", "high table near the bar", inset = 0.17f), Top("pub_table_b", "high table by the window", inset = 0.17f),
    ), randomSince = 1)

    val CLASSROOM = CaseLocation("classroom", "Classroom", 1, ScenarioTemplate(
        objectives = objectives(),
        room = RoomSpec(10f, 8f, 3.0f, "#7E8C6C", "#9A9488"),
        door = DoorSpec("south", 8.8f, 1.0f),
        playerStart = PlayerStart(8.8f, 7.2f, 0f),
        sceneLog = Point2(7.7f, 7.6f),
        furniture = listOf(
            FurnitureSpec("teacher_desk", 2.2f, 1.6f, 1.5f, 0.75f, 0.76f, "#6B4A32", name = "teacher's desk"),
            FurnitureSpec("student_desks_a", 3.0f, 3.5f, 3.2f, 1.0f, 0.76f, "#B08A5A", name = "front-left desks"),
            FurnitureSpec("student_desks_b", 7.0f, 3.5f, 3.2f, 1.0f, 0.76f, "#B08A5A", name = "front-right desks"),
            FurnitureSpec("student_desks_c", 3.0f, 5.3f, 3.2f, 1.0f, 0.76f, "#B08A5A", name = "back-left desks"),
            FurnitureSpec("student_desks_d", 7.0f, 5.3f, 3.2f, 1.0f, 0.76f, "#B08A5A", name = "back-right desks"),
            FurnitureSpec("class_cabinet", 9.7f, 2.0f, 0.5f, 1.6f, 1.0f, "#6B4A32", name = "supply cabinet"),
        ),
        spawnPoints = emptyList(),
        evidencePool = emptyList(),
    ).randomOn(
        Top("teacher_desk", "teacher's desk"),
        // Desk tops are the north 0.5 m of each row; the chairs are tucked in behind them.
        Top("student_desks_a", "front-left row of desks", front = 0.5f), Top("student_desks_b", "front-right row of desks", front = 0.5f),
        Top("student_desks_c", "back-left row of desks", front = 0.5f), Top("student_desks_d", "back-right row of desks", front = 0.5f),
        Top("class_cabinet", "top of the supply cabinet"),
    ), randomSince = 1)

    private fun List<SpawnPoint>.ids() = map { it.id }.toSet()

    val all = listOf(RESIDENCE, APARTMENT, OFFICE, STORE, WAREHOUSE, HOTEL_ROOM, KITCHEN, GARAGE, BAR, CLASSROOM)

    fun byId(id: String?) = all.firstOrNull { it.id == id }
}

/** A case scene that is guaranteed to generate: seed + evidence list for a location. */
data class CaseDraft(val type: CaseType, val location: CaseLocation, val seed: Int, val manifest: List<ManifestItem>, val victim: Victim? = null)

object CaseFactory {
    const val MIN_ITEMS = 2
    const val MAX_ITEMS = 7
    /** Red herrings a case may add on top of its evidence (mirrors the server limit in professor_create_case). */
    const val MAX_RED_HERRINGS = 5

    /**
     * Picks [count] items (required ones first, the rest at random; past the pool size, supporting
     * items repeat) plus [redHerrings] different everyday objects, orders them so items that
     * fit only one kind of surface are placed first, and tries seeds until the layout generates
     * and every item is reachable (with the [victim]'s body in the room, when there is one). Throws if nothing works (should not happen for the bundled rooms).
     * Item ids (ev_1, ev_2...) follow that order, so they never reveal which items are red herrings.
     */
    fun draft(
        type: CaseType, location: CaseLocation, count: Int, random: Random = Random.Default, victim: Victim? = null,
        redHerrings: Int = 0,
    ): CaseDraft {
        val n = count.coerceIn(MIN_ITEMS, MAX_ITEMS)
        val h = redHerrings.coerceIn(0, MAX_RED_HERRINGS)
        repeat(40) {
            val (required, optional) = type.pool.partition { it.required }
            // Each kind of evidence once; beyond the pool size, extra supporting items repeat
            // (a second fingerprint or shoe print), never a second copy of the key evidence.
            val picks = (required + optional.shuffled(random)).take(n).map { it.type } +
                List((n - type.pool.size).coerceAtLeast(0)) { optional.random(random).type } +
                (if (h > 0) EvidenceType.redHerrings.shuffled(random).take(h).map { it.key } else emptyList())
            // With red herrings, shuffle first so they don't always come last within a surface group.
            val types = (if (h > 0) picks.shuffled(random) else picks).sortedBy { EvidenceType.from(it)?.surfaces?.size ?: 9 }
            val manifest = types.mapIndexed { i, t -> ManifestItem("ev_${i + 1}", t) }
            val seed = random.nextInt(1, Int.MAX_VALUE)
            val ok = runCatching { SceneGenerator.generate(location.template, manifest, seed, victim) }.isSuccess
            if (ok) return CaseDraft(type, location, seed, manifest, victim)
        }
        throw SceneGenerationException("Could not build a ${type.label.lowercase()} scene in the ${location.label.lowercase()}")
    }
}

/** The three required training runs: all in the residence, each with fixed evidence and seed. Parts 1-2 are guided. */
object Training {
    // Training keeps the original room layout so its fixed seeds give the same scenes as before.
    fun template(part: TrainingPart): ScenarioTemplate = CaseLocations.RESIDENCE.templateFor(1).copy(
        briefing = when (part) {
            TrainingPart.BASICS -> "A break-in was reported and patrol has taped off the room. This is your first scene: follow the steps and arrows to document, collect and package each item. Every step tells you why."
            TrainingPart.PACKAGING -> "Blood, hair and broken glass were found after a fight. Each needs a different tool and container: paper bag, plastic bag or evidence box. The guide shows you which one and why."
            TrainingPart.SOLO -> "Now process the scene from start to finish on your own: document, collect each item with the right technique, package it correctly and submit your report."
        },
        objectives = when (part) {
            TrainingPart.BASICS -> listOf("Sign the scene entry log and put on gloves", "Mark, photograph and scale-photograph each item",
                "Collect each item with the right tool", "Package, seal and label each item")
            TrainingPart.PACKAGING -> listOf("Document each item before moving it", "Swab the blood; use forceps for the hair and glass",
                "Choose the right container: paper bag, plastic bag or evidence box", "Seal and label each package")
            TrainingPart.SOLO -> listOf("Document every item first", "Collect with the right tool (print kit, swab, forceps...)",
                "Package each item in the right container", "Submit your case report")
        },
        practiceGuide = true,
    )

    fun manifest(part: TrainingPart): List<ManifestItem> = when (part) {
        TrainingPart.BASICS -> listOf("cartridge_case", "latent_print")
        TrainingPart.PACKAGING -> listOf("bloodstain", "hair_fiber", "broken_glass")
        TrainingPart.SOLO -> listOf("latent_print", "bloodstain", "hair_fiber")
    }.mapIndexed { i, t -> ManifestItem("ev_${i + 1}", t) }

    fun seed(part: TrainingPart) = 2100 + part.number * 101
}
