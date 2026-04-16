package ai.getmaxim.sdk.models

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvaluatorModelsTest {

    // ─── EvaluatorType ──────────────────────────────────────────────────

    @Nested
    inner class EvaluatorTypeTest {
        @Test
        fun `fromValue returns correct enum for all types`() {
            assertEquals(EvaluatorType.AI, EvaluatorType.fromValue("AI"))
            assertEquals(EvaluatorType.PROGRAMMATIC, EvaluatorType.fromValue("Programmatic"))
            assertEquals(EvaluatorType.STATISTICAL, EvaluatorType.fromValue("Statistical"))
            assertEquals(EvaluatorType.API, EvaluatorType.fromValue("API"))
            assertEquals(EvaluatorType.HUMAN, EvaluatorType.fromValue("Human"))
            assertEquals(EvaluatorType.LOCAL, EvaluatorType.fromValue("Local"))
        }

        @Test
        fun `fromValue throws on unknown type`() {
            assertThrows<IllegalArgumentException> {
                EvaluatorType.fromValue("Unknown")
            }
        }

        @Test
        fun `value property returns correct string`() {
            assertEquals("AI", EvaluatorType.AI.value)
            assertEquals("Programmatic", EvaluatorType.PROGRAMMATIC.value)
            assertEquals("Local", EvaluatorType.LOCAL.value)
        }
    }

    // ─── Evaluator ──────────────────────────────────────────────────────

    @Nested
    inner class EvaluatorTest {
        @Test
        fun `toJsonObject with all fields`() {
            val evaluator = Evaluator(
                id = "eval-1",
                name = "Bias",
                type = EvaluatorType.AI,
                builtin = true,
                reversed = false,
                config = buildJsonObject { put("key", "value") },
                meta = buildJsonObject { put("version", 2) }
            )
            val json = evaluator.toJsonObject()

            assertEquals("eval-1", json["id"]!!.jsonPrimitive.content)
            assertEquals("Bias", json["name"]!!.jsonPrimitive.content)
            assertEquals("AI", json["type"]!!.jsonPrimitive.content)
            assertEquals(true, json["builtin"]!!.jsonPrimitive.boolean)
            assertEquals(false, json["reversed"]!!.jsonPrimitive.boolean)
            assertEquals("value", json["config"]!!.jsonObject["key"]!!.jsonPrimitive.content)
            assertEquals(2, json["meta"]!!.jsonObject["version"]!!.jsonPrimitive.int)
        }

        @Test
        fun `toJsonObject omits null config and meta`() {
            val evaluator = Evaluator(
                id = "eval-1",
                name = "Test",
                type = EvaluatorType.LOCAL,
                builtin = false
            )
            val json = evaluator.toJsonObject()
            assertNull(json["config"])
            assertNull(json["meta"])
        }

        @Test
        fun `fromJsonObject roundtrip`() {
            val original = Evaluator(
                id = "eval-1",
                name = "Clarity",
                type = EvaluatorType.STATISTICAL,
                builtin = false,
                reversed = true,
                config = buildJsonObject { put("threshold", 0.8) }
            )
            val parsed = Evaluator.fromJsonObject(original.toJsonObject())

            assertEquals(original.id, parsed.id)
            assertEquals(original.name, parsed.name)
            assertEquals(original.type, parsed.type)
            assertEquals(original.builtin, parsed.builtin)
            assertEquals(original.reversed, parsed.reversed)
            assertNotNull(parsed.config)
        }
    }

    // ─── LocalEvaluatorReturn ───────────────────────────────────────────

    @Nested
    inner class LocalEvaluatorReturnTest {
        @Test
        fun `toJsonObject with int score`() {
            val ret = LocalEvaluatorReturn(score = 5, reasoning = "Good")
            val json = ret.toJsonObject()
            assertEquals(5, json["score"]!!.jsonPrimitive.int)
            assertEquals("Good", json["reasoning"]!!.jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject with boolean score`() {
            val ret = LocalEvaluatorReturn(score = true)
            val json = ret.toJsonObject()
            assertEquals(true, json["score"]!!.jsonPrimitive.boolean)
            assertNull(json["reasoning"])
        }

        @Test
        fun `toJsonObject with string score`() {
            val ret = LocalEvaluatorReturn(score = "Err", reasoning = "Failed to evaluate")
            val json = ret.toJsonObject()
            assertEquals("Err", json["score"]!!.jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject with double score`() {
            val ret = LocalEvaluatorReturn(score = 0.85)
            val json = ret.toJsonObject()
            assertEquals(0.85, json["score"]!!.jsonPrimitive.double)
        }
    }

    // ─── PassFailCriteria ───────────────────────────────────────────────

    @Nested
    inner class PassFailCriteriaTest {
        @Test
        fun `toJsonObject produces correct structure`() {
            val criteria = PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
            )
            val json = criteria.toJsonObject()

            val onEach = json["onEachEntry"]!!.jsonObject
            assertEquals(">=", onEach["scoreShouldBe"]!!.jsonPrimitive.content)
            assertEquals(0.5, onEach["value"]!!.jsonPrimitive.double)

            val overall = json["forTestrunOverall"]!!.jsonObject
            assertEquals(">=", overall["overallShouldBe"]!!.jsonPrimitive.content)
            assertEquals(70, overall["value"]!!.jsonPrimitive.int)
            assertEquals("average", overall["for"]!!.jsonPrimitive.content)
        }

        @Test
        fun `roundtrip serialization`() {
            val original = PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.8),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 90, "percentageOfPassedResults")
            )
            val parsed = PassFailCriteria.fromJsonObject(original.toJsonObject())

            assertEquals(original.onEachEntry.scoreShouldBe, parsed.onEachEntry.scoreShouldBe)
            assertEquals(original.forTestrunOverall.forResult, parsed.forTestrunOverall.forResult)
            assertEquals(original.forTestrunOverall.value, parsed.forTestrunOverall.value)
        }

        @Test
        fun `PassFailCriteriaOnEachEntry with boolean value`() {
            val criteria = PassFailCriteriaOnEachEntry("=", true)
            val json = criteria.toJsonObject()
            assertEquals(true, json["value"]!!.jsonPrimitive.boolean)
        }

        @Test
        fun `PassFailCriteriaOnEachEntry with null value`() {
            val criteria = PassFailCriteriaOnEachEntry(">=", null)
            val json = criteria.toJsonObject()
            assertTrue(json["value"] is JsonNull)
        }

        @Test
        fun `PassFailCriteriaForTestrunOverall rejects invalid forResult`() {
            assertThrows<IllegalArgumentException> {
                PassFailCriteriaForTestrunOverall(">=", 50, "invalid")
            }
        }

        @Test
        fun `PassFailCriteriaForTestrunOverall accepts average`() {
            val criteria = PassFailCriteriaForTestrunOverall(">=", 50, "average")
            assertEquals("average", criteria.forResult)
        }

        @Test
        fun `PassFailCriteriaForTestrunOverall accepts percentageOfPassedResults`() {
            val criteria = PassFailCriteriaForTestrunOverall(">=", 80, "percentageOfPassedResults")
            assertEquals("percentageOfPassedResults", criteria.forResult)
        }
    }

    // ─── LocalEvaluationResultWithId ────────────────────────────────────

    @Nested
    inner class LocalEvaluationResultWithIdTest {
        @Test
        fun `toJsonObject produces correct structure`() {
            val result = LocalEvaluationResultWithId(
                id = "leval-1",
                result = LocalEvaluatorReturn(score = 0.9, reasoning = "Excellent"),
                name = "relevance",
                passFailCriteria = PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
                ),
                output = "some output"
            )
            val json = result.toJsonObject()

            assertEquals("leval-1", json["id"]!!.jsonPrimitive.content)
            assertEquals("relevance", json["name"]!!.jsonPrimitive.content)
            assertEquals("some output", json["output"]!!.jsonPrimitive.content)
            assertEquals(0.9, json["result"]!!.jsonObject["score"]!!.jsonPrimitive.double)
            assertEquals("Excellent", json["result"]!!.jsonObject["reasoning"]!!.jsonPrimitive.content)
            assertNotNull(json["passFailCriteria"])
        }
    }

    // ─── VariableMappingInput ───────────────────────────────────────────

    @Nested
    inner class VariableMappingInputTest {
        @Test
        fun `creates with all fields`() {
            val input = VariableMappingInput(
                data = "test output",
                retrievedContextToEvaluate = "some context",
                messages = listOf("msg1", "msg2"),
                meta = mapOf("key" to "val"),
                extra = mapOf("extra" to "data")
            )
            assertEquals("test output", input.data)
            assertEquals("some context", input.retrievedContextToEvaluate)
            assertEquals(2, input.messages!!.size)
        }

        @Test
        fun `creates with minimal fields`() {
            val input = VariableMappingInput(data = "output")
            assertEquals("output", input.data)
            assertNull(input.retrievedContextToEvaluate)
            assertNull(input.messages)
            assertNull(input.meta)
            assertNull(input.extra)
        }
    }

    // ─── VersionInfo ────────────────────────────────────────────────────

    @Nested
    inner class VersionInfoTest {
        @Test
        fun `creates workflow version info`() {
            val info = VersionInfo(id = "wf-1", type = "workflow")
            assertEquals("wf-1", info.id)
            assertEquals("workflow", info.type)
        }

        @Test
        fun `creates prompt version info`() {
            val info = VersionInfo(id = "pv-1", type = "prompt")
            assertEquals("prompt", info.type)
        }

        @Test
        fun `creates promptChain version info`() {
            val info = VersionInfo(id = "pcv-1", type = "promptChain")
            assertEquals("promptChain", info.type)
        }
    }

    // ─── PlatformEvaluator ──────────────────────────────────────────────

    @Nested
    inner class PlatformEvaluatorTest {
        @Test
        fun `creates without variable mapping`() {
            val eval = PlatformEvaluator(name = "Bias")
            assertEquals("Bias", eval.name)
            assertNull(eval.variableMapping)
        }

        @Test
        fun `creates with variable mapping`() {
            val mapping: VariableMapping = mapOf(
                "custom_input" to { input, _, _ -> input.data }
            )
            val eval = PlatformEvaluator(name = "CustomEval", variableMapping = mapping)
            assertEquals("CustomEval", eval.name)
            assertNotNull(eval.variableMapping)
            assertEquals(1, eval.variableMapping!!.size)

            // Test the mapping function
            val testInput = VariableMappingInput(data = "hello world")
            val result = eval.variableMapping!!["custom_input"]!!(testInput, emptyMap(), null)
            assertEquals("hello world", result)
        }
    }
}
