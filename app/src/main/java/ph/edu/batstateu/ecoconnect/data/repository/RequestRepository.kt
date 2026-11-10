package ph.edu.batstateu.ecoconnect.data.repository

import kotlinx.coroutines.flow.Flow
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import java.util.UUID

/**
 * Single source of truth for collection requests.
 * Offline-first: every write goes to Room first; a future sync worker will
 * read [CollectionRequestDao.getPending] and push those rows to the server.
 */
class RequestRepository(
    private val requestDao: CollectionRequestDao,
    @Suppress("unused") private val receiptDao: CollectionReceiptDao,
) {
    fun observeRequests(): Flow<List<CollectionRequestEntity>> = requestDao.observeAll()

    fun observeRequest(id: String): Flow<CollectionRequestEntity?> = requestDao.observeById(id)

    fun observePendingCount(): Flow<Int> = requestDao.observePendingCount()

    /** Saves a new request locally with status PENDING_SUBMISSION and returns its id. */
    suspend fun submitRequest(
        userId: String,
        wasteItems: List<WasteItem>,
        address: String,
        pickupDate: Long,
        pickupTime: String,
        notes: String? = null,
    ): String {
        val now = System.currentTimeMillis()
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
        // TODO: trigger sync (WorkManager → Supabase) once it exists.
        return request.id
    }
}
