package ph.edu.batstateu.ecoconnect.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.data.remote.CollectionRequestRowDto
import ph.edu.batstateu.ecoconnect.data.remote.CollectorRemote
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.remote.ProfileNameDto
import ph.edu.batstateu.ecoconnect.data.repository.CollectorAction
import ph.edu.batstateu.ecoconnect.data.repository.CollectorRepository
import ph.edu.batstateu.ecoconnect.data.repository.RespondResult
import ph.edu.batstateu.ecoconnect.guide.TestGuide
import java.io.IOException

private const val ME = "collector-1"

/** In-memory DAO with just enough SQL semantics for the collector paths. */
internal class FakeRequestDao : CollectionRequestDao {
    val rows = MutableStateFlow<Map<String, CollectionRequestEntity>>(emptyMap())
    var writes = 0

    private fun put(e: CollectionRequestEntity) {
        writes++
        rows.value += e.id to e
    }

    override fun observeAll(userId: String) = rows.map { m -> m.values.filter { it.userId == userId } }
    override fun observeById(id: String) = rows.map { it[id] }
    override suspend fun getById(id: String) = rows.value[id]
    override suspend fun getPending(userId: String) =
        rows.value.values.filter { it.userId == userId && it.syncStatus == SyncStatus.PENDING_SUBMISSION }
    override fun observePendingCount(userId: String): Flow<Int> = flowOf(0)
    override suspend fun insert(request: CollectionRequestEntity): Long {
        if (request.id in rows.value) return -1
        put(request)
        return 1
    }
    override suspend fun getOldestPending(userId: String) = getPending(userId).minByOrNull { it.createdAt }
    override suspend fun markSent(id: String, sentAt: Long) = Unit
    override suspend fun claimDemoRows(userId: String) = 0
    override suspend fun updateFromServer(
        id: String,
        status: SyncStatus,
        collectorResponse: String?,
        proposedDate: Long?,
        collectorId: String?,
        collectorName: String?,
        requesterName: String?,
        updatedAt: Long,
    ) {
        rows.value[id]?.let {
            put(
                it.copy(
                    syncStatus = status, collectorResponse = collectorResponse, proposedDate = proposedDate,
                    collectorId = collectorId, collectorName = collectorName, requesterName = requesterName,
                    updatedAt = updatedAt,
                ),
            )
        }
    }
    override fun observeUnassigned(collectorId: String) = rows.map { m ->
        m.values.filter { it.syncStatus == SyncStatus.SENT && it.collectorId == null && it.userId != collectorId }
    }
    override fun observeAssigned(collectorId: String, statuses: List<String>) = rows.map { m ->
        m.values.filter { it.collectorId == collectorId && it.syncStatus.name in statuses }
    }
    override suspend fun upsertAll(requests: List<CollectionRequestEntity>) = requests.forEach(::put)
    override suspend fun markCollected(id: String, updatedAt: Long): Int {
        val row = rows.value[id] ?: return 0
        put(row.copy(syncStatus = SyncStatus.COLLECTED, updatedAt = updatedAt))
        return 1
    }
    override suspend fun deleteStaleCollectorRows(collectorId: String, keepIds: List<String>): Int {
        val drop = rows.value.values.filter {
            it.userId != collectorId && it.syncStatus != SyncStatus.PENDING_SUBMISSION &&
                it.syncStatus != SyncStatus.COLLECTED && it.id !in keepIds
        }
        if (drop.isNotEmpty()) writes++
        rows.value -= drop.map { it.id }.toSet()
        return drop.size
    }
    override suspend fun deleteAllCollectorRows(collectorId: String) = deleteStaleCollectorRows(collectorId, emptyList())
}

private class FakeRemote : CollectorRemote {
    var serverRows: List<CollectionRequestRowDto> = emptyList()
    var respondResult: Result<List<CollectionRequestRowDto>> = Result.success(emptyList())
    var fetchCalls = 0
    var respondCalls = 0
    var lastStatus: SyncStatus? = null
    var lastResponse: String? = null
    var lastProposedDate: String? = null

    override suspend fun fetchCollectorRequests(collectorId: String): Result<List<CollectionRequestRowDto>> {
        fetchCalls++
        return Result.success(serverRows)
    }

    override suspend fun respondToRequest(
        id: String,
        collectorId: String,
        status: SyncStatus,
        response: String?,
        proposedDate: String?,
    ): Result<List<CollectionRequestRowDto>> {
        respondCalls++
        lastStatus = status
        lastResponse = response
        lastProposedDate = proposedDate
        return respondResult
    }
}

private class FakeNetwork(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline() = online
    override fun observeOnline(): Flow<Boolean> = flowOf(online)
}

class CollectorRepositoryTest {

    // 2026-11-21 (Saturday) 00:00 UTC – a recyclable collection day.
    private val saturday = 1_795_219_200_000L

