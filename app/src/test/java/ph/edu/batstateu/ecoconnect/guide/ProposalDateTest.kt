package ph.edu.batstateu.ecoconnect.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds
import ph.edu.batstateu.ecoconnect.data.guide.PickupSchedule
import ph.edu.batstateu.ecoconnect.data.guide.Weekday
import ph.edu.batstateu.ecoconnect.data.local.WasteItem

/** Dates a collector may propose follow the request's waste categories. */
class ProposalDateTest {

    private val guide = TestGuide.guide
    private val day = 86_400_000L

    // 2026-11-16 (Monday) 00:00 UTC; the next 7 days cover every weekday.
    private val monday = 1_794_787_200_000L
    private val week = (0 until 7).map { monday + it * day }

    private fun allowed(vararg categories: String): List<Weekday> =
        week.filter { PickupSchedule.isProposalDateAllowed(it, categories.map { c -> WasteItem(c, 1.0) }, guide) }
            .map(Weekday::fromUtcMillis)

    @Test
    fun weekStartsOnMonday() {
        assertEquals(Weekday.MONDAY, Weekday.fromUtcMillis(monday))
    }

    @Test
    fun recyclableOnlyOnItsCollectionDays() {
        val days = guide.category(CategoryIds.RECYCLABLE)!!.collectionDays
        assertTrue(days.isNotEmpty())
        assertEquals(Weekday.entries.filter { it in days }, allowed(CategoryIds.RECYCLABLE))
        assertTrue(Weekday.SATURDAY in allowed(CategoryIds.RECYCLABLE))
        assertFalse(Weekday.MONDAY in allowed(CategoryIds.RECYCLABLE))
    }

    @Test
    fun mixedRequestAllowsTheUnionOfDays() {
        val bio = guide.category(CategoryIds.BIODEGRADABLE)!!.collectionDays
        val rec = guide.category(CategoryIds.RECYCLABLE)!!.collectionDays
        assertEquals(
            Weekday.entries.filter { it in bio || it in rec },
            allowed(CategoryIds.BIODEGRADABLE, CategoryIds.RECYCLABLE),
        )
    }

    @Test
    fun residualAllowsAnyDate() {
        assertEquals(Weekday.entries, allowed(CategoryIds.RESIDUAL))
        assertEquals(Weekday.entries, allowed(CategoryIds.RECYCLABLE, CategoryIds.RESIDUAL))
    }

    @Test
    fun categoryIdsAreCaseInsensitive() {
        assertEquals(allowed(CategoryIds.RECYCLABLE), allowed("recyclable"))
    }

    @Test
    fun unknownOrSpecialOnlyAllowsNothing() {
        assertEquals(emptyList<Weekday>(), allowed("NOT_A_CATEGORY"))
        assertEquals(emptyList<Weekday>(), allowed(CategoryIds.SPECIAL))
    }
}
