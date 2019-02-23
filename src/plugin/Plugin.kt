package plugin

import scape.editor.gui.plugin.PluginDescriptor
import scape.editor.gui.plugin.extension.IPlugin

@PluginDescriptor(name = "317 Texture Plugin", authors = ["Nshusa"])
class Plugin : IPlugin {

    override fun applicationIcon(): String {
        return "icons/icon.png"
    }

    override fun fxml(): String {
        return "scene.fxml"
    }

    override fun stylesheets(): Array<String> {
        return arrayOf("css/style.css")
    }
}
