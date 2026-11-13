package ph.edu.batstateu.ecoconnect.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.scan.parseClassifyResponse
import ph.edu.batstateu.ecoconnect.data.scan.scaledSize
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory

class ScanMappingTest {

    @Test
    fun validResponseMapsToGuideCategory() {
        val r = parseClassifyResponse(
            """{"category":"RECYCLABLE","itemName":"Plastic bottle","confidence":0.93,"reason":"PET bottle","model":"gemini-3.6-flash","latencyMs":1400}""",
        )
        assertEquals(WasteCategory.RECYCLABLE, r.category)
        assertEquals("Plastic bottle", r.itemName)
        assertEquals(0.93, r.confidence, 1e-9)
        assertEquals(1400L, r.latencyMs)
        assertFalse(r.isUnsure)
    }

    @Test
    fun lowerCaseCategoryWorks() {
        val r = parseClassifyResponse("""{"category":"special","itemName":"AA battery","confidence":0.8}""")
        assertEquals(WasteCategory.SPECIAL, r.category)
    }

    @Test
    fun unknownCategoryIsUnsure() {
        val r = parseClassifyResponse("""{"category":"COMPOST","itemName":"Thing","confidence":0.99}""")
        assertNull(r.category)
        assertTrue(r.isUnsure)
        assertEquals("COMPOST", r.rawCategory)
    }

    @Test
    fun explicitUnsureIsUnsure() {
        assertTrue(parseClassifyResponse("""{"category":"UNSURE","itemName":"Mixed","confidence":0.9}""").isUnsure)
    }

    @Test
    fun lowConfidenceIsUnsureButKeepsGuess() {
        val r = parseClassifyResponse("""{"category":"RECYCLABLE","itemName":"Bottle?","confidence":0.3}""")
        assertTrue(r.isUnsure)
        assertEquals("RECYCLABLE", r.rawCategory)
    }

    @Test
    fun missingFieldsGetSafeDefaults() {
        val r = parseClassifyResponse("""{"confidence":7}""")
        assertTrue(r.isUnsure)
        assertEquals("Unknown item", r.itemName)
        assertEquals(1.0, r.confidence, 1e-9)
        assertEquals("UNSURE", r.rawCategory)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedJsonThrows() {
        parseClassifyResponse("<html>Bad gateway</html>")
    }

    @Test
    fun downscaleKeepsLongestSideAtMost1024() {
        assertEquals(1024 to 768, scaledSize(4032, 3024))
        assertEquals(576 to 1024, scaledSize(2160, 3840))
        assertEquals(800 to 600, scaledSize(800, 600)) // small images untouched
        val (w, h) = scaledSize(10_000, 3)
        assertTrue(w <= 1024 && h >= 1)
    }
}
