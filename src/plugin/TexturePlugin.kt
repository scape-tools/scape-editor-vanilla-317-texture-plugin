package plugin

import scape.editor.fs.RSArchive
import scape.editor.fs.RSFileStore
import scape.editor.gui.plugin.Plugin
import scape.editor.gui.plugin.extension.TextureExtension

@Plugin(name = "317 Texture Plugin", authors = ["Nshusa"])
class TexturePlugin : TextureExtension() {

    override fun getStoreId(): Int {
        return RSFileStore.ARCHIVE_FILE_STORE
    }

    override fun getFileId(): Int {
        return RSArchive.TEXTURE_ARCHIVE
    }

    override fun applicationIcon(): String {
        return "icons/icon.png"
    }

    override fun fxml(): String {
        return "TextureScene.fxml"
    }

    override fun stylesheets(): Array<String> {
        return arrayOf("css/style.css")
    }
}
