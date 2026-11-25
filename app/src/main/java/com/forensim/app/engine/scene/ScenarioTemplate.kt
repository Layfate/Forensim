package com.forensim.app.engine.scene

import com.forensim.app.engine.interaction.Tool
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Public scenario template (simulation_scenarios.template). Contains layout and the evidence
 * pool only; the scoring rubric stays on the server.
 * Coordinates are metres. The room spans x 0..width, z 0..depth; yaw 0 faces -z (north).
 */
@Serializable
data class ScenarioTemplate(
    @SerialName("fixed_seed") val fixedSeed: Int? = null,
    @SerialName("evidence_count") val evidenceCount: Int = 3,
    val briefing: String = "",
    val objectives: List<String> = emptyList(),
    val room: RoomSpec,
    val door: DoorSpec,
    @SerialName("player_start") val playerStart: PlayerStart,
    @SerialName("scene_log") val sceneLog: Point2,
    val furniture: List<FurnitureSpec> = emptyList(),
    @SerialName("spawn_points") val spawnPoints: List<SpawnPoint>,
    @SerialName("evidence_pool") val evidencePool: List<PoolEntry>,
    @SerialName("practice_guide") val practiceGuide: Boolean = false,
    /**
     * When true, evidence is not put on the fixed [spawnPoints]: the seed picks a free spot
     * anywhere on the floor or on one of the [surfaces], and turns the item to a random angle,
     * so every case in the same room looks different (SceneGenerator.placeRandom).
     */
    @SerialName("random_placement") val randomPlacement: Boolean = false,
    /** Flat tops evidence may rest on when [randomPlacement] is on. */
    val surfaces: List<TopSurface> = emptyList(),
)

/**
 * A flat area on top of a piece of furniture (room coordinates, metres): x [x0]..[x1],
 * z [z0]..[z1], at height [y]. [label] is how a student is told where an item is.
 */
@Serializable
data class TopSurface(
    val id: String,
    val label: String,
    val x0: Float,
    val z0: Float,
    val x1: Float,
    val z1: Float,
    val y: Float,
)

@Serializable
data class RoomSpec(
    val width: Float,
    val depth: Float,
    val height: Float = 2.8f,
    @SerialName("wall_color") val wallColor: String = "#3A4558",
    @SerialName("floor_color") val floorColor: String = "#4A3F35",
)

@Serializable
data class DoorSpec(val wall: String = "south", val offset: Float, val width: Float = 1.2f)

@Serializable
data class PlayerStart(val x: Float, val z: Float, val yaw: Float = 0f)

@Serializable
data class Point2(val x: Float, val z: Float)

@Serializable
data class FurnitureSpec(
    val id: String,
    val x: Float,
    val z: Float,
    val w: Float,
    val d: Float,
    val h: Float,
    val color: String = "#6B4E3D",
    @SerialName("top_label") val topLabel: String? = null,
    /** What students call it ("sofa", "pool table"); used for "floor by the ..." labels. */
    val name: String? = null,
) {
    val displayName: String get() = name ?: topLabel ?: id.replace('_', ' ')
}

@Serializable
data class SpawnPoint(
    val id: String,
    val label: String,
    val x: Float,
    val z: Float,
    val y: Float = 0f,
    val surface: String,
)

@Serializable
data class PoolEntry(val type: String, val required: Boolean = false)

private val FLOOR = setOf("floor")
private val FURN = setOf("furniture")
private val ANY = setOf("floor", "furniture")
private fun rgb(hex: Int) = floatArrayOf(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f)

/**
 * Evidence types. Collection/packaging guidance here is public lesson knowledge used only for
 * practice-mode hints; authoritative scoring is server-side.
 *
 * [redHerring] items are everyday objects placed in a scene to mislead. Students process them
 * like anything else (collecting one costs nothing), but citing one as proof in the case report
 * costs points. Nothing on screen marks them out; the server holds the list
 * (submit-simulation/scoring.ts RED_HERRINGS, private.red_herring_types()).
 *
 * Sizes match the `.glb` models in assets/models/evidence (GlbAssetsTest checks this).
 */
