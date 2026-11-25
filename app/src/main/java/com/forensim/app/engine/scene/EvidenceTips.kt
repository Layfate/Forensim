package com.forensim.app.engine.scene

/** Why this item is collected the way it is. Shown in the training parts and the tutorial. */
val EvidenceType.collectTip: String
    get() = when (this) {
        EvidenceType.CARTRIDGE_CASE -> "Pick it up by the rim with gloved fingers, so the marks on its head are not smudged."
        EvidenceType.LATENT_PRINT -> "Dust it with powder, then lift it with tape onto a backing card."
        EvidenceType.BLOODSTAIN -> "Use a sterile swab lightly moistened with distilled water, then let the swab air-dry."
        EvidenceType.QUESTIONED_DOCUMENT -> "Hold it only by the edges and never fold it."
        EvidenceType.HAIR_FIBER -> "Clean forceps stop you adding your own hair or fibres."
        EvidenceType.SHOE_PRINT -> "Photograph it with a scale first, then cast or gel-lift it."
        EvidenceType.TOOL_MARK -> "Take a silicone cast that copies the mark's fine detail."
        EvidenceType.BROKEN_GLASS -> "Forceps protect your hands and the broken edges, which can be matched later."
        EvidenceType.KNIFE -> "Lift it by the edges of the handle, never by the blade or the grip where prints may be."
        EvidenceType.FIREARM -> "Hold it by the textured grips, never near the trigger; in real life it is made safe first."
        EvidenceType.BULLET -> "Rubber-tipped forceps don't scratch the rifling marks used to match the gun."
        EvidenceType.BLUNT_WEAPON -> "Handle it as little as possible: it may carry blood, hair and prints."
        EvidenceType.DRUG_PACKET -> "Never open, smell or taste it."
        EvidenceType.DIGITAL_SCALE -> "Handle it by the edges to keep drug residue and prints on it."
        else -> collectHint ?: "Wear gloves and handle it as little as possible, by surfaces nobody else is likely to have touched."
    }

/** Why this item goes in its container. Shown in the training parts and the tutorial. */
val EvidenceType.packagingTip: String
    get() = when (this) {
        EvidenceType.CARTRIDGE_CASE -> "It is dry and not biological, so a paper or plastic bag both work."
        EvidenceType.LATENT_PRINT -> "The lift card goes in a paper bag or envelope so it isn't bent or smudged."
        EvidenceType.BLOODSTAIN -> "Blood is biological. Paper lets it breathe; plastic traps moisture, so mould grows and destroys the DNA."
        EvidenceType.QUESTIONED_DOCUMENT -> "A paper envelope or clear plastic sleeve keeps it flat and readable."
        EvidenceType.HAIR_FIBER -> "Fold it into a paper packet inside a paper bag. Plastic builds static and moisture."
        EvidenceType.SHOE_PRINT -> "Once dry, the cast goes in a rigid box or a paper bag."
        EvidenceType.TOOL_MARK -> "The cast goes in a rigid evidence box so its fine detail isn't crushed."
        EvidenceType.BROKEN_GLASS -> "Glass cuts through bags and breaks further, so it goes in a rigid evidence box."
        EvidenceType.KNIFE -> "A blade cuts through bags and can injure whoever opens it, so it goes in a rigid knife box."
        EvidenceType.FIREARM -> "A rigid box holds it still and protects prints on it."
        EvidenceType.BULLET -> "A small box or paper keeps it from rubbing against other items."
        EvidenceType.BLUNT_WEAPON -> "After air-drying, paper, so any blood on it does not rot."
        EvidenceType.DRUG_PACKET -> "A sealed plastic evidence bag stops powder escaping or absorbing moisture."
        EvidenceType.DIGITAL_SCALE -> "A plastic bag or box keeps the residue on it."
        else -> packagingHint ?: "Choose a container that keeps it dry and stops it being rubbed or crushed."
    }
