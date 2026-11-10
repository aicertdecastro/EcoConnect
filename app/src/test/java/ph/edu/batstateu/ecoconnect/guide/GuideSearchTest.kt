package ph.edu.batstateu.ecoconnect.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds
import ph.edu.batstateu.ecoconnect.data.guide.GuideSearch

class GuideSearchTest {

    private val guide = TestGuide.guide
    private fun search(q: String) = GuideSearch.search(guide, q)

    @Test
    fun `blank query returns nothing`() {
        assertTrue(search("").isEmpty())
        assertTrue(search("   ").isEmpty())
    }

    @Test
    fun `search is case insensitive`() {
        assertEquals(search("sachet").map { it.item.name }, search("SACHET").map { it.item.name })
        assertEquals("Sachets", search("SaChEt").first().item.name)
    }

    @Test
    fun `partial match on item name`() {
        assertTrue(search("styro").any { it.item.name == "Styrofoam" })
        assertTrue(search("cardb").any { it.item.name == "Cardboard boxes" })
    }

    @Test
    fun `matches tagalog keywords`() {
        assertEquals("Banana peel", search("saging").first().item.name)
        assertEquals("Batteries", search("baterya").first().item.name)
        assertEquals("Newspapers", search("dyaryo").first().item.name)
    }

    @Test
    fun `query is trimmed`() {
        assertEquals("Disposable diapers", search("  lampin ").first().item.name)
    }

    @Test
    fun `whole word beats a prefix of another word`() {
        // "can" must find cans before "Candy and chip wrappers".
        assertEquals(CategoryIds.RECYCLABLE, search("can").first().category.id)
    }

    @Test
    fun `exact keyword ranks first`() {
        // "kape" is a keyword of coffee grounds and part of "kape sachet".
        assertEquals("Coffee grounds", search("kape").first().item.name)
    }

    @Test
    fun `multi word query matches words in any position`() {
        assertEquals("Candy and chip wrappers", search("candy wrapper").first().item.name)
        assertEquals("Candy and chip wrappers", search("chip candy").first().item.name)
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(search("zzzz-not-an-item").isEmpty())
    }

    @Test
    fun `limit is respected`() {
        assertTrue(GuideSearch.search(guide, "a", limit = 3).size <= 3)
    }

    @Test
    fun `hit carries its category`() {
        val hit = search("syringe").first()
        assertEquals(CategoryIds.SPECIAL, hit.category.id)
        assertEquals(hit.item.category, hit.category.id)
    }
}
