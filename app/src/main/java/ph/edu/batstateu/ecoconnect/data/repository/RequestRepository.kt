package ph.edu.batstateu.ecoconnect.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.data.remote.MergeAction
import ph.edu.batstateu.ecoconnect.data.remote.RemoteRequestDataSource
import ph.edu.batstateu.ecoconnect.data.remote.mergeServerRequest
import ph.edu.batstateu.ecoconnect.data.remote.toDto
import ph.edu.batstateu.ecoconnect.data.remote.toEntity
import java.util.UUID

/**
 * Single source of truth for collection requests and receipts.
 * Offline-first: every write goes to Room first, then [requestSync] asks WorkManager
 * to upload when there's a network. A failed upload only leaves the row pending;
 * nothing local is ever deleted.
 */
class RequestRepository(
    private val requestDao: CollectionRequestDao,
    private val receiptDao: CollectionReceiptDao,
    private val remote: RemoteRequestDataSource,
    private val requestSync: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Serializes refreshes with uploads so they never race on the same rows. */
    private val mutex = Mutex()

    fun observeRequests(userId: String): Flow<List<CollectionRequestEntity>> = requestDao.observeAll(userId)

    fun observeRequest(id: String): Flow<CollectionRequestEntity?> = requestDao.observeById(id)

    fun observePendingCount(userId: String): Flow<Int> = requestDao.observePendingCount(userId)

    /** Saves a new request locally as PENDING_SUBMISSION, schedules a sync, and returns its id. */
    suspend fun submitRequest(
        userId: String,
        wasteItems: List<WasteItem>,
        address: String,
        pickupDate: Long,
        pickupTime: String,
        notes: String? = null,
    ): String {
        val now = clock()
        val request = CollectionRequestEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            wasteItems = wasteItems,
            address = address.trim(),
            pickupDate = pickupDate,
            pickupTime = pickupTime,
            notes = notes,
            syncStatus = SyncStatus.PENDING_SUBMISSION,
            createdAt = now,
            updatedAt = now,
        )
        requestDao.insert(request)
        requestSync()
        return request.id
    }

    /** Saves a collection receipt locally (uploaded = false) and schedules a sync. Returns its id. */
    suspend fun saveReceipt(
        requestId: String,
        userId: String,
        collectorId: String,
        confirmedAt: Long = clock(),
    ): String {
        val receipt = CollectionReceiptEntity(
            receiptId = UUID.randomUUID().toString(),
            requestId = requestId,
            userId = userId,
            collectorId = collectorId,
            confirmedAt = confirmedAt,
        )
        receiptDao.insert(receipt)
        requestSync()
        return receipt.receiptId
    }

    /** Adopts rows made before sign-in existed ("demo-user"). */
    suspend fun claimDemoRows(userId: String) {
        val claimed = requestDao.claimDemoRows(userId)
        if (claimed > 0) Log.i(TAG, "Assigned $claimed demo request(s) to the signed-in user")
    }

    // ---------- Used by the sync worker ----------

    suspend fun oldestPendingRequest(userId: String): CollectionRequestEntity? = requestDao.getOldestPending(userId)

    /** Idempotent upsert; on success marks the row SENT and returns sentAt. */
    suspend fun uploadRequest(request: CollectionRequestEntity): Result<Long> = mutex.withLock {
        remote.pushRequest(request.toDto()).map {
            val sentAt = clock()
            requestDao.markSent(request.id, sentAt)
            sentAt
        }
    }

    suspend fun oldestUnuploadedReceipt(): CollectionReceiptEntity? = receiptDao.getOldestNotUploaded()

    /**
     * Uploads the receipt; when this phone is the collector, also sets the server request to
     * COLLECTED. Marked uploaded only after every call succeeded (all are idempotent).
     */
    suspend fun uploadReceipt(receipt: CollectionReceiptEntity, uploadedBy: String): Result<Unit> {
        remote.pushReceipt(receipt.toDto(uploadedBy)).onFailure { return Result.failure(it) }
        if (uploadedBy == receipt.collectorId) {
            remote.markCollected(receipt.requestId, receipt.collectorId).onFailure { return Result.failure(it) }
        }
        receiptDao.markUploaded(receipt.receiptId)
        return Result.success(Unit)
    }

    fun observeReceipt(requestId: String): Flow<CollectionReceiptEntity?> = receiptDao.observeForRequest(requestId)

    /**
     * Pulls the user's requests from the server into Room. Local rows that are still
     * PENDING_SUBMISSION are not overwritten; rows missing on the server are kept.
     * Returns how many local rows were inserted or updated.
     */
    suspend fun refreshFromServer(userId: String): Result<Int> = mutex.withLock {
        val serverRows = remote.fetchMyRequests(userId).getOrElse { return@withLock Result.failure(it) }
        var changed = 0
        for (dto in serverRows) {
            try {
                val server = dto.toEntity()
                when (val action = mergeServerRequest(requestDao.getById(server.id), server)) {
                    is MergeAction.Insert -> if (requestDao.insert(action.entity) != -1L) changed++
                    is MergeAction.UpdateFromServer -> {
                        requestDao.updateFromServer(action.server)
                        changed++
                    }
                    MergeAction.Skip -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed server row ${dto.id}: ${e.message}")
            }
        }
        Result.success(changed)
    }

    private companion object {
        const val TAG = "RequestRepository"
    }
}
