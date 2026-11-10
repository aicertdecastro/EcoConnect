package ph.edu.batstateu.ecoconnect.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds
import ph.edu.batstateu.ecoconnect.data.guide.PickupSchedule
import ph.edu.batstateu.ecoconnect.data.guide.Weekday

class PickupScheduleTest {

    private val guide = TestGuide.guide
    private val bio = guide.category(CategoryIds.BIODEGRADABLE)!!
    private val rec = guide.category(CategoryIds.RECYCLABLE)!!
    private val residual = guide.category(CategoryIds.RESIDUAL)!!
    private val special = guide.category(CategoryIds.SPECIAL)!!

    @Test
    fun `biodegradable only on its days`() {
        assertTrue(PickupSchedule.isDateAllowed(Weekday.MONDAY, listOf(bio)))
        assertTrue(PickupSchedule.isDateAllowed(Weekday.SUNDAY, listOf(bio)))
        assertFalse(PickupSchedule.isDateAllowed(Weekday.TUESDAY, listOf(bio)))
    }

    @Test
    fun `recyclable only on its days`() {
        assertTrue(PickupSchedule.isDateAllowed(Weekday.SATURDAY, listOf(rec)))
        assertFalse(PickupSchedule.isDateAllowed(Weekday.MONDAY, listOf(rec)))
    }

    @Test
    fun `multiple categories allow the union of days`() {
        Weekday.entries.forEach { day ->
            assertTrue("$day", PickupSchedule.isDateAllowed(day, listOf(bio, rec)))
        }
    }

    @Test
    fun `residual allows any day`() {
        Weekday.entries.forEach { assertTrue(PickupSchedule.isDateAllowed(it, listOf(residual))) }
    }

    @Test
    fun `nothing selected or only special allows no day`() {
        assertFalse(PickupSchedule.isDateAllowed(Weekday.MONDAY, emptyList()))
        Weekday.entries.forEach { assertFalse(PickupSchedule.isDateAllowed(it, listOf(special))) }
    }

    @Test
    fun `coverage splits collected and not collected`() {
        val c = PickupSchedule.coverage(Weekday.MONDAY, listOf(bio, rec, residual))
        assertEquals(listOf(bio, residual), c.collected)
        assertEquals(listOf(rec), c.notCollected)
    }

    @Test
    fun `weekday from DatePicker UTC millis`() {
        // 2026-11-20 00:00 UTC is a Friday; 2026-11-17 is a Tuesday; 2026-11-22 is a Sunday.
        assertEquals(Weekday.FRIDAY, Weekday.fromUtcMillis(1_795_132_800_000L))
        assertEquals(Weekday.TUESDAY, Weekday.fromUtcMillis(1_794_873_600_000L))
        assertEquals(Weekday.SUNDAY, Weekday.fromUtcMillis(1_795_305_600_000L))
    }
}
