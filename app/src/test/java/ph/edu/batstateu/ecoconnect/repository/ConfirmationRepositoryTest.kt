package ph.edu.batstateu.ecoconnect.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.bluetooth.COLLECTOR
import ph.edu.batstateu.ecoconnect.bluetooth.REQUEST_ID
import ph.edu.batstateu.ecoconnect.bluetooth.RESIDENT
import ph.edu.batstateu.ecoconnect.bluetooth.testRequest
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.remote.CollectionReceiptDto
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.repository.ConfirmationRepository
import ph.edu.batstateu.ecoconnect.ui.confirm.ConfirmRoute
import ph.edu.batstateu.ecoconnect.ui.confirm.OnlineConfirmState
import ph.edu.batstateu.ecoconnect.ui.confirm.OnlineConfirmViewModel
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull

/** Unique requestId + IGNORE, like the Room table. */
private class FakeReceiptDao : CollectionReceiptDao {
    val rows = MutableStateFlow<List<CollectionReceiptEntity>>(emptyList())

    override suspend fun insert(receipt: CollectionReceiptEntity): Long {
        if (rows.value.any { it.requestId == receipt.requestId || it.receiptId == receipt.receiptId }) return -1
        rows.value += receipt
        return rows.value.size.toLong()
    }
    override suspend fun getNotUploaded() = rows.value.filter { !it.uploaded }
    override suspend fun getOldestNotUploaded() = getNotUploaded().minByOrNull { it.confirmedAt }
    override suspend fun markUploaded(receiptId: String) {
        rows.value = rows.value.map { if (it.receiptId == receiptId) it.copy(uploaded = true) else it }
    }
    override suspend fun getForRequest(requestId: String) = rows.value.firstOrNull { it.requestId == requestId }
    override fun observeForRequest(requestId: String): Flow<CollectionReceiptEntity?> =
        rows.map { list -> list.firstOrNull { it.requestId == requestId } }
}

class ConfirmationRepositoryTest {

    private val requests = FakeRequestDao()
    private val receipts = FakeReceiptDao()
    private var syncs = 0
    private var transactions = 0
    private val repo = ConfirmationRepository(
        requestDao = requests,
        receiptDao = receipts,
        inTransaction = { block ->
            transactions++
            block()
        },
        requestSync = { syncs++ },
        uploader = { dto, markCollected ->
            uploads += dto to markCollected
            if (serverUp) Result.success(Unit) else Result.failure(java.io.IOException("offline"))
        },
        clock = { 9_000L },
    )
    private val uploads = mutableListOf<Pair<CollectionReceiptDto, Boolean>>()
    private var serverUp = true

    @Test
    fun saveWritesReceiptMarksCollectedAndSchedulesSync() = runTest {
        requests.upsertAll(listOf(testRequest()))

        val saved = repo.saveConfirmed(REQUEST_ID, RESIDENT, COLLECTOR, confirmedAt = 5_000L, durationMs = 2_100L)

        assertEquals(5_000L, saved.confirmedAt)
        assertEquals(2_100L, saved.durationMs)
        assertEquals(false, saved.uploaded)
        assertEquals(SyncStatus.COLLECTED, requests.getById(REQUEST_ID)?.syncStatus)
        assertEquals(1, syncs)
        assertEquals(1, transactions)
    }

    @Test
    fun retryNeverDuplicatesAndKeepsFirstReceipt() = runTest {
        requests.upsertAll(listOf(testRequest()))

        val first = repo.saveConfirmed(REQUEST_ID, RESIDENT, COLLECTOR, 5_000L, 2_100L)
        val second = repo.saveConfirmed(REQUEST_ID, RESIDENT, COLLECTOR, 6_000L, 900L)

        assertEquals(1, receipts.rows.value.size)
        assertEquals(first, second)
        assertEquals(5_000L, second.confirmedAt)
        assertTrue(syncs >= 1)
    }

    // ---------- Online confirmation ----------

