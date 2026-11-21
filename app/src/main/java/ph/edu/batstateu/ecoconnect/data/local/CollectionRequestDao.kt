package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
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

    /** Oldest request still waiting to be uploaded, or null when nothing is pending. */
    @Query("SELECT * FROM collection_requests WHERE userId = :userId AND syncStatus = 'PENDING_SUBMISSION' ORDER BY createdAt LIMIT 1")
    suspend fun getOldestPending(userId: String): CollectionRequestEntity?

    /**
     * Marks an uploaded request as SENT and records [sentAt], unless something already
     * moved it past PENDING_SUBMISSION.
     */
    @Query(
        """
        UPDATE collection_requests SET syncStatus = 'SENT', sentAt = :sentAt, updatedAt = :sentAt
        WHERE id = :id AND syncStatus = 'PENDING_SUBMISSION'
        """,
    )
    suspend fun markSent(id: String, sentAt: Long)

    /** Gives requests made before sign-in existed (user "demo-user") to the signed-in user. */
    @Query("UPDATE collection_requests SET userId = :userId WHERE userId = '$LEGACY_DEMO_USER_ID'")
    suspend fun claimDemoRows(userId: String): Int

    /** Applies a server row to an already-sent local row; keeps the local sentAt. */
    @Query(
        """
        UPDATE collection_requests
        SET syncStatus = :status,
            collectorResponse = :collectorResponse,
            proposedDate = :proposedDate,
            collectorId = :collectorId,
            collectorName = :collectorName,
            requesterName = :requesterName,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateFromServer(
        id: String,
        status: SyncStatus,
        collectorResponse: String?,
        proposedDate: Long?,
        collectorId: String?,
        collectorName: String?,
        requesterName: String?,
        updatedAt: Long,
    )

    suspend fun updateFromServer(server: CollectionRequestEntity) = updateFromServer(
        id = server.id,
        status = server.syncStatus,
        collectorResponse = server.collectorResponse,
        proposedDate = server.proposedDate,
        collectorId = server.collectorId,
        collectorName = server.collectorName,
        requesterName = server.requesterName,
        updatedAt = server.updatedAt,
    )

    // ---------- Collector views (rows owned by other users, cached from the server) ----------

    /** SENT and unassigned, soonest pickup first. */
    @Query("SELECT * FROM collection_requests WHERE syncStatus = 'SENT' AND collectorId IS NULL AND userId != :collectorId ORDER BY pickupDate, createdAt")
    fun observeUnassigned(collectorId: String): Flow<List<CollectionRequestEntity>>

    /** Requests assigned to [collectorId] whose status name is in [statuses], soonest pickup first. */
    @Query("SELECT * FROM collection_requests WHERE collectorId = :collectorId AND syncStatus IN (:statuses) ORDER BY pickupDate, createdAt")
    fun observeAssigned(collectorId: String, statuses: List<String>): Flow<List<CollectionRequestEntity>>

    /** Insert or overwrite rows that mirror the server (never used for the user's own unsent rows). */
    @Upsert
    suspend fun upsertAll(requests: List<CollectionRequestEntity>)

    /**
     * Drops cached rows of OTHER users that the server no longer shows this collector
     * (e.g. taken by someone else). Never touches unsent rows.
     */
    // Collected rows and rows with a receipt are kept: deleting the request would
    // CASCADE-delete its receipt, possibly before the receipt was uploaded.
    @Query(
        """
        DELETE FROM collection_requests
        WHERE userId != :collectorId AND syncStatus NOT IN ('PENDING_SUBMISSION', 'COLLECTED')
          AND id NOT IN (SELECT requestId FROM collection_receipts)
          AND id NOT IN (:keepIds)
        """,
    )
    suspend fun deleteStaleCollectorRows(collectorId: String, keepIds: List<String>): Int

    @Query(
        """
        DELETE FROM collection_requests
        WHERE userId != :collectorId AND syncStatus NOT IN ('PENDING_SUBMISSION', 'COLLECTED')
          AND id NOT IN (SELECT requestId FROM collection_receipts)
        """,
    )
    suspend fun deleteAllCollectorRows(collectorId: String): Int

    /** Only call in the same transaction as the receipt insert: never COLLECTED without a receipt. */
    @Query("UPDATE collection_requests SET syncStatus = 'COLLECTED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCollected(id: String, updatedAt: Long): Int

    companion object {
        /** User id used before authentication was added. */
        const val LEGACY_DEMO_USER_ID = "demo-user"
    }
}
