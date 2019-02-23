package plugin

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.text.Text
import javafx.stage.FileChooser
import org.imgscalr.Scalr
import scape.editor.fs.RSArchive
import scape.editor.fs.RSFileStore
import scape.editor.fs.graphics.RSSprite
import scape.editor.gui.App
import scape.editor.gui.Settings
import scape.editor.gui.controller.BaseController
import scape.editor.gui.util.toType
import scape.editor.gui.util.write24Int
import scape.editor.util.HashUtils
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URL
import java.util.*
import javax.imageio.ImageIO

class Controller : BaseController() {

    lateinit var listView: ListView<SpriteModel>
    lateinit var idT: Text
    lateinit var resizeWidthTf: TextField
    lateinit var resizeHeightTf: TextField
    lateinit var colorsT: Text
    lateinit var widthT: Text
    lateinit var heightT: Text
    lateinit var encodingCb: ComboBox<SpriteEncodingType>
    lateinit var searchTf: TextField

    private val items = FXCollections.observableArrayList<SpriteModel>()
    private val encodingOptions = FXCollections.observableArrayList(SpriteEncodingType.HORIZONTAL, SpriteEncodingType.VERTICAL)

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        encodingCb.items = encodingOptions

        listView.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            newValue ?: return@addListener
            updateInfo(newValue.id, newValue.sprite)
        }

        val filteredList = FilteredList(items) { true }
        searchTf.textProperty().addListener { _, _, newValue ->
            filteredList.setPredicate {
                if (newValue == null || newValue.isEmpty()) {
                    return@setPredicate true
                }

                val lowercase = newValue.toLowerCase()

                if (it.toString().toLowerCase().contains(lowercase)) {
                    return@setPredicate true
                }

                return@setPredicate false
            }
        }

        val sortedList = SortedList(filteredList)
        listView.items = sortedList

        listView.setCellFactory { _ ->
            object : ListCell<SpriteModel?>() {
                private val imageView = ImageView()
                public override fun updateItem(model: SpriteModel?, empty: Boolean) {
                    super.updateItem(model, empty)
                    if (empty || model == null) {
                        text = null
                        graphic = null
                    } else {
                        var bimage = model.sprite.toBufferedImage()

                        if (bimage.width > 64 || bimage.height > 64) {
                            bimage = Scalr.resize(bimage, 64, 64)
                        }

                        imageView.image = SwingFXUtils.toFXImage(bimage, null)

                        text = model.toString()
                        graphic = imageView
                    }
                }
            }
        }
    }

    private fun updateInfo(id: Int, sprite: RSSprite) {
        val colors = sprite.pixels.toSet()
        colorsT.text = colors.size.toString()

        idT.text = id.toString()
        resizeWidthTf.text = sprite.resizeWidth.toString()
        resizeHeightTf.text = sprite.resizeHeight.toString()
        widthT.text = sprite.width.toString()
        heightT.text = sprite.height.toString()

        if (sprite.format == 0 || sprite.format == 1) {
            encodingCb.selectionModel.select(sprite.format)
        }
    }

    override fun onPopulate() {
        items.clear()

        val archive = App.fs.getArchive(RSFileStore.ARCHIVE_FILE_STORE, RSArchive.TEXTURE_ARCHIVE) ?: return

        val indexHash = HashUtils.hashName("index.dat")

        val models = mutableListOf<SpriteModel>()

        var count = 0
        var remaining = archive.entries.size - 1

        while (remaining > 0) {
            for (entry in archive.entries) {
                if (entry.hash == indexHash) {
                    continue
                }

                val hash = HashUtils.hashName("$count.dat")

                if (entry.hash == hash) {
                    try {
                        val indexedImage = RSSprite.decode(archive, entry.hash, 0)
                        models.add(SpriteModel(count, indexedImage))
                        remaining--
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            count++
        }

        var temp: SpriteModel?

        for (i in 0 until models.size) {
            for (j in 1 until (models.size - i)) {
                if (models[j - 1].id > models[j].id) {
                    temp = models[j - 1]
                    models[j - 1] = models[j]
                    models[j] = temp
                }
            }
        }

        for (model in models) {
            Settings.putNameForHash("${model.id}.dat")
            items.add(model)
        }

    }

    @FXML
    private fun export() {
        val selectedItem = listView.selectionModel.selectedItem ?: return

        val outputDir = File("./dump/")

        if (!outputDir.exists()) {
            outputDir.mkdir()
        }

        val bimage = selectedItem.sprite.toBufferedImage()
        ImageIO.write(bimage, "png", File(outputDir, "${selectedItem.id}.png"))

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Information"
        alert.headerText = "Would you like to view this file?"
        alert.contentText = "Choose an option."

        val choiceOne = ButtonType("Yes.")
        val close = ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE)

        alert.buttonTypes.setAll(choiceOne, close)

        val result = alert.showAndWait()

        if (result.isPresent) {

            val type = result.get()

            if (type == choiceOne) {
                try {
                    Desktop.getDesktop().open(outputDir)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }

        }

    }

    @FXML
    private fun pack() {
        if (items.isEmpty()) {
            return
        }

        val task = object : Task<Boolean>() {
            override fun call(): Boolean {

                val archive = RSArchive()

                ByteArrayOutputStream().use { ibos ->
                    var idxOffset = 0

                    DataOutputStream(ibos).use { idxOut ->
                        for (currentModel in items) {

                            ByteArrayOutputStream().use { dbos ->

                                DataOutputStream(dbos).use { datOut ->

                                    val images = mutableListOf<BufferedImage>()
                                    val colorSet = mutableListOf<Int>()
                                    colorSet.add(0)

                                    val bimage = currentModel.sprite.toBufferedImage()

                                    for (x in 0 until bimage.width) {
                                        for (y in 0 until bimage.height) {
                                            val argb = bimage.getRGB(x, y)
                                            val rgb = argb and 0xFFFFFF

                                            if (colorSet.contains(rgb)) {
                                                continue
                                            }

                                            colorSet.add(rgb)
                                        }
                                    }

                                    images.add(bimage)

                                    idxOut.writeShort(currentModel.sprite.resizeWidth)
                                    idxOut.writeShort(currentModel.sprite.resizeHeight)
                                    idxOut.writeByte(colorSet.size)

                                    for (i in 1 until colorSet.size) {
                                        idxOut.write24Int(colorSet[i])
                                    }

                                    val sprite = currentModel.sprite
                                    idxOut.writeByte(sprite.offsetX)
                                    idxOut.writeByte(sprite.offsetY)
                                    idxOut.writeShort(sprite.width)
                                    idxOut.writeShort(sprite.height)
                                    idxOut.writeByte(sprite.format)

                                    datOut.writeShort(idxOffset)

                                    idxOffset = idxOut.size()

                                    if (sprite.format === 0) { // horizontal encoding
                                        for (y in 0 until bimage.height) {
                                            for (x in 0 until bimage.width) {
                                                val argb = bimage.getRGB(x, y)

                                                val rgb = argb and 0xFFFFFF

                                                val paletteIndex = colorSet.indexOf(rgb)

                                                assert(paletteIndex != -1)

                                                datOut.writeByte(paletteIndex)
                                            }
                                        }
                                    } else { // vertical encoding
                                        for (x in 0 until bimage.width) {
                                            for (y in 0 until bimage.height) {
                                                val argb = bimage.getRGB(x, y)

                                                val rgb = argb and 0xFFFFFF

                                                val paletteIndex = colorSet.indexOf(rgb)

                                                assert(paletteIndex != -1)

                                                datOut.writeByte(paletteIndex)
                                            }
                                        }
                                    }
                                }

                                archive.writeFile("${currentModel.id}.dat", dbos.toByteArray())

                            }
                        }

                        archive.writeFile("index.dat", ibos.toByteArray())

                    }
                }

                val encoded = archive.encode()

                val store = App.fs.getStore(RSFileStore.ARCHIVE_FILE_STORE)
                store.writeFile(RSArchive.TEXTURE_ARCHIVE, encoded)

                val alert = Alert(Alert.AlertType.INFORMATION)
                alert.title = "Info"
                alert.headerText = "Success!"

                Platform.runLater {
                    alert.showAndWait()
                }

                return true
            }

        }

        task.run()
    }

    @FXML
    private fun replaceTexture() {
        val selectedItem = listView.selectionModel.selectedItem ?: return

        val chooser = FileChooser()
        chooser.initialDirectory = File("./")
        chooser.title = "Select sprites to add"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("Images", "*.png", "*.jpg"))

        val selectedFile = chooser.showOpenDialog(App.mainStage) ?: return

        try {
            var bimage = ImageIO.read(selectedFile) ?: return

            if (bimage.type != BufferedImage.TYPE_INT_RGB) {
                bimage = bimage.toType(BufferedImage.TYPE_INT_RGB)
            }

            val sprite = RSSprite(bimage)

            if (bimage.width < 64 || bimage.width > 128 || bimage.height < 64 || bimage.height > 128) {
                val alert = Alert(Alert.AlertType.WARNING)
                alert.headerText = "Image=${selectedFile.name} width/height must be between 64-128"
                alert.showAndWait()
                return
            }

            val colors = sprite.pixels.toSet()

            if (colors.size > 255) {
                val alert = Alert(Alert.AlertType.WARNING)
                alert.headerText = "Image=${selectedFile.name} exceeds color limit=255 colors=${colors.size}"
                alert.showAndWait()
                return
            }

            items[selectedItem.id].sprite = sprite

            updateInfo(selectedItem.id, sprite)

            listView.refresh()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @FXML
    private fun addTexture() {
        val chooser = FileChooser()
        chooser.initialDirectory = File("./")
        chooser.title = "Select sprites to add"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("Images", "*.png", "*.jpg"))

        val files = chooser.showOpenMultipleDialog(App.mainStage) ?: return

        for (file in files) {
            try {
                var pos = 0
                var found = true
                while (found) {
                    found = false
                    for (item in items) {
                        if (item.id == pos) {
                            pos++
                            found = true
                            break
                        }
                    }
                }

                var bimage = ImageIO.read(file) ?: continue

                if (bimage.type != BufferedImage.TYPE_INT_RGB) {
                    bimage = bimage.toType(BufferedImage.TYPE_INT_RGB)
                }

                val sprite = RSSprite(bimage)

                if (bimage.width < 64 || bimage.width > 128 || bimage.height < 64 || bimage.height > 128) {
                    val alert = Alert(Alert.AlertType.WARNING)
                    alert.headerText = "Image=${file.name} width/height must be between 64-128"
                    alert.showAndWait()
                    return
                }

                val colors = sprite.pixels.toSet()

                if (colors.size > 255) {
                    val alert = Alert(Alert.AlertType.WARNING)
                    alert.headerText = "Image=${file.name} exceeds color limit=255 colors=${colors.size}"
                    alert.showAndWait()
                    break
                }

                if (pos < items.size) {
                    items.add(pos, SpriteModel(pos, sprite))
                } else {
                    items.add(pos, SpriteModel(pos, sprite))
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    @FXML
    private fun removeTexture() {
        val selectedItem = listView.selectionModel.selectedItem ?: return

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.headerText = "Are you sure you want to remove this?"

        val optional = alert.showAndWait()

        if (!optional.isPresent) {
            return
        }

        val result = optional.get()

        if (result != ButtonType.OK) {
            return
        }

        items.remove(selectedItem)
    }

    @FXML
    private fun exportAll() {
        if (items.isEmpty()) {
            return
        }

        val outputDir = File("./dump/")

        if (!outputDir.exists()) {
            outputDir.mkdir()
        }

        for (item in items) {
            val bimage = item.sprite.toBufferedImage()

            ImageIO.write(bimage, "png", File(outputDir, "${item.id}.png"))
        }

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Information"
        alert.headerText = "Would you like to view these files?"
        alert.contentText = "Choose an option."

        val choiceOne = ButtonType("Yes.")
        val close = ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE)

        alert.buttonTypes.setAll(choiceOne, close)

        val result = alert.showAndWait()

        if (result.isPresent) {

            val type = result.get()

            if (type == choiceOne) {
                try {
                    Desktop.getDesktop().open(outputDir)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }

        }

    }

    @FXML
    private fun onAction() {
        val selectedItem = listView.selectionModel.selectedItem ?: return

        var flag = false

        try {
            val value = resizeWidthTf.text.toInt()
            selectedItem.sprite.resizeWidth = value
            flag = true
        } catch (ex: Exception) {

        }

        try {
            val value = resizeHeightTf.text.toInt()
            selectedItem.sprite.resizeHeight = value
            flag = true
        } catch (ex: Exception) {

        }

        if (flag) {
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.title = "Info"
            alert.headerText = "Success!"
            alert.showAndWait()
        }
    }

    override fun onClear() {
        items.clear()
        idT.text = "0"
        colorsT.text = "0"
        resizeWidthTf.text = "0"
        resizeHeightTf.text = "0"
        widthT.text = "0"
        heightT.text = "0"
        encodingCb.selectionModel.select(0)
    }

}