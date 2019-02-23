package plugin

import scape.editor.fs.graphics.RSSprite

class SpriteModel(var id: Int, var sprite: RSSprite) {

    override fun toString(): String {
        return id.toString()
    }

}