package ph.edu.batstateu.ecoconnect.bluetooth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BtCodecTest {

    private val all = listOf(
        BtMessage.Hello(REQUEST_ID, RESIDENT, 1_795_300_000_000L),
        BtMessage.Hello(REQUEST_ID, RESIDENT, 1_795_300_000_000L, previousConfirmedAt = 1_795_200_000_000L),
        BtMessage.Confirm(REQUEST_ID, COLLECTOR, 1_795_300_000_123L),
        BtMessage.Reject("Request is not ready for pickup (status SENT).\nTry later"),
        BtMessage.Ack(REQUEST_ID),
        BtMessage.Done(REQUEST_ID),
    )

    @Test
    fun everyMessageRoundTrips() {
        all.forEach { assertEquals(it, BtCodec.decode(BtCodec.encode(it))) }
    }

    @Test
    fun encodedMessageIsOneLineWithTypeField() {
        all.forEach { msg ->
            val line = BtCodec.encode(msg)
            assertFalse("newline in $line", '\n' in line)
            assertTrue(line, line.contains("\"type\":"))
        }
        assertEquals(
            """{"type":"HELLO","requestId":"$REQUEST_ID","userId":"$RESIDENT","sentAt":5}""",
            BtCodec.encode(BtMessage.Hello(REQUEST_ID, RESIDENT, 5)),
        )
        assertEquals("""{"type":"ACK","requestId":"x"}""", BtCodec.encode(BtMessage.Ack("x")))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        assertEquals(BtMessage.Done("x"), BtCodec.decode("""{"type":"DONE","requestId":"x","appVersion":7}"""))
    }

    @Test
    fun badInputIsAProtocolError() {
        listOf(
            "",
            "   ",
            "hello",
            "{",
            """{"type":"PING"}""",
            """{"requestId":"x"}""",
            """{"type":"ACK"}""",
            """{"type":"HELLO","requestId":"x","userId":"u","sentAt":"soon"}""",
        ).forEach { line ->
            try {
                BtCodec.decode(line)
                throw AssertionError("decoded: '$line'")
            } catch (expected: BtProtocolException) {
            }
        }
    }

    @Test
    fun referenceCodeIsFirstSixCharsUppercase() {
        assertEquals("EC-3F9A1C", referenceCode(REQUEST_ID))
        assertEquals("EC-AB", referenceCode("ab"))
    }

    @Test
    fun channelFramesMessagesAndSkipsBlankLines() = runBlocking {
        val pipe = MemoryPipe()
        val reader = StreamMessageChannel(pipe.input, MemoryPipe().output)
        pipe.output.write("\n\r\n${BtCodec.encode(BtMessage.Ack("a"))}\n${BtCodec.encode(BtMessage.Done("a"))}\n".toByteArray())
        assertEquals(BtMessage.Ack("a"), reader.receive(1_000))
        assertEquals(BtMessage.Done("a"), reader.receive(1_000))
        reader.close()
    }

    @Test(expected = BtTimeoutException::class)
    fun receiveTimesOut() = runBlocking<Unit> {
        val (a, _) = channelPair()
        a.receive(200)
    }
}
