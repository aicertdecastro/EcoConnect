package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CollectionReceiptDao {

    /** Returns the row id, or -1 if the receipt (or a receipt for the same request) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipt: CollectionReceiptEntity): Long

    @Query("SELECT * FROM collection_receipts WHERE uploaded = 0 ORDER BY confirmedAt")
    suspend fun getNotUploaded(): List<CollectionReceiptEntity>

    @Query("UPDATE collection_receipts SET uploaded = 1 WHERE receiptId = :receiptId")
    suspend fun markUploaded(receiptId: String)
}
