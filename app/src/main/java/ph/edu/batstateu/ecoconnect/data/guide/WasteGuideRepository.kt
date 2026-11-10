package ph.edu.batstateu.ecoconnect.data.guide

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Loads the bundled waste guide once and keeps it in memory.
 * [open] supplies the JSON (e.g. `assets.open(...)` in the app, a file in tests).
 */
class WasteGuideRepository(
    private val open: () -> InputStream,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: WasteGuide? = null

    suspend fun guide(): WasteGuide =
        cached ?: mutex.withLock {
            cached ?: withContext(io) {
                open().bufferedReader().use { WasteGuideParser.parse(it.readText()) }
            }.also { cached = it }
        }

    companion object {
        const val ASSET_NAME = "waste_guide_alangilan.json"
    }
}
