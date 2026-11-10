package ph.edu.batstateu.ecoconnect.guide

import org.junit.Assert.assertEquals
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds.BIODEGRADABLE
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds.RECYCLABLE
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds.RESIDUAL
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds.SPECIAL
import ph.edu.batstateu.ecoconnect.data.guide.GuideSearch

/**
 * "Waste guide correctness" metric: what a resident would type -> the bin they should use.
 * The top search result must be in the expected category.
 */
class GuideCorrectnessTest {

    private val samples = listOf(
        "banana peel" to BIODEGRADABLE,
        "tirang pagkain" to BIODEGRADABLE,
        "coffee grounds" to BIODEGRADABLE,
        "tuyong dahon" to BIODEGRADABLE,
        "eggshell" to BIODEGRADABLE,
        "PET bottle" to RECYCLABLE,
        "softdrink can" to RECYCLABLE,
        "dyaryo" to RECYCLABLE,
        "karton" to RECYCLABLE,
        "bote" to RECYCLABLE,
        "sachet" to RESIDUAL,
        "styro" to RESIDUAL,
        "diaper" to RESIDUAL,
        "candy wrapper" to RESIDUAL,
        "pizza box" to RESIDUAL,
        "battery" to SPECIAL,
        "charger" to SPECIAL,
        "cellphone" to SPECIAL,
        "pintura" to SPECIAL,
        "expired medicine" to SPECIAL,
    )

    @Test
    fun `20 sample items map to the expected category`() {
        assertEquals(20, samples.size)
        val failures = samples.mapNotNull { (query, expected) ->
            val actual = GuideSearch.search(TestGuide.guide, query).firstOrNull()?.category?.id
            if (actual == expected) null else "'$query': expected $expected, got $actual"
        }
        val correct = samples.size - failures.size
        println("Waste guide correctness: $correct/${samples.size} (${correct * 100 / samples.size}%)")
        assertEquals("Wrong categories:\n" + failures.joinToString("\n"), emptyList<String>(), failures)
    }
}
