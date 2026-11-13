package ph.edu.batstateu.ecoconnect.scan

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.scan.ClassifierApi
import ph.edu.batstateu.ecoconnect.data.scan.ClassifierHttpException
import ph.edu.batstateu.ecoconnect.data.scan.ScanOutcome
import ph.edu.batstateu.ecoconnect.data.scan.ScanRepository
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import java.io.IOException
import java.util.Base64

class ScanRepositoryTest {

    private class Net(var online: Boolean = true) : NetworkMonitor {
        override fun isOnline() = online
        override fun observeOnline(): Flow<Boolean> = flowOf(online)
    }

    private var calls = 0
    private var sentImage: String? = null
    private var sentMime: String? = null

    private fun repo(net: Net = Net(), reply: suspend () -> String): ScanRepository {
        var now = 0L
        return ScanRepository(
            api = ClassifierApi { image, mime ->
                calls++
                sentImage = image
                sentMime = mime
                reply()
            },
            network = net,
            encodeBase64 = { Base64.getEncoder().encodeToString(it) },
            clock = { now.also { now += 250 } },
        )
    }

    private val jpeg = byteArrayOf(1, 2, 3)

    @Test
    fun offlineMakesNoCall() = runTest {
        val outcome = repo(Net(online = false)) { error("must not be called") }.classify(jpeg)
        assertEquals(ScanOutcome.Offline, outcome)
        assertEquals(0, calls)
    }

    @Test
    fun successSendsBase64JpegAndParses() = runTest {
        val outcome = repo { """{"category":"BIODEGRADABLE","itemName":"Banana peel","confidence":0.97,"reason":"Food scrap"}""" }
            .classify(jpeg)
        assertTrue(outcome is ScanOutcome.Success)
        outcome as ScanOutcome.Success
        assertEquals(WasteCategory.BIODEGRADABLE, outcome.result.category)
        assertEquals(250L, outcome.roundTripMs)
        assertEquals("AQID", sentImage)
        assertEquals("image/jpeg", sentMime)
    }

    @Test
    fun httpErrorsBecomeFriendlyMessages() = runTest {
        val expected = mapOf(
            401 to "Sign in again",
            404 to "not deployed",
            429 to "Too many scans",
            500 to "missing Gemini key",
            502 to "error 502",
        )
        expected.forEach { (status, text) ->
            val outcome = repo { throw ClassifierHttpException(status, "boom") }.classify(jpeg)
            assertTrue("$status → $outcome", outcome is ScanOutcome.Error && text in outcome.message)
        }
    }

    @Test
    fun networkFailureIsAnErrorNotACrash() = runTest {
        val outcome = repo { throw IOException("reset") }.classify(jpeg)
        assertTrue(outcome is ScanOutcome.Error)
    }

    @Test
    fun unreadableReplyIsAnError() = runTest {
        val outcome = repo { "not json" }.classify(jpeg)
        assertTrue(outcome is ScanOutcome.Error && "could not be read" in outcome.message)
    }

    @Test
    fun emptyPhotoIsRejectedLocally() = runTest {
        val outcome = repo { error("must not be called") }.classify(ByteArray(0))
        assertTrue(outcome is ScanOutcome.Error)
        assertEquals(0, calls)
    }
}
