package com.xnotes.settings

/** How the editor toolbar is drawn; one setting for both the paged and the canvas bar. */
// NextNotes: a floating pen tray down the left is the default, as in a pencil case beside the page.
data class ToolbarLook(
    val position: ToolbarPosition = ToolbarPosition.LEFT,
    val size: ToolbarSize = ToolbarSize.REGULAR,
    /** A rounded pill over the canvas instead of a strip along the pane's edge. */
    val floating: Boolean = true,
)

/** Which edge of its pane the toolbar runs along. */
enum class ToolbarPosition(val id: String) {
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right");

    val vertical: Boolean get() = this == LEFT || this == RIGHT

    companion object {
        fun fromId(id: String): ToolbarPosition = entries.find { it.id == id } ?: LEFT
    }
}

/** How big the toolbar's buttons are. */
enum class ToolbarSize(val id: String) {
    COMPACT("compact"),
    REGULAR("regular"),
    COMFORTABLE("comfortable");

    companion object {
        fun fromId(id: String): ToolbarSize = entries.find { it.id == id } ?: REGULAR
    }
}
