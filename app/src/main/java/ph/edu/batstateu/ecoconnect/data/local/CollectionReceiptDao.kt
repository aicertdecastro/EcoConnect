package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionReceiptDao {

    /** Returns the row id, or -1 if the receipt (or a receipt for the same request) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipt: CollectionReceiptEntity): Long

    @Query("SELECT * FROM collection_receipts WHERE uploaded = 0 ORDER BY confirmedAt")
    suspend fun getNotUploaded(): List<CollectionReceiptEntity>

    @Query("SELECT * FROM collection_receipts WHERE uploaded = 0 ORDER BY confirmedAt LIMIT 1")
    suspend fun getOldestNotUploaded(): CollectionReceiptEntity?

    @Query("UPDATE collection_receipts SET uploaded = 1 WHERE receiptId = :receiptId")
    suspend fun markUploaded(receiptId: String)

    /** This phone's receipt for [requestId] (requestId is unique), or null. */
    @Query("SELECT * FROM collection_receipts WHERE requestId = :requestId")
    suspend fun getForRequest(requestId: String): CollectionReceiptEntity?

    @Query("SELECT * FROM collection_receipts WHERE requestId = :requestId")
    fun observeForRequest(requestId: String): Flow<CollectionReceiptEntity?>
}
