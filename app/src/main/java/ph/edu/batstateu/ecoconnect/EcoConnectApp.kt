package ph.edu.batstateu.ecoconnect

import android.app.Application
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import ph.edu.batstateu.ecoconnect.data.local.EcoConnectDatabase
import ph.edu.batstateu.ecoconnect.data.repository.RequestRepository

/** App-wide dependencies, created lazily (manual DI, no Hilt). */
class EcoConnectApp : Application() {

    val database: EcoConnectDatabase by lazy { EcoConnectDatabase.getInstance(this) }

    val requestRepository: RequestRepository by lazy {
        RequestRepository(
            requestDao = database.collectionRequestDao(),
            receiptDao = database.collectionReceiptDao(),
        )
    }

    /** Read-only waste guide bundled in assets; parsed once and kept in memory. */
    val wasteGuideRepository: WasteGuideRepository by lazy {
        WasteGuideRepository(open = { assets.open(WasteGuideRepository.ASSET_NAME) })
    }
}
