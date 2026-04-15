package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilsTest {

    private fun makePassFailCriteria() = PassFailCriteria(
        onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
        forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
    )

    private fun makeBaseEvaluator(vararg names: String): BaseEvaluator {
        val criteria = names.associateWith { makePassFailCriteria() }
        return object : BaseEvaluator(criteria) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                return names.associateWith { LocalEvaluatorReturn(score = 1) }
            }
        }
    }

    // ─── getLocalEvaluatorNameToIdAndPassFailCriteriaMap ─────────────────

    @Nested
    inner class GetLocalEvaluatorMapTest {
        @Test
        fun `returns empty map for no evaluators`() {
            val result = getLocalEvaluatorNameToIdAndPassFailCriteriaMap(emptyList())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns empty map for only string evaluators`() {
            val result = getLocalEvaluatorNameToIdAndPassFailCriteriaMap(listOf("Bias", "Clarity"))
            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns empty map for only PlatformEvaluators`() {
            val result = getLocalEvaluatorNameToIdAndPassFailCriteriaMap(
                listOf(PlatformEvaluator("Bias"), PlatformEvaluator("Clarity"))
            )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `generates IDs for BaseEvaluator names`() {
            val evaluator = makeBaseEvaluator("relevance", "fluency")
            val result = getLocalEvaluatorNameToIdAndPassFailCriteriaMap(listOf(evaluator))

            assertEquals(2, result.size)
            assertNotNull(result["relevance"])
            assertNotNull(result["fluency"])
            assertTrue(result["relevance"]!!.id.isNotBlank())
            assertTrue(result["fluency"]!!.id.isNotBlank())
            // IDs should be different
            assertTrue(result["relevance"]!!.id != result["fluency"]!!.id)
        }

        @Test
        fun `preserves passFailCriteria`() {
            val evaluator = makeBaseEvaluator("quality")
            val result = getLocalEvaluatorNameToIdAndPassFailCriteriaMap(listOf(evaluator))

            val criteria = result["quality"]!!.passFailCriteria
            assertNotNull(criteria)
            assertEquals(">=", criteria.onEachEntry.scoreShouldBe)
            assertEquals(70, criteria.forTestrunOverall.value)
        }

        @Test
        fun `handles mixed evaluator types`() {
            val localEval = makeBaseEvaluator("local1", "local2")
            val result = getLocalEvaluatorNameToIdAndPassFailCriteriaMap(
                listOf("Bias", PlatformEvaluator("Custom"), localEval)
            )

            // Only local evaluator names get IDs
            assertEquals(2, result.size)
            assertNotNull(result["local1"])
            assertNotNull(result["local2"])
            assertNull(result["Bias"])
            assertNull(result["Custom"])
        }
    }

    // ─── getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria ──────────

    @Nested
    inner class GetEvaluatorConfigTest {
        @Test
        fun `creates LOCAL evaluator with correct fields`() {
            val config = getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(
                id = "eval-123",
                name = "my_evaluator",
                passFailCriteria = makePassFailCriteria()
            )

            assertEquals("eval-123", config.id)
            assertEquals("my_evaluator", config.name)
            assertEquals(EvaluatorType.LOCAL, config.type)
            assertEquals(false, config.builtin)
            assertEquals(false, config.reversed)
        }

        @Test
        fun `config has correct passFailCriteria structure`() {
            val config = getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(
                id = "eval-1",
                name = "test",
                passFailCriteria = PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.8),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 90, "average")
                )
            )

            val passFailConfig = config.config!!["passFailCriteria"]!!.jsonObject
            val entryLevel = passFailConfig["entryLevel"]!!.jsonObject
            assertEquals("score", entryLevel["name"]!!.jsonPrimitive.content)
            assertEquals(">=", entryLevel["operator"]!!.jsonPrimitive.content)
            assertEquals(0.8, entryLevel["value"]!!.jsonPrimitive.double)

            val runLevel = passFailConfig["runLevel"]!!.jsonObject
            assertEquals("meanScore", runLevel["name"]!!.jsonPrimitive.content)
            assertEquals(">=", runLevel["operator"]!!.jsonPrimitive.content)
            assertEquals(90, runLevel["value"]!!.jsonPrimitive.int)
        }

        @Test
        fun `config converts boolean entry value to Yes No`() {
            val config = getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(
                id = "eval-1",
                name = "test",
                passFailCriteria = PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry("=", true),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 80, "percentageOfPassedResults")
                )
            )

            val entryLevel = config.config!!["passFailCriteria"]!!.jsonObject["entryLevel"]!!.jsonObject
            assertEquals("Yes", entryLevel["value"]!!.jsonPrimitive.content)

            val runLevel = config.config!!["passFailCriteria"]!!.jsonObject["runLevel"]!!.jsonObject
            assertEquals("queriesPassed", runLevel["name"]!!.jsonPrimitive.content)
        }

        @Test
        fun `config converts false boolean to No`() {
            val config = getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(
                id = "eval-1",
                name = "test",
                passFailCriteria = PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry("=", false),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 50, "average")
                )
            )

            val entryLevel = config.config!!["passFailCriteria"]!!.jsonObject["entryLevel"]!!.jsonObject
            assertEquals("No", entryLevel["value"]!!.jsonPrimitive.content)
        }
    }
}