    private val request = CollectionRequestEntity(
        id = "req-1",
        userId = "resident-1",
        wasteItems = listOf(WasteItem("RECYCLABLE", 2.0)),
        address = "Brgy. Alangilan",
        pickupDate = saturday - 86_400_000L,
        pickupTime = "07:30",
        notes = null,
        syncStatus = SyncStatus.SENT,
        requesterName = "Juan",
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun row(status: String, collectorId: String?, collector: String? = null, response: String? = null) =
        CollectionRequestRowDto(
            id = request.id,
            userId = request.userId,
            wasteItems = request.wasteItems,
            totalKg = 2.0,
            address = request.address,
            pickupDate = "2026-11-20",
            pickupTime = "07:30:00",
            status = status,
            collectorId = collectorId,
            collectorResponse = response,
            createdAt = "2026-11-18T08:30:15+00:00",
            updatedAt = "2026-11-18T09:00:00+00:00",
            requester = ProfileNameDto("Juan"),
            collector = collector?.let(::ProfileNameDto),
        )

    private val dao = FakeRequestDao()
    private val remote = FakeRemote()
    private val network = FakeNetwork()
    private var refreshedAt: Long? = null
    private val repo = CollectorRepository(
        requestDao = dao,
        remote = remote,
        network = network,
        guide = { TestGuide.guide },
        onRefreshed = { refreshedAt = it },
        clock = { 5_000L },
    )

    private suspend fun seed() {
        dao.upsertAll(listOf(request))
        dao.writes = 0
    }

    @Test
    fun refreshNeverDowngradesLocallyCollectedRow() = runTest {
        // Confirmed over Bluetooth offline; server still says ACCEPTED.
        dao.upsertAll(listOf(request.copy(syncStatus = SyncStatus.COLLECTED, collectorId = ME)))
        remote.serverRows = listOf(row("ACCEPTED", ME, collector = "Pedro"))

        repo.refresh(ME)

        val local = dao.rows.value.getValue(request.id)
        assertEquals(SyncStatus.COLLECTED, local.syncStatus)
        assertEquals("Pedro", local.collectorName) // other fields still update
    }

    @Test
    fun refreshKeepsCollectedRowsTheServerNoLongerShows() = runTest {
        dao.upsertAll(listOf(request.copy(syncStatus = SyncStatus.COLLECTED, collectorId = ME)))
        remote.serverRows = emptyList()

        repo.refresh(ME)

        assertEquals(SyncStatus.COLLECTED, dao.rows.value[request.id]?.syncStatus)
    }

    @Test
    fun offlineReturnsErrorAndChangesNothing() = runTest {
        seed()
        network.online = false
        val before = dao.rows.value

        val result = repo.respond(request, ME, CollectorAction.Accept("On my way"))

        assertEquals(RespondResult.Offline, result)
        assertEquals(0, remote.respondCalls)
        assertEquals(0, dao.writes)
        assertEquals(before, dao.rows.value)
    }

    @Test
    fun zeroRowsUpdatedMeansAlreadyTakenAndRefreshes() = runTest {
        seed()
        remote.respondResult = Result.success(emptyList())
        remote.serverRows = emptyList() // taken by someone else, so no longer visible to me

        val result = repo.respond(request, ME, CollectorAction.Accept())

        assertEquals(RespondResult.AlreadyTaken, result)
        assertEquals(1, remote.fetchCalls)
        assertNull("removed from New", dao.rows.value[request.id])
    }

    @Test
    fun successWritesReturnedRowToRoomImmediately() = runTest {
        seed()
        remote.respondResult = Result.success(listOf(row("ACCEPTED", ME, collector = "Pedro", response = "See you")))

        val result = repo.respond(request, ME, CollectorAction.Accept("  See you "))

        assertTrue(result is RespondResult.Success)
        assertEquals("See you", remote.lastResponse)
        val saved = dao.rows.value.getValue(request.id)
        assertEquals(SyncStatus.ACCEPTED, saved.syncStatus)
        assertEquals(ME, saved.collectorId)
        assertEquals("Pedro", saved.collectorName)
    }

    @Test
    fun serverErrorKeepsRoomUnchanged() = runTest {
        seed()
        remote.respondResult = Result.failure(IOException("timeout"))

        val result = repo.respond(request, ME, CollectorAction.Accept())

        assertTrue(result is RespondResult.Error)
        assertEquals(0, dao.writes)
        assertEquals(request, dao.rows.value[request.id])
    }

    @Test
    fun declineRequiresReason() = runTest {
        seed()
        val result = repo.respond(request, ME, CollectorAction.Decline("   "))
        assertTrue(result is RespondResult.Invalid)
        assertEquals(0, remote.respondCalls)
    }

    @Test
    fun declineSendsReason() = runTest {
        seed()
        remote.respondResult = Result.success(listOf(row("DECLINED", ME, response = "Outside service area")))
        repo.respond(request, ME, CollectorAction.Decline("Outside service area"))
        assertEquals(SyncStatus.DECLINED, remote.lastStatus)
        assertEquals("Outside service area", remote.lastResponse)
    }

    @Test
    fun proposalOnNonCollectionDayIsRejectedLocally() = runTest {
        seed()
        val monday = saturday + 2 * 86_400_000L // recyclables aren't collected on Monday
        val result = repo.respond(request, ME, CollectorAction.ProposeDate(monday))
        assertTrue(result is RespondResult.Invalid)
        assertEquals(0, remote.respondCalls)
    }

    @Test
    fun proposalOnAllowedDaySendsDate() = runTest {
        seed()
        remote.respondResult = Result.success(listOf(row("RESCHEDULED", ME)))
        val result = repo.respond(request, ME, CollectorAction.ProposeDate(saturday, "Truck is full"))
        assertTrue(result is RespondResult.Success)
        assertEquals(SyncStatus.RESCHEDULED, remote.lastStatus)
        assertEquals("2026-11-21", remote.lastProposedDate)
    }

    @Test
    fun refreshStoresRowsDropsStaleOnesAndKeepsPending() = runTest {
        val stale = request.copy(id = "gone")
        val pending = request.copy(id = "mine-unsent", userId = ME, syncStatus = SyncStatus.PENDING_SUBMISSION)
        dao.upsertAll(listOf(stale, pending))
        remote.serverRows = listOf(row("SENT", null))

        val result = repo.refresh(ME)

        assertEquals(Result.success(1), result)
        assertEquals(setOf(request.id, pending.id), dao.rows.value.keys)
        assertEquals("Juan", dao.rows.value.getValue(request.id).requesterName)
        assertEquals(5_000L, refreshedAt)
    }
}
