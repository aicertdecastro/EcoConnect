package ph.edu.batstateu.ecoconnect.bluetooth

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus

/**
 * Full HELLO → CONFIRM → ACK → DONE exchange over in-memory streams, in real time with
 * short step timeouts. No Bluetooth involved.
 */
class HandshakeTest {

    private val step = 400L
    private var now = 1_795_300_000_000L
    private val clock = { now }

    private val collectorStore = FakeStore(testRequest())
    private val userStore = FakeStore(testRequest())
    private val log = RecordingLogger()

    private fun collector() = Handshake(collectorStore, log, clock, step)
    private fun user() = Handshake(userStore, log, clock, step)

    /** Runs both real sides against each other. */
    private fun bothSides(): Pair<HandshakeResult, HandshakeResult> = runBlocking {
        withTimeout(10_000) {
            val (collectorEnd, userEnd) = channelPair()
            val c = async { collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now) }
            val u = async { user().runUser(userEnd, testRequest(), RESIDENT, now) }
            c.await() to u.await()
        }
    }

    @Test
    fun happyPathSavesSameReceiptOnBothPhones() {
        val (c, u) = bothSides()

        assertTrue("$c", c is HandshakeResult.Confirmed)
        assertTrue("$u", u is HandshakeResult.Confirmed && !u.missingDone)
        val cr = collectorStore.receipts.getValue(REQUEST_ID)
        val ur = userStore.receipts.getValue(REQUEST_ID)
        assertEquals(cr.confirmedAt, ur.confirmedAt)
        assertEquals(now, cr.confirmedAt)
        assertEquals(COLLECTOR, ur.collectorId)
        assertEquals(RESIDENT, cr.userId)
        assertEquals(SyncStatus.COLLECTED, collectorStore.requests.getValue(REQUEST_ID).syncStatus)
        assertEquals(SyncStatus.COLLECTED, userStore.requests.getValue(REQUEST_ID).syncStatus)
        assertTrue(cr.durationMs != null && ur.durationMs != null)
        assertEquals(1, collectorStore.syncRequests)
        assertEquals(1, userStore.syncRequests)
        assertTrue(log.lines.containsAll(listOf("COLLECTOR CONFIRMED", "USER CONFIRMED")))
    }

    @Test
    fun rejectSavesNothingOnEitherSide() {
        collectorStore.requests[REQUEST_ID] = testRequest(collectorId = "collector-2")

        val (c, u) = bothSides()

        assertEquals(HandshakeResult.Rejected(CollectorValidator.OTHER_COLLECTOR), c)
        assertEquals(HandshakeResult.Rejected(CollectorValidator.OTHER_COLLECTOR), u)
        assertEquals(0, collectorStore.saveCalls)
        assertEquals(0, userStore.saveCalls)
        assertEquals(SyncStatus.ACCEPTED, userStore.requests.getValue(REQUEST_ID).syncStatus)
    }

    @Test
    fun userRefusesConfirmFromWrongCollectorAndCollectorTimesOut() {
        userStore.requests[REQUEST_ID] = testRequest(collectorId = "collector-9")
        collectorStore.requests[REQUEST_ID] = testRequest()

        val (c, u) = runBlocking {
            val (collectorEnd, userEnd) = channelPair()
            val cJob = async { collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now) }
            val uJob = async { user().runUser(userEnd, testRequest(collectorId = "collector-9"), RESIDENT, now) }
            cJob.await() to uJob.await()
        }

        assertEquals(HandshakeResult.Rejected(UserVerifier.WRONG_COLLECTOR), u)
        assertTrue("$c", c is HandshakeResult.Incomplete) // user closed without ACK
        assertEquals(0, collectorStore.saveCalls)
        assertEquals(0, userStore.saveCalls)
    }

    @Test
    fun collectorTimesOutWaitingForAckAndSavesNothing() = runBlocking {
        val (collectorEnd, fakeUser) = channelPair()
        val c = async { collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now) }
        fakeUser.send(BtMessage.Hello(REQUEST_ID, RESIDENT, now))
        assertTrue(fakeUser.receive(2_000) is BtMessage.Confirm)
        // ...and never ACK.

        val result = c.await()

        assertTrue("$result", result is HandshakeResult.Incomplete && "stopped answering" in result.reason)
        assertEquals(0, collectorStore.saveCalls)
        assertEquals(SyncStatus.ACCEPTED, collectorStore.requests.getValue(REQUEST_ID).syncStatus)
        fakeUser.close()
    }

    @Test
    fun collectorTimesOutWhenHelloNeverComes() = runBlocking {
        val (collectorEnd, fakeUser) = channelPair()
        val result = collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now)
        assertTrue("$result", result is HandshakeResult.Incomplete)
        assertTrue(log.lines.contains("COLLECTOR INCOMPLETE"))
        fakeUser.close()
    }

    @Test
    fun userSavesWhenDoneTimesOutAfterAck() = runBlocking {
        val (fakeCollector, userEnd) = channelPair()
        val u = async { user().runUser(userEnd, testRequest(), RESIDENT, now) }
        assertTrue(fakeCollector.receive(2_000) is BtMessage.Hello)
        fakeCollector.send(BtMessage.Confirm(REQUEST_ID, COLLECTOR, 1234L))
        assertEquals(BtMessage.Ack(REQUEST_ID), fakeCollector.receive(2_000))
        // ...and never DONE.

        val result = u.await()

        assertTrue("$result", result is HandshakeResult.Confirmed && result.missingDone)
        assertEquals(1234L, userStore.receipts.getValue(REQUEST_ID).confirmedAt)
        assertEquals(SyncStatus.COLLECTED, userStore.requests.getValue(REQUEST_ID).syncStatus)
        fakeCollector.close()
    }

    @Test
    fun disconnectBeforeConfirmSavesNothing() = runBlocking {
        val (fakeCollector, userEnd) = channelPair()
        val u = async { user().runUser(userEnd, testRequest(), RESIDENT, now) }
        assertTrue(fakeCollector.receive(2_000) is BtMessage.Hello)
        fakeCollector.close() // collector phone walks away

        val result = u.await()

        assertTrue("$result", result is HandshakeResult.Incomplete && "lost" in result.reason)
        assertEquals(0, userStore.saveCalls)
        assertEquals(SyncStatus.ACCEPTED, userStore.requests.getValue(REQUEST_ID).syncStatus)
    }

    @Test
    fun collectorDisconnectAfterHelloIsIncomplete() = runBlocking {
        val (collectorEnd, fakeUser) = channelPair()
        val c = async { collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now) }
        fakeUser.send(BtMessage.Hello(REQUEST_ID, RESIDENT, now))
        assertTrue(fakeUser.receive(2_000) is BtMessage.Confirm)
        fakeUser.close()

        val result = c.await()

        assertTrue("$result", result is HandshakeResult.Incomplete)
        assertEquals(0, collectorStore.saveCalls)
    }

    @Test
    fun garbageIsIncomplete() = runBlocking {
        val aToB = MemoryPipe()
        val bToA = MemoryPipe()
        val collectorEnd = StreamMessageChannel(bToA.input, aToB.output)
        val c = async { collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now) }
        bToA.output.write("this is not json\n".toByteArray())

        val result = c.await()

        assertTrue("$result", result is HandshakeResult.Incomplete && "unexpected" in result.reason)
        assertEquals(0, collectorStore.saveCalls)
    }

    @Test
    fun failedSaveIsIncompleteAndNotCollected() = runBlocking {
        collectorStore.failSave = true
        val (collectorEnd, fakeUser) = channelPair()
        val c = async { collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now) }
        fakeUser.send(BtMessage.Hello(REQUEST_ID, RESIDENT, now))
        fakeUser.receive(2_000)
        fakeUser.send(BtMessage.Ack(REQUEST_ID))

        val result = c.await()

        assertTrue("$result", result is HandshakeResult.Incomplete)
        assertEquals(SyncStatus.ACCEPTED, collectorStore.requests.getValue(REQUEST_ID).syncStatus)
        fakeUser.close()
    }

    @Test
    fun retryAfterPartialSuccessFillsMissingSideWithoutDuplicates() {
        // Attempt 1: the user saved (DONE lost), the collector never got the ACK.
        userStore.receipts[REQUEST_ID] = ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity(
            "u-1", REQUEST_ID, RESIDENT, COLLECTOR, confirmedAt = 999L,
        )
        userStore.requests[REQUEST_ID] = testRequest(SyncStatus.COLLECTED)
        now += 60_000

        // Attempt 2.
        val (c, u) = bothSides()

        assertTrue("$c", c is HandshakeResult.Confirmed)
        assertTrue("$u", u is HandshakeResult.Confirmed)
        assertEquals(999L, collectorStore.receipts.getValue(REQUEST_ID).confirmedAt) // same time on both phones
        assertEquals(1, userStore.receipts.size)
        assertEquals("u-1", userStore.receipts.getValue(REQUEST_ID).receiptId) // not replaced

        // Attempt 3: everything already saved, still no duplicates and the same time.
        now += 60_000
        val (c3, u3) = bothSides()
        assertEquals(999L, (c3 as HandshakeResult.Confirmed).confirmedAt)
        assertEquals(999L, (u3 as HandshakeResult.Confirmed).confirmedAt)
        assertEquals(1, collectorStore.receipts.size)
        assertEquals(1, userStore.receipts.size)
    }

    @Test
    fun cancellingClosesTheChannel() = runBlocking {
        val (collectorEnd, fakeUser) = channelPair()
        val c = async { collector().runCollector(collectorEnd, REQUEST_ID, COLLECTOR, now) }
        delay(50)
        c.cancel()
        // The collector's end was closed, so the other side sees the disconnect at once.
        try {
            fakeUser.receive(2_000)
            throw AssertionError("expected a disconnect")
        } catch (expected: java.io.IOException) {
        }
        c.join()
        assertTrue(log.lines.contains("COLLECTOR CANCELLED"))
    }
}
