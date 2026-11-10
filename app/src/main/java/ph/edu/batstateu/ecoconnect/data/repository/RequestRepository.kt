package ph.edu.batstateu.ecoconnect.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.data.remote.MergeAction
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.remote.RemoteRequestDataSource
import ph.edu.batstateu.ecoconnect.data.remote.mergeServerRequest
import ph.edu.batstateu.ecoconnect.data.remote.toDto
import ph.edu.batstateu.ecoconnect.data.remote.toEntity
import java.util.UUID

/** Outcome of [RequestRepository.syncNow]. */
data class SyncResult(
    val pushed: Int = 0,
    val failed: Int = 0,
    val refreshed: Int = 0,
    val offline: Boolean = false,
    val error: String? = null,
)

/**
 * Single source of truth for collection requests.
 * Offline-first: every write goes to Room first; uploads are best-effort and a
 * failure only leaves the row as PENDING_SUBMISSION. Nothing local is ever deleted.
 */
class RequestRepository(
    private val requestDao: CollectionRequestDao,
    @Suppress("unused") private val receiptDao: CollectionReceiptDao,
    private val remote: RemoteRequestDataSource,
    private val network: NetworkMonitor,
    private val appScope: CoroutineScope,
) {
    /** Serializes uploads/refreshes so two syncs never race on the same rows. */
    private val syncMutex = Mutex()

    fun observeRequests(userId: String): Flow<List<CollectionRequestEntity>> = requestDao.observeAll(userId)

    fun observeRequest(id: String): Flow<CollectionRequestEntity?> = requestDao.observeById(id)

    fun observePendingCount(userId: String): Flow<Int> = requestDao.observePendingCount(userId)

    /**
     * Saves a new request locally as PENDING_SUBMISSION and returns its id right away.
     * If online, one upload attempt runs in the background (it outlives the screen).
     */
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
        if (network.isOnline()) {
            appScope.launch { syncMutex.withLock { pushOne(request.id) } }
        }
        return request.id
    }

    /** Pushes every pending request of [userId], then pulls server status. */
    suspend fun syncNow(userId: String): SyncResult {
        if (!network.isOnline()) return SyncResult(offline = true)
        return syncMutex.withLock {
            var pushed = 0
            var failed = 0
            var firstError: String? = null
            for (request in requestDao.getPending(userId)) {
                val result = pushOne(request.id)
                if (result.isSuccess) pushed++ else {
                    failed++
                    if (firstError == null) firstError = result.exceptionOrNull()?.let(::describe)
                }
            }
            val refresh = refreshLocked(userId)
            SyncResult(
                pushed = pushed,
                failed = failed,
                refreshed = refresh.getOrDefault(0),
                error = firstError ?: refresh.exceptionOrNull()?.let(::describe),
            )
        }
    }

    /**
     * Pulls the user's requests from the server into Room. Local rows that are still
     * PENDING_SUBMISSION are not overwritten; rows missing on the server are kept.
     * Returns how many local rows were inserted or updated.
     */
    suspend fun refreshFromServer(userId: String): Result<Int> = syncMutex.withLock { refreshLocked(userId) }

    /** Called once per sign-in: adopt pre-auth rows, then pull server state if online. */
    suspend fun onSignedIn(userId: String) {
        val claimed = requestDao.claimDemoRows(userId)
        if (claimed > 0) Log.i(TAG, "Assigned $claimed demo request(s) to the signed-in user")
        if (network.isOnline()) refreshFromServer(userId)
    }

    private suspend fun pushOne(id: String): Result<Unit> {
        val request = requestDao.getById(id) ?: return Result.success(Unit)
        if (request.syncStatus != SyncStatus.PENDING_SUBMISSION) return Result.success(Unit)
        return remote.pushRequest(request.toDto())
            .onSuccess { requestDao.markSent(id) }
    }

    private suspend fun refreshLocked(userId: String): Result<Int> {
        val serverRows = remote.fetchMyRequests(userId).getOrElse { return Result.failure(it) }
        var changed = 0
        for (dto in serverRows) {
            try {
                val server = dto.toEntity()
                when (val action = mergeServerRequest(requestDao.getById(server.id), server)) {
                    is MergeAction.Insert -> if (requestDao.insert(action.entity) != -1L) changed++
                    is MergeAction.UpdateStatus -> {
                        requestDao.updateStatus(
                            id = action.id,
                            status = action.status,
                            collectorResponse = action.collectorResponse,
                            proposedDate = action.proposedDate,
                            updatedAt = action.updatedAt,
                        )
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
        return Result.success(changed)
    }

    private fun describe(e: Throwable): String = AuthRepository.friendlyMessage(e)

    private companion object {
        const val TAG = "RequestRepository"
    }
}
