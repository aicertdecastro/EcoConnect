package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Proof that a request was collected. At most one receipt per request. */
@Entity(
    tableName = "collection_receipts",
    foreignKeys = [
        ForeignKey(
            entity = CollectionRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["requestId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["requestId"], unique = true)],
)
data class CollectionReceiptEntity(
    @PrimaryKey val receiptId: String,
    val requestId: String,
    val userId: String,
    val collectorId: String,
    val confirmedAt: Long,
    /** False until the receipt has been sent to the server. */
    val uploaded: Boolean = false,
)
