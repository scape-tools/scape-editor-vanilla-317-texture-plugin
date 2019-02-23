package plugin

enum class SpriteEncodingType(val id: Int, val displayName : String) {
    HORIZONTAL(0, "Horizontal"),
    VERTICAL(1, "Vertical");

    override fun toString(): String {
        return displayName
    }

}