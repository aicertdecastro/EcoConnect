package ph.edu.batstateu.ecoconnect.guide

import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideParser
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import java.io.File

/** Loads the real bundled guide. Gradle runs unit tests from the module (app/) directory. */
object TestGuide {
    val file: File = File("src/main/assets/${WasteGuideRepository.ASSET_NAME}")
    val guide: WasteGuide by lazy { WasteGuideParser.parse(file.readText()) }
}