enum class EvidenceType(
    val key: String,
    val displayName: String,
    val surfaces: Set<String>,
    val size: Triple<Float, Float, Float>, // w, h, d (enlarged slightly for visibility on phones)
    val color: FloatArray,
    val recommendedCollect: Tool,
    val recommendedPackaging: Set<Tool>,
    val description: String,
    /** Why it is collected this way (types added in 2026-11; older ones live in EvidenceTips.kt). */
    val collectHint: String? = null,
    val packagingHint: String? = null,
    val redHerring: Boolean = false,
) {
    CARTRIDGE_CASE(
        "cartridge_case", "Cartridge case", setOf("floor"), Triple(0.05f, 0.05f, 0.11f),
        floatArrayOf(0.85f, 0.66f, 0.25f), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.PLASTIC_BAG),
        "Brass cartridge case. Its head may carry firing pin, breech face and ejector marks.",
    ),
    LATENT_PRINT(
        "latent_print", "Latent fingerprint", setOf("furniture"), Triple(0.14f, 0.006f, 0.14f),
        floatArrayOf(0.62f, 0.66f, 0.72f), Tool.PRINT_KIT, setOf(Tool.PAPER_BAG),
        "Faint friction ridge impression on a smooth, non-porous surface.",
    ),
    BLOODSTAIN(
        "bloodstain", "Bloodstain", setOf("floor"), Triple(0.34f, 0.006f, 0.26f),
        floatArrayOf(0.42f, 0.05f, 0.06f), Tool.SWAB_KIT, setOf(Tool.PAPER_BAG),
        "Dried stain of suspected blood. Biological material: swab, air-dry, package in paper.",
    ),
    QUESTIONED_DOCUMENT(
        "questioned_document", "Questioned document", setOf("furniture"), Triple(0.21f, 0.008f, 0.29f),
        floatArrayOf(0.93f, 0.91f, 0.84f), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.PLASTIC_BAG),
        "Handwritten note. Handle by the edges; do not fold or write on top of it.",
    ),
    HAIR_FIBER(
        "hair_fiber", "Hair / fibre", setOf("floor", "furniture"), Triple(0.26f, 0.008f, 0.03f),
        floatArrayOf(0.18f, 0.12f, 0.08f), Tool.FORCEPS, setOf(Tool.PAPER_BAG),
        "Loose hair or fibre. Collect with clean forceps and fold into paper.",
    ),
    SHOE_PRINT(
        "shoe_print", "Shoe print", setOf("floor"), Triple(0.13f, 0.006f, 0.31f),
        floatArrayOf(0.36f, 0.31f, 0.26f), Tool.CASTING_KIT, setOf(Tool.PAPER_BAG, Tool.EVIDENCE_BOX),
        "Footwear impression in dust. Photograph with a scale, then gel-lift or cast it.",
    ),
    TOOL_MARK(
        "tool_mark", "Tool mark", setOf("furniture"), Triple(0.12f, 0.012f, 0.05f),
        floatArrayOf(0.88f, 0.80f, 0.64f), Tool.CASTING_KIT, setOf(Tool.EVIDENCE_BOX),
        "Pry mark from a screwdriver or crowbar. Photograph it, then take a silicone cast for comparison.",
    ),
    BROKEN_GLASS(
        "broken_glass", "Broken glass", setOf("floor"), Triple(0.30f, 0.012f, 0.22f),
        floatArrayOf(0.72f, 0.88f, 0.94f), Tool.FORCEPS, setOf(Tool.EVIDENCE_BOX),
        "Glass fragments from a forced window. Pick up with forceps; pack in a rigid box so they don't break further.",
    ),
    KNIFE(
        "knife", "Knife", setOf("floor", "furniture"), Triple(0.05f, 0.025f, 0.30f),
        floatArrayOf(0.80f, 0.82f, 0.86f), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Bladed weapon. Lift by the edges of the handle with gloves and secure it in a rigid knife box, never a bag.",
    ),
    FIREARM(
        "firearm", "Handgun", setOf("floor"), Triple(0.06f, 0.13f, 0.21f),
        floatArrayOf(0.16f, 0.16f, 0.18f), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Handgun. In real practice a qualified officer makes it safe first. Handle by textured grips and box it.",
    ),
    BULLET(
        "bullet", "Fired bullet", setOf("floor", "furniture"), Triple(0.04f, 0.035f, 0.05f),
        floatArrayOf(0.72f, 0.42f, 0.24f), Tool.FORCEPS, setOf(Tool.EVIDENCE_BOX, Tool.PAPER_BAG),
        "Fired projectile. Use rubber-tipped forceps so the rifling marks aren't scratched.",
    ),
    BLUNT_WEAPON(
        "blunt_weapon", "Blunt weapon", setOf("floor"), Triple(0.07f, 0.07f, 0.62f),
        floatArrayOf(0.55f, 0.38f, 0.22f), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Wooden bat that may carry blood, hair and prints. Air-dry and package in paper.",
    ),
    DRUG_PACKET(
        "drug_packet", "Suspected drug packet", setOf("furniture", "floor"), Triple(0.09f, 0.03f, 0.11f),
        floatArrayOf(0.96f, 0.96f, 0.96f), Tool.HAND, setOf(Tool.PLASTIC_BAG),
        "Sealed sachet of white powder. Do not open or taste it; seal it in a plastic evidence bag for the lab.",
    ),
    DIGITAL_SCALE(
        "digital_scale", "Digital scale", setOf("furniture"), Triple(0.15f, 0.03f, 0.15f),
        floatArrayOf(0.26f, 0.27f, 0.30f), Tool.HAND, setOf(Tool.PLASTIC_BAG, Tool.EVIDENCE_BOX),
        "Pocket scale that may carry drug residue and fingerprints.",
    ),

    // ------------------------------------------------------------------ evidence added 2026-11
    CIGARETTE_BUTT(
        "cigarette_butt", "Cigarette butt", ANY, Triple(0.028f, 0.021f, 0.09f), rgb(0xD19A55), Tool.FORCEPS, setOf(Tool.PAPER_BAG),
        "Stubbed-out cigarette. Saliva on the filter can carry the smoker's DNA.",
        "Forceps keep your own DNA off the filter.", "Paper lets any moisture dry out so the DNA does not degrade.",
    ),
    DRINKING_GLASS(
        "drinking_glass", "Drinking glass", FURN, Triple(0.08f, 0.12f, 0.08f), rgb(0xDCEBF0), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Tumbler with a little drink left and a lip mark on the rim. Prints, lip DNA and the drink itself can be tested.",
        "Lift it by the base and the very top edge, away from where fingers held it.", "A rigid box stops it breaking and keeps the outside from being rubbed.",
    ),
    CELL_PHONE(
        "cell_phone", "Mobile phone", ANY, Triple(0.078f, 0.009f, 0.16f), rgb(0x2C2F35), Tool.HAND, setOf(Tool.EVIDENCE_BOX, Tool.PLASTIC_BAG),
        "Phone with a cracked screen. Its calls, messages and location history may matter; its case may carry prints.",
        "Hold it by the edges and don't press any buttons: switching it on or off can change the data.", "A box or bag keeps it from being pressed or scratched; a lab will isolate it from the network.",
    ),
    WALLET(
        "wallet", "Wallet", ANY, Triple(0.13f, 0.02f, 0.09f), rgb(0x5A3A24), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Leather wallet with cards half pulled out and no cash in it.",
        "Handle it by the edges; the cards and leather can hold prints.", "Paper keeps leather from sweating, which would smear prints.",
    ),
    SYRINGE(
        "syringe", "Syringe", ANY, Triple(0.035f, 0.02f, 0.156f), rgb(0xE8F2F6), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Used syringe with liquid still inside and the needle uncapped.",
        "Never recap the needle. Pick it up by the barrel, needle pointing away from you.", "Sharps go in a rigid, puncture-proof container so nobody is injured opening the package.",
    ),
    PILL_BOTTLE(
        "pill_bottle", "Pill bottle", FURN, Triple(0.048f, 0.09f, 0.048f), rgb(0xB8621B), Tool.HAND, setOf(Tool.PLASTIC_BAG),
        "Amber pill bottle with a torn-off label and a few tablets inside.",
        "Keep the cap on and hold it by the cap and base.", "A sealed plastic bag keeps the tablets and any residue together.",
    ),
    CROWBAR(
        "crowbar", "Crowbar", FLOOR, Triple(0.08f, 0.02f, 0.567f), rgb(0xA8322A), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.EVIDENCE_BOX),
        "Red-painted pry bar. Its tip can be matched to pry marks, and paint may have transferred.",
        "Don't touch the tips: paint and wood fibres there can be matched to the damage.", "Wrap or box it so the tips don't rub against anything.",
    ),
    SCREWDRIVER(
        "screwdriver", "Screwdriver", ANY, Triple(0.031f, 0.032f, 0.24f), rgb(0xE0B020), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Flat-head screwdriver. The blade's nicks and wear can be matched to a tool mark.",
        "Lift it by the middle of the handle and keep the blade tip untouched.", "A rigid box stops the tip being damaged or blunted.",
    ),
    GLOVE(
        "glove", "Discarded glove", FLOOR, Triple(0.133f, 0.023f, 0.26f), rgb(0x232326), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Black leather work glove. The inside can hold the wearer's DNA and sweat.",
        "Pick it up by the cuff and don't turn it inside out.", "Paper lets sweat inside dry, so the DNA survives.",
    ),
    SKI_MASK(
        "ski_mask", "Balaclava", ANY, Triple(0.22f, 0.022f, 0.28f), rgb(0x121214), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Black knitted balaclava. Hair, saliva and skin cells collect where it touched the face.",
        "Lift it flat by the edges; don't shake it, or hairs on it are lost.", "Fabric goes in paper so moisture escapes and nothing goes mouldy.",
    ),
    ROPE(
        "rope", "Rope", FLOOR, Triple(0.3f, 0.026f, 0.176f), rgb(0xB49A6A), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Length of rope, loosely coiled, one end frayed. Knots and fibres can be compared.",
        "Never untie or cut through a knot; if it has to be cut, cut away from the knot.", "Paper keeps the fibres dry and stops them shedding into plastic.",
    ),
    MAGAZINE(
        "magazine", "Pistol magazine", ANY, Triple(0.04f, 0.026f, 0.127f), rgb(0x3A3D42), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Pistol magazine with a round still in it. The rounds and the magazine can carry prints.",
        "Lift it by the base plate; the cartridges and lips carry prints.", "A rigid box holds it and its rounds still.",
    ),
    BLOODY_CLOTH(
        "bloody_cloth", "Bloodstained towel", FLOOR, Triple(0.3f, 0.024f, 0.248f), rgb(0xD8D4CC), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Crumpled towel soaked with what looks like blood.",
        "Lift it by a clean corner; fold it inward on itself only if it must be folded, never stain to stain.", "Blood-soaked fabric is air-dried and goes in paper, never plastic, or the DNA rots.",
    ),
    SHOE(
        "shoe", "Shoe", FLOOR, Triple(0.091f, 0.12f, 0.283f), rgb(0x3B4250), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.EVIDENCE_BOX),
        "A single sneaker. Its sole can be compared with shoe prints at the scene.",
        "Hold it by the heel collar; the sole pattern and anything stuck in it must stay intact.", "Paper or a box; keep the sole from rubbing against anything.",
    ),
    JEWELRY_BOX(
        "jewelry_box", "Jewellery box", FURN, Triple(0.134f, 0.079f, 0.14f), rgb(0x5B2E22), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Wooden jewellery box, lid forced open, a chain hanging out.",
        "Lift it by the bottom corners: whoever opened it touched the lid and clasp.", "A rigid box keeps the glossy lid from being rubbed.",
    ),
    TORN_FABRIC(
        "torn_fabric", "Torn fabric", ANY, Triple(0.08f, 0.007f, 0.06f), rgb(0x2A3A62), Tool.FORCEPS, setOf(Tool.PAPER_BAG),
        "Scrap of denim torn from clothing, frayed on one side.",
        "Forceps keep your own fibres off it.", "Fold it into a paper packet so no threads are lost.",
    ),
    CASH_BUNDLE(
        "cash_bundle", "Cash bundle", ANY, Triple(0.168f, 0.023f, 0.08f), rgb(0xC9D6C4), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Bundle of banknotes with a paper band, a few loose notes on top.",
        "Handle the notes by their edges; paper takes prints well.", "Paper envelopes; the notes are counted and recorded at the lab.",
    ),
    SPRAY_CAN(
        "spray_can", "Spray paint can", FLOOR, Triple(0.064f, 0.065f, 0.189f), rgb(0xD23A2B), Tool.HAND, setOf(Tool.PLASTIC_BAG),
        "Red spray paint can with a drip down its side.",
        "Hold it by the base; the nozzle and can are where the user's prints are.", "A sealed plastic bag stops paint smearing onto other items.",
    ),
    HAMMER(
        "hammer", "Hammer", ANY, Triple(0.13f, 0.026f, 0.226f), rgb(0xA87A4C), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Claw hammer with a dark stain on its face.",
        "Lift it by the middle of the handle, away from the stained face.", "Air-dry, then paper, so any blood on it does not rot.",
    ),
    BRICK(
        "brick", "Brick", FLOOR, Triple(0.099f, 0.068f, 0.2f), rgb(0x9A4A32), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.EVIDENCE_BOX),
        "Chipped brick with old mortar on it, lying away from any wall.",
        "Lift it by its ends; glass fragments or paint may be stuck to it.", "Paper or a box, so anything stuck to it stays on.",
    ),

    // ------------------------------------------------------------------ evidence added 2026-11 (second set)
    DUCT_TAPE(
        "duct_tape", "Duct tape", ANY, Triple(0.096f, 0.046f, 0.13f), rgb(0xA9AEB4), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Roll of silver duct tape with a torn end hanging loose. The torn edge can be matched to tape used on a victim, and the sticky side holds hair, fibres and prints.",
        "Hold the roll by its outer edges and never touch the sticky side.", "Paper; a loose end is stuck to a plastic sheet first so it can't grab the bag.",
    ),
    ZIP_TIES(
        "zip_ties", "Zip ties", ANY, Triple(0.119f, 0.005f, 0.2f), rgb(0x1E1F22), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Black cable ties, one pulled into a loop and cut open. Used to tie someone's wrists; the cut can be matched to the cutting tool.",
        "Pick them up by the tails, away from the cut and the locking head.", "Paper, keeping the cut loop whole and the ends uncut.",
    ),
    BOLT_CUTTERS(
        "bolt_cutters", "Bolt cutters", FLOOR, Triple(0.088f, 0.025f, 0.6f), rgb(0xC0392B), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.EVIDENCE_BOX),
        "Long-handled bolt cutters. Their jaws leave marks that can be matched to a cut padlock or chain.",
        "Lift it by the middle of the handles and keep the jaws untouched.", "Cover the jaws, then a box or paper so the cutting edges aren't damaged.",
    ),
    FLASHLIGHT(
        "flashlight", "Torch", ANY, Triple(0.04f, 0.04f, 0.199f), rgb(0x24262A), Tool.HAND, setOf(Tool.EVIDENCE_BOX, Tool.PAPER_BAG),
        "Small black torch, switched off. Whoever loaded its batteries may have left prints on them inside.",
        "Hold it by the lens ring and tail cap; don't open it or switch it on here.", "A box or paper; the lab opens it and prints the batteries.",
    ),
    HANDBAG(
        "handbag", "Handbag", ANY, Triple(0.296f, 0.24f, 0.131f), rgb(0x7A2E2A), Tool.HAND, setOf(Tool.PAPER_BAG),
        "Handbag left open and emptied, its zip pulled right back.",
        "Lift it by the bottom corners, not the handles or zip the thief held.", "A large paper bag; leather can sweat in plastic and smear prints.",
    ),
    BROKEN_BOTTLE(
        "broken_bottle", "Broken bottle", ANY, Triple(0.075f, 0.075f, 0.239f), rgb(0x2F6B3A), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Green glass bottle snapped off at the base, with a stained jagged edge. It may have been used as a weapon.",
        "Hold it by the very tip of the neck, jagged end pointing away from you.", "A rigid box so the jagged edge can't cut through or break further.",
    ),
    BRASS_KNUCKLES(
        "brass_knuckles", "Knuckle duster", ANY, Triple(0.12f, 0.013f, 0.064f), rgb(0xB8913A), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.EVIDENCE_BOX),
        "Metal knuckle duster with a dark stain along the striking edge.",
        "Pick it up by its ends, not the finger holes or the striking edge.", "Air-dry, then paper or a box, never plastic, so any blood survives.",
    ),
    SHOTGUN_SHELL(
        "shotgun_shell", "Shotgun shell", FLOOR, Triple(0.024f, 0.024f, 0.069f), rgb(0xB0282A), Tool.HAND, setOf(Tool.PAPER_BAG, Tool.EVIDENCE_BOX),
        "Fired red shotgun shell. Its brass head carries firing-pin and extractor marks.",
        "Pick it up by the plastic hull, not the brass head where the marks are.", "Paper or a box, one shell per package.",
    ),
    AMMO_BOX(
        "ammo_box", "Ammunition box", FURN, Triple(0.1f, 0.039f, 0.19f), rgb(0x3D5A3A), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Open box of pistol rounds with several missing. Their calibre and brand can be compared with cases at the scene.",
        "Lift it by the bottom edges; the lid and rounds hold prints.", "A rigid box, kept level so no rounds spill.",
    ),
    BURNT_SPOON(
        "burnt_spoon", "Burnt spoon", ANY, Triple(0.04f, 0.009f, 0.147f), rgb(0x9A9DA2), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Spoon with a blackened bowl and brown residue in it, used to heat drugs.",
        "Keep it level and hold it by the end of the handle so no residue falls out.", "A rigid box, bowl up, so the residue reaches the lab.",
    ),
    GLASS_PIPE(
        "glass_pipe", "Glass pipe", ANY, Triple(0.035f, 0.035f, 0.12f), rgb(0xBFD9E0), Tool.HAND, setOf(Tool.EVIDENCE_BOX),
        "Glass smoking pipe with burnt residue in the bowl.",
        "Hold it by the stem; it breaks easily and the residue is what the lab tests.", "A padded rigid box so it can't shatter.",
    ),
    PAINT_MARKER(
        "paint_marker", "Paint marker", ANY, Triple(0.023f, 0.023f, 0.14f), rgb(0x2B6CB0), Tool.HAND, setOf(Tool.PLASTIC_BAG),
        "Thick paint marker with its cap off and fresh paint on the tip.",
        "Hold it by the ends of the barrel; don't touch the wet tip.", "A sealed plastic bag stops the paint transferring to anything else.",
    ),

    // ------------------------------------------------------------------ red herrings (2026-11)
    COFFEE_MUG("coffee_mug", "Coffee mug", FURN, Triple(0.116f, 0.1f, 0.087f), rgb(0xE8E4DA), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Ceramic mug with cold coffee at the bottom.", redHerring = true),
    TV_REMOTE("tv_remote", "TV remote", ANY, Triple(0.048f, 0.019f, 0.18f), rgb(0x1E1F23), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Television remote control.", redHerring = true),
    HOUSE_KEYS("house_keys", "Bunch of keys", ANY, Triple(0.1f, 0.007f, 0.088f), rgb(0xC9A24A), Tool.HAND, setOf(Tool.PAPER_BAG), "Three keys on a ring with a red fob.", redHerring = true),
    EYEGLASSES("eyeglasses", "Glasses", FURN, Triple(0.119f, 0.018f, 0.05f), rgb(0x1A1A1C), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Pair of reading glasses, folded.", redHerring = true),
    NEWSPAPER("newspaper", "Newspaper", ANY, Triple(0.28f, 0.015f, 0.2f), rgb(0xE4E1D8), Tool.HAND, setOf(Tool.PAPER_BAG), "Folded local newspaper.", redHerring = true),
    SODA_CAN("soda_can", "Soda can", FLOOR, Triple(0.066f, 0.066f, 0.121f), rgb(0xB5121B), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Dented, empty soft-drink can.", redHerring = true),
    WATER_BOTTLE("water_bottle", "Water bottle", FLOOR, Triple(0.07f, 0.07f, 0.22f), rgb(0x9FD3EA), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Half-full plastic water bottle.", redHerring = true),
    RECEIPT("receipt", "Shop receipt", ANY, Triple(0.08f, 0.009f, 0.15f), rgb(0xF4F2EC), Tool.FORCEPS, setOf(Tool.PAPER_BAG), "Curled till receipt from a convenience store.", redHerring = true),
    PEN("pen", "Ballpoint pen", ANY, Triple(0.009f, 0.011f, 0.14f), rgb(0x2F4F8F), Tool.HAND, setOf(Tool.PAPER_BAG), "Blue ballpoint pen.", redHerring = true),
    BOOK("book", "Book", ANY, Triple(0.16f, 0.027f, 0.211f), rgb(0x2E4A3A), Tool.HAND, setOf(Tool.PAPER_BAG), "Hardback book with a green cover.", redHerring = true),
    UMBRELLA("umbrella", "Umbrella", FLOOR, Triple(0.09f, 0.052f, 0.6f), rgb(0x1E2A3E), Tool.HAND, setOf(Tool.PAPER_BAG), "Folded umbrella with a wooden handle.", redHerring = true),
    TOY_CAR("toy_car", "Toy car", FLOOR, Triple(0.075f, 0.05f, 0.115f), rgb(0xD2332B), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Small red die-cast toy car.", redHerring = true),
    HAIRBRUSH("hairbrush", "Hairbrush", ANY, Triple(0.065f, 0.031f, 0.208f), rgb(0x8A4FA0), Tool.HAND, setOf(Tool.PAPER_BAG), "Purple paddle hairbrush.", redHerring = true),
    LIPSTICK("lipstick", "Lipstick", FURN, Triple(0.049f, 0.022f, 0.068f), rgb(0xB01E3A), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Open lipstick with its cap beside it.", redHerring = true),
    COINS("coins", "Loose coins", ANY, Triple(0.08f, 0.01f, 0.061f), rgb(0xC9A24A), Tool.HAND, setOf(Tool.PAPER_BAG), "A few loose coins.", redHerring = true),
    HEADPHONES("headphones", "Headphones", FURN, Triple(0.18f, 0.04f, 0.117f), rgb(0x1B1C20), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Over-ear headphones.", redHerring = true),
    TISSUE_BOX("tissue_box", "Tissue box", FURN, Triple(0.217f, 0.13f, 0.119f), rgb(0x6FA8C8), Tool.HAND, setOf(Tool.PAPER_BAG), "Box of tissues, one pulled up.", redHerring = true),
    CANDLE("candle", "Candle", FURN, Triple(0.09f, 0.101f, 0.09f), rgb(0xEFE6D2), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Half-burned pillar candle on a dish.", redHerring = true),
    SOCK("sock", "Sock", FLOOR, Triple(0.104f, 0.021f, 0.25f), rgb(0x5B6B7F), Tool.HAND, setOf(Tool.PAPER_BAG), "A single grey sock.", redHerring = true),
    CAP("cap", "Baseball cap", ANY, Triple(0.186f, 0.104f, 0.28f), rgb(0x2E4E7A), Tool.HAND, setOf(Tool.PAPER_BAG), "Navy baseball cap.", redHerring = true),
    MATCHBOX("matchbox", "Matchbox", FURN, Triple(0.05f, 0.015f, 0.099f), rgb(0xC0392B), Tool.HAND, setOf(Tool.PAPER_BAG), "Half-open box of matches.", redHerring = true),
    PHONE_CHARGER("phone_charger", "Phone charger", ANY, Triple(0.13f, 0.025f, 0.143f), rgb(0xEDEDEA), Tool.HAND, setOf(Tool.PLASTIC_BAG), "White phone charger with a coiled cable.", redHerring = true),
    PLAYING_CARDS("playing_cards", "Playing cards", FURN, Triple(0.1f, 0.013f, 0.116f), rgb(0xA8262A), Tool.HAND, setOf(Tool.PAPER_BAG), "Deck of cards with two cards set apart.", redHerring = true),
    FOOD_WRAPPER("food_wrapper", "Crisp packet", FLOOR, Triple(0.119f, 0.017f, 0.16f), rgb(0xE3A22A), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Empty, crumpled crisp packet.", redHerring = true),
    APPLE("apple", "Apple", FURN, Triple(0.078f, 0.085f, 0.078f), rgb(0xB5222A), Tool.HAND, setOf(Tool.PAPER_BAG), "A red apple.", redHerring = true),
    ASHTRAY("ashtray", "Ashtray", FURN, Triple(0.12f, 0.028f, 0.12f), rgb(0x8FA8B0), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Glass ashtray with old ash in it.", redHerring = true),
    PAPER_CUP("paper_cup", "Takeaway cup", FURN, Triple(0.088f, 0.13f, 0.088f), rgb(0xF2EFE8), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Takeaway coffee cup with a lid.", redHerring = true),
    SCISSORS("scissors", "Scissors", ANY, Triple(0.07f, 0.009f, 0.158f), rgb(0x2F7A4A), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Pair of green-handled scissors.", redHerring = true),
    NOTEBOOK("notebook", "Notebook", FURN, Triple(0.157f, 0.014f, 0.21f), rgb(0xD35400), Tool.HAND, setOf(Tool.PAPER_BAG), "Orange spiral notebook, closed.", redHerring = true),
    WATCH("watch", "Wristwatch", FURN, Triple(0.049f, 0.013f, 0.22f), rgb(0x3B2A1E), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Wristwatch with a leather strap.", redHerring = true),
    STAPLER("stapler", "Stapler", FURN, Triple(0.045f, 0.048f, 0.169f), rgb(0x2E2F33), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Black desk stapler.", redHerring = true),
    CALCULATOR("calculator", "Calculator", FURN, Triple(0.085f, 0.015f, 0.15f), rgb(0x3A3D44), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Pocket calculator.", redHerring = true),
    LIGHTER("lighter", "Lighter", ANY, Triple(0.023f, 0.012f, 0.073f), rgb(0x2E86C1), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Blue disposable lighter.", redHerring = true),
    HAND_SANITIZER("hand_sanitizer", "Hand sanitiser", FURN, Triple(0.058f, 0.152f, 0.06f), rgb(0xA8D8E8), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Pump bottle of hand sanitiser gel.", redHerring = true),
    GAME_CONTROLLER("game_controller", "Game controller", ANY, Triple(0.155f, 0.038f, 0.093f), rgb(0x1E1F23), Tool.HAND, setOf(Tool.PLASTIC_BAG), "Wireless game controller.", redHerring = true),
    STICKY_NOTES("sticky_notes", "Sticky notes", FURN, Triple(0.073f, 0.019f, 0.076f), rgb(0xF4D03F), Tool.HAND, setOf(Tool.PAPER_BAG), "Pad of blank yellow sticky notes.", redHerring = true),
    TAPE_MEASURE("tape_measure", "Tape measure", ANY, Triple(0.065f, 0.033f, 0.075f), rgb(0xF1C40F), Tool.HAND, setOf(Tool.EVIDENCE_BOX), "Yellow tape measure.", redHerring = true),
    FLIP_FLOP("flip_flop", "Slipper", FLOOR, Triple(0.094f, 0.03f, 0.259f), rgb(0x2E86C1), Tool.HAND, setOf(Tool.PAPER_BAG), "A single rubber slipper, worn at the heel.", redHerring = true);

    companion object {
        fun from(key: String): EvidenceType? = entries.firstOrNull { it.key == key }

        /** Real evidence types (not red herrings). */
        val evidence: List<EvidenceType> get() = entries.filter { !it.redHerring }
        val redHerrings: List<EvidenceType> get() = entries.filter { it.redHerring }
    }
}
