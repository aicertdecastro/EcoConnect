package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collection_requests",
    indices = [Index("userId"), Index("syncStatus")],
)
data class CollectionRequestEntity(
    /** UUID generated on the phone so the request has a stable id before it reaches the server. */
    @PrimaryKey val id: String,
    val userId: String,
    /** Saved as a JSON array (see [Converters]). */
    val wasteItems: List<WasteItem>,
    val address: String,
    /** UTC-midnight epoch millis, as returned by the Material 3 DatePicker. */
    val pickupDate: Long,
    /** 24-hour "HH:mm". */
    val pickupTime: String,
    val notes: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING_SUBMISSION,
    val collectorResponse: String? = null,
    /** New date proposed by the collector when [syncStatus] is RESCHEDULED. */
    val proposedDate: Long? = null,
    /** Collector who responded (null while unassigned). */
    val collectorId: String? = null,
    /** Display names from `profiles`, filled when rows come from the server. */
    val collectorName: String? = null,
    val requesterName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** When the upload to Supabase succeeded (local only; used for the R2 sync-delay metric). */
    val sentAt: Long? = null,
)

/** Not stored; computed from [CollectionRequestEntity.wasteItems]. */
val CollectionRequestEntity.totalKg: Double get() = wasteItems.totalKg
