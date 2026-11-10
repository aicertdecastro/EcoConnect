package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionRequestDao {

    @Query("SELECT * FROM collection_requests WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<CollectionRequestEntity>>

    @Query("SELECT * FROM collection_requests WHERE id = :id")
    fun observeById(id: String): Flow<CollectionRequestEntity?>

    @Query("SELECT * FROM collection_requests WHERE id = :id")
    suspend fun getById(id: String): CollectionRequestEntity?

    /** Requests saved on the phone but not yet sent to the server. */
    @Query("SELECT * FROM collection_requests WHERE userId = :userId AND syncStatus = 'PENDING_SUBMISSION' ORDER BY createdAt")
    suspend fun getPending(userId: String): List<CollectionRequestEntity>

    @Query("SELECT COUNT(*) FROM collection_requests WHERE userId = :userId AND syncStatus = 'PENDING_SUBMISSION'")
    fun observePendingCount(userId: String): Flow<Int>

    /** Returns the row id, or -1 if a request with the same id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(request: CollectionRequestEntity): Long

    /** Marks an uploaded request as SENT, unless something already moved it past PENDING_SUBMISSION. */
    @Query(
        """
        UPDATE collection_requests SET syncStatus = 'SENT', updatedAt = :updatedAt
        WHERE id = :id AND syncStatus = 'PENDING_SUBMISSION'
        """,
    )
    suspend fun markSent(id: String, updatedAt: Long = System.currentTimeMillis())

    /** Gives requests made before sign-in existed (user "demo-user") to the signed-in user. */
    @Query("UPDATE collection_requests SET userId = :userId WHERE userId = '$LEGACY_DEMO_USER_ID'")
    suspend fun claimDemoRows(userId: String): Int

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

    companion object {
        /** User id used before authentication was added. */
        const val LEGACY_DEMO_USER_ID = "demo-user"
    }
}
