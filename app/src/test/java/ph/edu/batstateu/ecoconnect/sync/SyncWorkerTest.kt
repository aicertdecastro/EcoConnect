package ph.edu.batstateu.ecoconnect.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import java.io.IOException

/** Test Application: hands the worker a fake backend instead of Supabase + Room. */
class TestSyncApp : Application(), SyncBackendProvider {
    var backend = FakeSyncBackend()
    override val syncBackend: SyncBackend get() = backend
}

/** In-memory stand-in for RequestRepository + AuthRepository + SyncStatusStore. */
class FakeSyncBackend(
    var userId: String? = USER,
    val requests: MutableList<CollectionRequestEntity> = mutableListOf(),
    val receipts: MutableList<CollectionReceiptEntity> = mutableListOf(),
) : SyncBackend {
    var failRequestIds = mutableSetOf<String>()
    var failReceipts = false
    var failRefresh = false
    /** Runs once, during the first upload (simulates a request saved mid-sync). */
    var onFirstUpload: (() -> Unit)? = null

    var uploadCalls = 0
    var receiptCalls = 0
    var refreshCalls = 0
    var lastSynced: Long? = null
    private var clock = 10_000L

    override suspend fun awaitSignedInUserId() = userId

    override suspend fun oldestPendingRequest(userId: String) = requests
        .filter { it.userId == userId && it.syncStatus == SyncStatus.PENDING_SUBMISSION }
        .minByOrNull { it.createdAt }

    override suspend fun uploadRequest(request: CollectionRequestEntity): Result<Long> {
        uploadCalls++
        onFirstUpload?.also { onFirstUpload = null }?.invoke()
        if (request.id in failRequestIds) return Result.failure(IOException("offline"))
        val sentAt = ++clock
        val i = requests.indexOfFirst { it.id == request.id }
        requests[i] = requests[i].copy(syncStatus = SyncStatus.SENT, sentAt = sentAt)
        return Result.success(sentAt)
    }

    override suspend fun oldestUnuploadedReceipt() = receipts.filter { !it.uploaded }.minByOrNull { it.confirmedAt }

    override suspend fun uploadReceipt(receipt: CollectionReceiptEntity, uploadedBy: String): Result<Unit> {
        receiptCalls++
        if (failReceipts) return Result.failure(IOException("server error"))
        val i = receipts.indexOfFirst { it.receiptId == receipt.receiptId }
        receipts[i] = receipts[i].copy(uploaded = true)
        return Result.success(Unit)
    }

    override suspend fun refresh(userId: String): Result<Int> {
        refreshCalls++
        return if (failRefresh) Result.failure(IOException("timeout")) else Result.success(0)
    }

    override fun onSyncCompleted(timeMillis: Long) {
        lastSynced = timeMillis
    }

    companion object {
        const val USER = "user-1"

        fun request(id: String, createdAt: Long, userId: String = USER) = CollectionRequestEntity(
            id = id,
            userId = userId,
            wasteItems = listOf(WasteItem("RECYCLABLE", 1.0)),
            address = "Alangilan",
            pickupDate = 0L,
            pickupTime = "08:00",
            syncStatus = SyncStatus.PENDING_SUBMISSION,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        fun receipt(id: String) = CollectionReceiptEntity(
            receiptId = id,
            requestId = "r1",
            userId = USER,
            collectorId = "collector-1",
            confirmedAt = 1L,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestSyncApp::class)
class SyncWorkerTest {

    private lateinit var app: TestSyncApp
    private val backend get() = app.backend

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.backend = FakeSyncBackend()
    }

    private suspend fun runWorker(attempt: Int = 0): ListenableWorker.Result =
        TestListenableWorkerBuilder<SyncWorker>(app).setRunAttemptCount(attempt).build().doWork()

    @Test
    fun successMarksEveryPendingRequestSentAndRefreshes() = runTest {
        backend.requests += FakeSyncBackend.request("b", createdAt = 2)
        backend.requests += FakeSyncBackend.request("a", createdAt = 1)
        backend.receipts += FakeSyncBackend.receipt("rc1")

        assertEquals(ListenableWorker.Result.success(), runWorker())

        backend.requests.forEach {
            assertEquals(SyncStatus.SENT, it.syncStatus)
            assertNotNull(it.sentAt)
        }
        // Oldest first.
        assertTrue(backend.requests.first { it.id == "a" }.sentAt!! < backend.requests.first { it.id == "b" }.sentAt!!)
        assertTrue(backend.receipts.single().uploaded)
        assertEquals(1, backend.refreshCalls)
        assertNotNull(backend.lastSynced)
    }

    @Test
    fun uploadFailureRetriesAndKeepsRequestPending() = runTest {
        backend.requests += FakeSyncBackend.request("a", createdAt = 1)
        backend.failRequestIds += "a"

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        val row = backend.requests.single()
        assertEquals(SyncStatus.PENDING_SUBMISSION, row.syncStatus)
        assertNull(row.sentAt)
        assertEquals(0, backend.refreshCalls)
        assertNull(backend.lastSynced)
    }

    @Test
    fun failureAfterSomeUploadsKeepsTheSuccessfulOnes() = runTest {
        backend.requests += FakeSyncBackend.request("a", createdAt = 1)
        backend.requests += FakeSyncBackend.request("b", createdAt = 2)
        backend.failRequestIds += "b"

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertEquals(SyncStatus.SENT, backend.requests.first { it.id == "a" }.syncStatus)
        assertEquals(SyncStatus.PENDING_SUBMISSION, backend.requests.first { it.id == "b" }.syncStatus)
        assertEquals(2, backend.requests.size) // nothing deleted
    }

    @Test
    fun noSessionReturnsSuccessWithoutTouchingData() = runTest {
        backend.userId = null
        backend.requests += FakeSyncBackend.request("a", createdAt = 1)
        backend.receipts += FakeSyncBackend.receipt("rc1")
        val before = backend.requests.toList() to backend.receipts.toList()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(before, backend.requests.toList() to backend.receipts.toList())
        assertEquals(0, backend.uploadCalls)
        assertEquals(0, backend.receiptCalls)
        assertEquals(0, backend.refreshCalls)
        assertNull(backend.lastSynced)
    }

    @Test
    fun requestSavedDuringRunIsAlsoUploaded() = runTest {
        backend.requests += FakeSyncBackend.request("a", createdAt = 1)
        backend.onFirstUpload = { backend.requests += FakeSyncBackend.request("late", createdAt = 5) }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(SyncStatus.SENT, SyncStatus.SENT), backend.requests.map { it.syncStatus })
        assertEquals(2, backend.uploadCalls)
    }

    @Test
    fun onlyTheSignedInUsersRequestsAreUploaded() = runTest {
        backend.requests += FakeSyncBackend.request("mine", createdAt = 1)
        backend.requests += FakeSyncBackend.request("theirs", createdAt = 2, userId = "someone-else")

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(SyncStatus.SENT, backend.requests.first { it.id == "mine" }.syncStatus)
        assertEquals(SyncStatus.PENDING_SUBMISSION, backend.requests.first { it.id == "theirs" }.syncStatus)
    }

    @Test
    fun receiptFailureRetriesAndKeepsReceiptNotUploaded() = runTest {
        backend.receipts += FakeSyncBackend.receipt("rc1")
        backend.failReceipts = true

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertTrue(!backend.receipts.single().uploaded)
        assertEquals(0, backend.refreshCalls)
    }

    @Test
    fun refreshFailureRetriesButUploadsStayDone() = runTest {
        backend.requests += FakeSyncBackend.request("a", createdAt = 1)
        backend.failRefresh = true

        assertEquals(ListenableWorker.Result.retry(), runWorker(attempt = 2))

        assertEquals(SyncStatus.SENT, backend.requests.single().syncStatus)
        assertNull(backend.lastSynced)
    }
}
