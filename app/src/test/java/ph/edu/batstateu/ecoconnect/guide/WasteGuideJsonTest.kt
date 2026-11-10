package ph.edu.batstateu.ecoconnect.guide

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds
import ph.edu.batstateu.ecoconnect.data.guide.Outcome
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideParser
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import ph.edu.batstateu.ecoconnect.data.guide.Weekday

class WasteGuideJsonTest {

    private val guide = TestGuide.guide

    @Test
    fun `json loads with expected metadata`() {
        assertTrue(TestGuide.file.exists())
        assertEquals("Barangay Alangilan, Batangas City", guide.community)
        assertEquals("No Segregation, No Collection", guide.policy)
        assertNull(guide.reviewedBy)
        assertTrue(guide.generalNote.isNotBlank())
        assertTrue(guide.unsure.instructions.isNotEmpty())
    }

    @Test
    fun `every question and result id is valid`() {
        val errors = WasteGuideParser.validate(guide)
        assertTrue("Validation errors: $errors", errors.isEmpty())
    }

    @Test
    fun `has the four categories with the published rules`() {
        assertEquals(
            listOf(CategoryIds.BIODEGRADABLE, CategoryIds.RECYCLABLE, CategoryIds.RESIDUAL, CategoryIds.SPECIAL),
            guide.categories.map { it.id },
        )
        val bio = guide.category(CategoryIds.BIODEGRADABLE)!!
        assertEquals(listOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY, Weekday.SUNDAY), bio.collectionDays)
        assertTrue(bio.requestable)

        val rec = guide.category(CategoryIds.RECYCLABLE)!!
        assertEquals(listOf(Weekday.TUESDAY, Weekday.THURSDAY, Weekday.SATURDAY), rec.collectionDays)
        assertTrue(rec.requestable)

        val residual = guide.category(CategoryIds.RESIDUAL)!!
        assertTrue(residual.requestable)
        assertTrue(residual.collectionDays.isEmpty())
        assertNotNull(residual.scheduleNote)

        assertFalse(guide.category(CategoryIds.SPECIAL)!!.requestable)
    }

    @Test
    fun `hazardous question is asked first`() {
        val first = guide.question(guide.startQuestionId)!!
        assertEquals(CategoryIds.SPECIAL, first.yes.result)
    }

    @Test
    fun `has at least 25 items covering every category`() {
        assertTrue("only ${guide.items.size} items", guide.items.size >= 25)
        guide.categories.forEach { c ->
            assertTrue("no items for ${c.id}", guide.items.any { it.category == c.id })
        }
        guide.items.forEach { assertTrue("${it.name} has no keywords", it.keywords.isNotEmpty()) }
    }

    @Test
    fun `validator catches broken references`() {
        val broken = guide.copy(
            questions = guide.questions.map {
                if (it.id == "Q2") it.copy(no = Outcome(next = "Q99")) else it
            },
        )
        val errors = WasteGuideParser.validate(broken)
        assertTrue(errors.any { "Q99" in it })
    }

    @Test
    fun `repository loads once and caches`() = runBlocking {
        var opens = 0
        val repo = WasteGuideRepository(
            open = { opens++; TestGuide.file.inputStream() },
            io = Dispatchers.Unconfined,
        )
        val a = repo.guide()
        val b = repo.guide()
        assertSame(a, b)
        assertEquals(1, opens)
    }
}
