package ph.edu.batstateu.ecoconnect.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.guide.Answer
import ph.edu.batstateu.ecoconnect.data.guide.Answer.NO
import ph.edu.batstateu.ecoconnect.data.guide.Answer.UNSURE
import ph.edu.batstateu.ecoconnect.data.guide.Answer.YES
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds
import ph.edu.batstateu.ecoconnect.data.guide.GuideEngine
import ph.edu.batstateu.ecoconnect.data.guide.GuideResult
import ph.edu.batstateu.ecoconnect.data.guide.GuideStep

class GuideEngineTest {

    private fun engine() = GuideEngine(TestGuide.guide)

    private fun GuideEngine.run(vararg answers: Answer): GuideStep {
        answers.forEach { answer(it) }
        return current
    }

    private fun GuideStep.categoryId(): String = when (this) {
        is GuideStep.Done -> when (val r = result) {
            is GuideResult.Category -> r.category.id
            is GuideResult.Unsure -> CategoryIds.UNSURE
        }
        is GuideStep.Ask -> error("Expected a result but still asking ${question.id}")
    }

    private fun GuideStep.questionId(): String = (this as GuideStep.Ask).question.id

    // ---- every path ----

    @Test fun `Q1 yes is SPECIAL`() = assertEquals(CategoryIds.SPECIAL, engine().run(YES).categoryId())

    @Test fun `Q2 yes is BIODEGRADABLE`() = assertEquals(CategoryIds.BIODEGRADABLE, engine().run(NO, YES).categoryId())

    @Test fun `Q3 no is RESIDUAL`() = assertEquals(CategoryIds.RESIDUAL, engine().run(NO, NO, NO).categoryId())

    @Test fun `Q4 yes is RECYCLABLE`() = assertEquals(CategoryIds.RECYCLABLE, engine().run(NO, NO, YES, YES).categoryId())

    @Test fun `Q4 no is RESIDUAL`() = assertEquals(CategoryIds.RESIDUAL, engine().run(NO, NO, YES, NO).categoryId())

    // ---- UNSURE at every question ----

    @Test fun `unsure at Q1`() = assertEquals(CategoryIds.UNSURE, engine().run(UNSURE).categoryId())

    @Test fun `unsure at Q2`() = assertEquals(CategoryIds.UNSURE, engine().run(NO, UNSURE).categoryId())

    @Test fun `unsure at Q3`() = assertEquals(CategoryIds.UNSURE, engine().run(NO, NO, UNSURE).categoryId())

    @Test fun `unsure at Q4`() = assertEquals(CategoryIds.UNSURE, engine().run(NO, NO, YES, UNSURE).categoryId())

    @Test
    fun `unsure result carries the advice`() {
        val step = engine().run(UNSURE) as GuideStep.Done
        val result = step.result as GuideResult.Unsure
        assertEquals(TestGuide.guide.unsure, result.advice)
    }

    // ---- numbering ----

    @Test
    fun `questions are numbered out of up to 4`() {
        val e = engine()
        assertEquals(4, e.maxQuestions)
        listOf("Q1", "Q2", "Q3", "Q4").forEachIndexed { i, id ->
            val ask = e.current as GuideStep.Ask
            assertEquals(id, ask.question.id)
            assertEquals(i + 1, ask.number)
            assertEquals(4, ask.maxQuestions)
            if (id == "Q3") e.answer(YES) else if (id != "Q4") e.answer(NO)
        }
    }

    // ---- back / restart ----

    @Test
    fun `back at first question does nothing`() {
        val e = engine()
        assertFalse(e.canGoBack)
        assertFalse(e.back())
        assertEquals("Q1", e.current.questionId())
    }

    @Test
    fun `back from Q2 returns to Q1`() {
        val e = engine()
        e.answer(NO)
        assertTrue(e.back())
        assertEquals("Q1", e.current.questionId())
    }

    @Test
    fun `back from a result returns to its last question`() {
        val e = engine()
        e.run(NO, NO, YES, NO)
        assertTrue(e.canGoBack)
        assertTrue(e.back())
        assertEquals("Q4", e.current.questionId())
        assertEquals(4, (e.current as GuideStep.Ask).number)
    }

    @Test
    fun `back from unsure returns to the question`() {
        val e = engine()
        e.run(NO, UNSURE)
        assertTrue(e.back())
        assertEquals("Q2", e.current.questionId())
    }

    @Test
    fun `can change an answer after going back`() {
        val e = engine()
        e.run(NO, NO, YES, NO) // residual
        e.back()               // Q4
        assertEquals(CategoryIds.RECYCLABLE, e.run(YES).categoryId())
    }

    @Test
    fun `back several steps then take a different branch`() {
        val e = engine()
        e.run(NO, NO, YES) // at Q4
        e.back()           // Q3
        e.back()           // Q2
        assertEquals(CategoryIds.BIODEGRADABLE, e.run(YES).categoryId())
    }

    @Test
    fun `restart returns to Q1`() {
        val e = engine()
        e.run(NO, NO, YES, YES)
        val step = e.restart()
        assertEquals("Q1", step.questionId())
        assertEquals(1, (step as GuideStep.Ask).number)
        assertFalse(e.canGoBack)
    }

    @Test(expected = IllegalStateException::class)
    fun `answering after a result is an error`() {
        engine().run(YES, YES)
    }
}
