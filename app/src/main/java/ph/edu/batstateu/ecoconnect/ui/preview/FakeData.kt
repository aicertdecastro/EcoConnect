package ph.edu.batstateu.ecoconnect.ui.preview

import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.home.HomeStats
import ph.edu.batstateu.ecoconnect.ui.pickup.CategoryAmount
import ph.edu.batstateu.ecoconnect.ui.pickup.RequestPickupUiState
import ph.edu.batstateu.ecoconnect.ui.pickup.WasteCategory

/**
 * Sample values for @Preview functions ONLY.
 * Do not reference this from runtime code; real data will come from Room/Supabase later.
 */
internal object FakeData {
    const val userName = "Juan"

    val homeStats = HomeStats(
        kgSegregated = 42.5,
        totalRequests = 8,
        pendingRequests = 2,
    )

    // 2026-11-20 00:00 UTC (DatePicker works in UTC millis)
    private const val SAMPLE_DATE_MILLIS = 1_795_132_800_000L

    val pickupState = RequestPickupUiState(
        address = "Blk 4 Lot 12, Mabini St., Brgy. Alangilan, Batangas City",
        dateMillis = SAMPLE_DATE_MILLIS,
        hour = 7,
        minute = 30,
        categories = listOf(
            CategoryAmount(WasteCategory.Biodegradable, 3.5),
            CategoryAmount(WasteCategory.Recyclable, 2.0),
            CategoryAmount(WasteCategory.Residual, 1.2),
            CategoryAmount(WasteCategory.Special, 0.0),
        ),
        status = SyncStatus.PENDING_SUBMISSION,
    )

    const val datePickerInitialMillis = SAMPLE_DATE_MILLIS
}
