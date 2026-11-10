package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionRequestDao {

    @Query("SELECT * FROM collection_requests ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CollectionRequestEntity>>

    @Query("SELECT * FROM collection_requests WHERE id = :id")
    fun observeById(id: String): Flow<CollectionRequestEntity?>

    /** Requests saved on the phone but not yet sent; used by the future sync worker. */
    @Query("SELECT * FROM collection_requests WHERE syncStatus = 'PENDING_SUBMISSION' ORDER BY createdAt")
    suspend fun getPending(): List<CollectionRequestEntity>

    @Query("SELECT COUNT(*) FROM collection_requests WHERE syncStatus = 'PENDING_SUBMISSION'")
    fun observePendingCount(): Flow<Int>

    /** Returns the row id, or -1 if a request with the same id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(request: CollectionRequestEntity): Long

    @Query(
        """
        UPDATE collection_requests
        SET syncStatus = :status,
            collectorResponse = :collectorResponse,
            proposedDate = :proposedDate,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: String,
        status: SyncStatus,
        collectorResponse: String? = null,
        proposedDate: Long? = null,
        updatedAt: Long = System.currentTimeMillis(),
    )
}
