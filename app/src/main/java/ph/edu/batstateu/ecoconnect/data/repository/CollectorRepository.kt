package ph.edu.batstateu.ecoconnect.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.guide.PickupSchedule
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.remote.CollectorRemote
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.remote.formatServerDate
import ph.edu.batstateu.ecoconnect.data.remote.toEntity

/** What a collector decides about a SENT request. */
sealed interface CollectorAction {
    data class Accept(val message: String? = null) : CollectorAction
    data class Decline(val reason: String) : CollectorAction
    data class ProposeDate(val dateUtcMillis: Long, val message: String? = null) : CollectorAction
}

sealed interface RespondResult {
    data class Success(val request: CollectionRequestEntity) : RespondResult

    /** No internet: nothing was sent and nothing changed locally. */
    data object Offline : RespondResult

    /** Zero rows updated: another collector responded first. The list was refreshed. */
    data object AlreadyTaken : RespondResult

    /** Rejected before calling the server (missing reason, date not allowed, …). */
    data class Invalid(val message: String) : RespondResult

    /** Network or server error; nothing changed locally. */
    data class Error(val message: String) : RespondResult
}

/**
 * Collector side of requests. Lists are read from Room (so they work offline);
 * [refresh] mirrors the server's view into Room. Responding needs internet and
 * only updates Room after the server confirmed the change.
 */
class CollectorRepository(
    private val requestDao: CollectionRequestDao,
    private val remote: CollectorRemote,
    private val network: NetworkMonitor,
    private val guide: suspend () -> WasteGuide,
    private val onRefreshed: (timeMillis: Long) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** SENT and unassigned, soonest pickup first. */
    fun observeNew(collectorId: String): Flow<List<CollectionRequestEntity>> = requestDao.observeUnassigned(collectorId)

    /** Accepted or rescheduled pickups assigned to me. */
    fun observeMine(collectorId: String): Flow<List<CollectionRequestEntity>> =
        requestDao.observeAssigned(collectorId, listOf(SyncStatus.ACCEPTED.name, SyncStatus.RESCHEDULED.name))

    /** Declined or collected requests assigned to me. */
    fun observeHistory(collectorId: String): Flow<List<CollectionRequestEntity>> =
        requestDao.observeAssigned(collectorId, listOf(SyncStatus.DECLINED.name, SyncStatus.COLLECTED.name))

    fun observeRequest(id: String): Flow<CollectionRequestEntity?> = requestDao.observeById(id)

    fun isOnline(): Boolean = network.isOnline()

    fun observeOnline(): Flow<Boolean> = network.observeOnline()

    /**
     * Pulls SENT+unassigned and my assigned requests into Room, and drops cached rows
     * the server no longer shows me (e.g. taken by another collector). Returns the row count.
     */
    suspend fun refresh(collectorId: String): Result<Int> = mutex.withLock { refreshLocked(collectorId) }

    private suspend fun refreshLocked(collectorId: String): Result<Int> {
        val rows = remote.fetchCollectorRequests(collectorId).getOrElse { return Result.failure(it) }
        val entities = rows.mapNotNull { dto ->
            try {
                dto.toEntity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed server row ${dto.id}: ${e.message}")
                null
            }
        }
            // Never overwrite a row that is still waiting to be uploaded from this phone.
            .filter { requestDao.getById(it.id)?.syncStatus != SyncStatus.PENDING_SUBMISSION }
        requestDao.upsertAll(entities)
        if (rows.isEmpty()) {
            requestDao.deleteAllCollectorRows(collectorId)
        } else {
            requestDao.deleteStaleCollectorRows(collectorId, rows.map { it.id })
        }
        onRefreshed(clock())
        return Result.success(entities.size)
    }

    suspend fun respond(request: CollectionRequestEntity, collectorId: String, action: CollectorAction): RespondResult {
        // Validate locally first so bad input never reaches the server.
        val (status, response, proposedDate) = when (action) {
            is CollectorAction.Accept -> Triple(SyncStatus.ACCEPTED, action.message.clean(), null)
            is CollectorAction.Decline -> {
                val reason = action.reason.clean() ?: return RespondResult.Invalid("Please give a reason for declining.")
                Triple(SyncStatus.DECLINED, reason, null)
            }
            is CollectorAction.ProposeDate -> {
                if (!PickupSchedule.isProposalDateAllowed(action.dateUtcMillis, request.wasteItems, guide())) {
                    return RespondResult.Invalid("That date isn't a collection day for this request's waste.")
                }
                Triple(SyncStatus.RESCHEDULED, action.message.clean(), formatServerDate(action.dateUtcMillis))
            }
        }
        if (request.syncStatus != SyncStatus.SENT) return RespondResult.Invalid("This request was already answered.")
        if (!network.isOnline()) return RespondResult.Offline

        return mutex.withLock {
            val rows = remote.respondToRequest(request.id, collectorId, status, response, proposedDate)
                .getOrElse { return@withLock RespondResult.Error(AuthRepository.friendlyMessage(it)) }
            if (rows.isEmpty()) {
                refreshLocked(collectorId) // remove it from "New"
                return@withLock RespondResult.AlreadyTaken
            }
            val updated = try {
                rows.first().toEntity()
            } catch (e: IllegalArgumentException) {
                // The server accepted it; mirror what we know locally.
                request.copy(syncStatus = status, collectorId = collectorId, collectorResponse = response,
                    proposedDate = (action as? CollectorAction.ProposeDate)?.dateUtcMillis, updatedAt = clock())
            }
            requestDao.upsertAll(listOf(updated))
            RespondResult.Success(updated)
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "CollectorRepository"
    }
}
