package com.forensim.app.engine.interaction

enum class ToolKind { DOCUMENT, COLLECT, PACKAGE }

/** Investigation tools. `key` values are the wire names recorded in simulation events. */
enum class Tool(val key: String, val label: String, val kind: ToolKind, val actionLabel: String) {
    MARKER("marker", "Marker", ToolKind.DOCUMENT, "Place evidence marker"),
    CAMERA("camera", "Camera", ToolKind.DOCUMENT, "Photograph"),
    SCALE("scale", "Scale", ToolKind.DOCUMENT, "Photograph with scale"),
    HAND("hand", "Hand", ToolKind.COLLECT, "Collect by hand"),
    PRINT_KIT("print_kit", "Print kit", ToolKind.COLLECT, "Dust and lift print"),
    SWAB_KIT("swab_kit", "Swab", ToolKind.COLLECT, "Swab the stain"),
    FORCEPS("forceps", "Forceps", ToolKind.COLLECT, "Pick up with forceps"),
    CASTING_KIT("casting_kit", "Casting kit", ToolKind.COLLECT, "Cast or gel-lift the impression"),
    PAPER_BAG("paper_bag", "Paper bag", ToolKind.PACKAGE, "Package in paper bag"),
    PLASTIC_BAG("plastic_bag", "Plastic bag", ToolKind.PACKAGE, "Package in plastic bag"),
    EVIDENCE_BOX("evidence_box", "Evidence box", ToolKind.PACKAGE, "Secure in a rigid evidence box");

    companion object {
        val sceneTools = entries.filter { it.kind != ToolKind.PACKAGE }
        val packaging = entries.filter { it.kind == ToolKind.PACKAGE }
    }
}
