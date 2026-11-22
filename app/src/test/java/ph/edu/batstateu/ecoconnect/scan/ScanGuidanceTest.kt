package ph.edu.batstateu.ecoconnect.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds
import ph.edu.batstateu.ecoconnect.data.scan.ScanGuidance
import ph.edu.batstateu.ecoconnect.data.scan.ScanResult
import ph.edu.batstateu.ecoconnect.data.scan.guidanceFor
import ph.edu.batstateu.ecoconnect.guide.TestGuide
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory

/** Scanner result → waste guide advice, using the real bundled guide. */
class ScanGuidanceTest {

    private val guide = TestGuide.guide

    private fun result(category: WasteCategory?, raw: String = category?.id ?: "UNSURE") =
        ScanResult(category, "Item", 0.9, "", null, null, raw)

    @Test
    fun householdCategoriesGetAdviceAndPickup() {
        listOf(WasteCategory.BIODEGRADABLE, WasteCategory.RECYCLABLE, WasteCategory.RESIDUAL).forEach { category ->
            val g = guidanceFor(result(category), guide) as ScanGuidance.Known
            assertEquals(category.id, g.category.id)
            assertTrue("${category.id} pickup", g.canRequestPickup)
            assertTrue(g.category.instructions.isNotEmpty())
        }
    }

    @Test
    fun specialWasteGetsAdviceButNoPickup() {
        val g = guidanceFor(result(WasteCategory.SPECIAL), guide) as ScanGuidance.Known
        assertEquals(CategoryIds.SPECIAL, g.category.id)
        assertFalse(g.canRequestPickup)
    }

    @Test
    fun lowConfidenceKeepsGeminisGuess() {
        val g = guidanceFor(result(null, raw = "RECYCLABLE"), guide) as ScanGuidance.Unsure
        assertEquals(CategoryIds.RECYCLABLE, g.guess?.id)
        assertEquals(guide.unsure, g.advice)
    }

    @Test
    fun unsureOrUnknownHasNoGuess() {
        listOf("UNSURE", "COMPOST", "", " recyclable ").forEach { raw ->
            val g = guidanceFor(result(null, raw = raw), guide) as ScanGuidance.Unsure
            if (raw.trim().equals("recyclable", ignoreCase = true)) {
                assertEquals(CategoryIds.RECYCLABLE, g.guess?.id) // tolerant of case/spaces
            } else {
                assertNull("raw '$raw'", g.guess)
            }
        }
    }
}