    @Test
    fun collectorOnlineUploadsMarksServerCollectedThenSavesLocally() = runTest {
        requests.upsertAll(listOf(testRequest()))

        val saved = repo.confirmOnline(REQUEST_ID, COLLECTOR).getOrThrow()

        assertEquals(1, uploads.size)
        val (dto, markCollected) = uploads.single()
        assertTrue("collector also sets the server row to COLLECTED", markCollected)
        assertEquals(COLLECTOR, dto.uploadedBy)
        assertEquals(RESIDENT, dto.userId)
        assertTrue(saved.uploaded)
        assertNull("online receipts have no Bluetooth duration", saved.durationMs)
        assertEquals(9_000L, saved.confirmedAt)
        assertEquals(SyncStatus.COLLECTED, requests.getById(REQUEST_ID)?.syncStatus)
        assertEquals("nothing left for the sync worker", 0, syncs)
    }

    @Test
    fun residentOnlineUploadsOwnReceiptOnly() = runTest {
        requests.upsertAll(listOf(testRequest(status = SyncStatus.RESCHEDULED)))

        repo.confirmOnline(REQUEST_ID, RESIDENT).getOrThrow()

        val (dto, markCollected) = uploads.single()
        assertFalse("only the collector may set COLLECTED on the server", markCollected)
        assertEquals(RESIDENT, dto.uploadedBy)
        assertEquals(SyncStatus.COLLECTED, requests.getById(REQUEST_ID)?.syncStatus)
    }

    @Test
    fun serverFailureSavesNothing() = runTest {
        requests.upsertAll(listOf(testRequest()))
        serverUp = false

        val result = repo.confirmOnline(REQUEST_ID, COLLECTOR)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Bluetooth"))
        assertTrue(receipts.rows.value.isEmpty())
        assertEquals(SyncStatus.ACCEPTED, requests.getById(REQUEST_ID)?.syncStatus)
    }

    @Test
    fun refusesUnacceptedStrangersAndMissing() = runTest {
        requests.upsertAll(listOf(testRequest(status = SyncStatus.SENT, collectorId = null)))
        assertTrue(repo.confirmOnline(REQUEST_ID, COLLECTOR).isFailure)

        requests.upsertAll(listOf(testRequest()))
        assertTrue("not the resident or the collector", repo.confirmOnline(REQUEST_ID, "someone-else").isFailure)
        assertTrue(repo.confirmOnline("missing-id", COLLECTOR).isFailure)
        assertTrue(uploads.isEmpty())
    }

    @Test
    fun alreadyConfirmedReturnsExistingReceiptWithoutUploading() = runTest {
        requests.upsertAll(listOf(testRequest()))
        val first = repo.saveConfirmed(REQUEST_ID, RESIDENT, COLLECTOR, 5_000L, 2_100L)
        requests.upsertAll(listOf(testRequest())) // still ACCEPTED on this phone, e.g. after a refresh

        assertEquals(first, repo.confirmOnline(REQUEST_ID, COLLECTOR).getOrThrow())
        assertTrue(uploads.isEmpty())
    }

    // ---------- Online or Bluetooth? ----------

    private fun network(online: Boolean) = object : NetworkMonitor {
        override fun isOnline() = online
        override fun observeOnline() = flowOf(online)
    }

    @Test
    fun noInternetGoesToBluetooth() {
        val vm = OnlineConfirmViewModel(repo, network(online = false)) { COLLECTOR }
        assertEquals(ConfirmRoute.Bluetooth, vm.onConfirmClicked())
        assertEquals(OnlineConfirmState.Idle, vm.state.value)
    }

    @Test
    fun internetAsksToConfirmOnline() {
        val vm = OnlineConfirmViewModel(repo, network(online = true)) { COLLECTOR }
        assertEquals(ConfirmRoute.Online, vm.onConfirmClicked())
        assertEquals(OnlineConfirmState.Asking, vm.state.value)
        vm.dismiss()
        assertEquals(OnlineConfirmState.Idle, vm.state.value)
    }
}
