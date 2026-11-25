package com.forensim.app.engine.scene

/**
 * Groups for the Evidence lab and the camera/AR viewer, in the order students swipe through them.
 * Every real specimen (not red herrings) belongs to exactly one group; anything added to
 * [EvidenceType] later without a group lands in [OTHER] so it is never hidden.
 */
enum class SpecimenCategory(val label: String, val shortLabel: String, private val keys: List<String>) {
    BALLISTICS("Ballistics", "Ballistics", listOf("cartridge_case", "bullet", "firearm", "magazine", "shotgun_shell", "ammo_box")),
    IMPRESSIONS("Fingerprints & impressions", "Prints", listOf("latent_print", "shoe_print", "tool_mark", "shoe")),
    BIOLOGICAL("Biological & trace", "Biological", listOf("bloodstain", "bloody_cloth", "hair_fiber", "torn_fabric", "cigarette_butt",
        "drinking_glass", "glove", "broken_glass")),
    WEAPONS("Weapons", "Weapons", listOf("knife", "blunt_weapon", "hammer", "brick", "broken_bottle", "brass_knuckles")),
    TOOLS("Tools & restraints", "Tools", listOf("crowbar", "screwdriver", "bolt_cutters", "flashlight", "ski_mask", "rope",
        "duct_tape", "zip_ties", "spray_can", "paint_marker")),
    DRUGS("Drugs", "Drugs", listOf("drug_packet", "digital_scale", "syringe", "pill_bottle", "burnt_spoon", "glass_pipe")),
    DOCUMENTS("Documents & belongings", "Documents", listOf("questioned_document", "cell_phone", "wallet", "jewelry_box", "handbag", "cash_bundle")),
    OTHER("Other", "Other", emptyList());

    /** This group's specimens, in the listed order. */
    val specimens: List<EvidenceType>
        get() = if (this == OTHER) EvidenceType.evidence.filter { t -> entries.none { it != OTHER && t.key in it.keys } }
        else keys.mapNotNull { EvidenceType.from(it) }.filter { !it.redHerring }

    companion object {
        /** Groups that have specimens, in display order. */
        val shown: List<SpecimenCategory> get() = entries.filter { it.specimens.isNotEmpty() }

        /** Every specimen, group after group: the order of the swipe in the camera view. */
        val ordered: List<EvidenceType> get() = shown.flatMap { it.specimens }

        fun of(type: EvidenceType): SpecimenCategory = entries.firstOrNull { it != OTHER && type.key in it.keys } ?: OTHER
    }
}
