package ai.getmaxim.sdk.evaluators

import ai.getmaxim.sdk.models.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseEvaluatorTest {

    private fun makePassFailCriteria() = PassFailCriteria(
        onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
        forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
    )

    private fun makeSimpleEvaluator(
        criteria: Map<String, PassFailCriteria> = mapOf("quality" to makePassFailCriteria())
    ): BaseEvaluator {
        return object : BaseEvaluator(criteria) {
            override fun evaluate(
                result: LocalEvaluatorResultParameter,
                data: LocalData
            ): Map<String, LocalEvaluatorReturn> {
                return mapOf("quality" to LocalEvaluatorReturn(score = 0.8, reasoning = "Good"))
            }
        }
    }

    @Nested
    inner class ConstructorTest {
        @Test
        fun `initializes names from passFailCriteria keys`() {
            val evaluator = makeSimpleEvaluator()
            assertEquals(listOf("quality"), evaluator.names)
        }

        @Test
        fun `initializes multiple names`() {
            val criteria = mapOf(
                "relevance" to makePassFailCriteria(),
                "clarity" to makePassFailCriteria(),
                "accuracy" to makePassFailCriteria()
            )
            val evaluator = makeSimpleEvaluator(criteria)
            assertEquals(3, evaluator.names.size)
            assertTrue(evaluator.names.contains("relevance"))
            assertTrue(evaluator.names.contains("clarity"))
            assertTrue(evaluator.names.contains("accuracy"))
        }

        @Test
        fun `stores passFailCriteria`() {
            val criteria = mapOf("test" to makePassFailCriteria())
            val evaluator = makeSimpleEvaluator(criteria)
            assertEquals(1, evaluator.passFailCriteria.size)
            assertEquals(">=", evaluator.passFailCriteria["test"]!!.onEachEntry.scoreShouldBe)
        }

        @Test
        fun `rejects empty passFailCriteria`() {
            assertThrows<IllegalArgumentException> {
                makeSimpleEvaluator(emptyMap())
            }
        }
    }

    @Nested
    inner class EvaluateTest {
        @Test
        fun `evaluate returns expected results`() {
            val evaluator = makeSimpleEvaluator()
            val result = evaluator.evaluate(
                LocalEvaluatorResultParameter(output = "test output"),
                mapOf("input" to "test input")
            )
            assertEquals(0.8, result["quality"]!!.score)
            assertEquals("Good", result["quality"]!!.reasoning)
        }
    }

    @Nested
    inner class GuardedEvaluateTest {
        @Test
        fun `guardedEvaluate returns results for valid names`() {
            val evaluator = makeSimpleEvaluator()
            val result = evaluator.guardedEvaluate(
                LocalEvaluatorResultParameter(output = "test"),
                emptyMap()
            )
            assertEquals(1, result.size)
            assertEquals(0.8, result["quality"]!!.score)
        }

        @Test
        fun `guardedEvaluate throws on unknown names`() {
            val evaluator = object : BaseEvaluator(mapOf("known" to makePassFailCriteria())) {
                override fun evaluate(
                    result: LocalEvaluatorResultParameter,
                    data: LocalData
                ): Map<String, LocalEvaluatorReturn> {
                    return mapOf(
                        "known" to LocalEvaluatorReturn(score = 1),
                        "unknown" to LocalEvaluatorReturn(score = 0) // This name is not registered
                    )
                }
            }
            val exception = assertThrows<IllegalArgumentException> {
                evaluator.guardedEvaluate(
                    LocalEvaluatorResultParameter(output = "test"),
                    emptyMap()
                )
            }
            assertTrue(exception.message!!.contains("unknown"))
        }

        @Test
        fun `guardedEvaluate with multi-criteria evaluator`() {
            val criteria = mapOf(
                "fluency" to makePassFailCriteria(),
                "grammar" to makePassFailCriteria()
            )
            val evaluator = object : BaseEvaluator(criteria) {
                override fun evaluate(
                    result: LocalEvaluatorResultParameter,
                    data: LocalData
                ): Map<String, LocalEvaluatorReturn> {
                    return mapOf(
                        "fluency" to LocalEvaluatorReturn(score = 0.9),
                        "grammar" to LocalEvaluatorReturn(score = 0.7, reasoning = "Minor issues")
                    )
                }
            }
            val result = evaluator.guardedEvaluate(
                LocalEvaluatorResultParameter(output = "test"),
                emptyMap()
            )
            assertEquals(2, result.size)
            assertEquals(0.9, result["fluency"]!!.score)
            assertEquals(0.7, result["grammar"]!!.score)
            assertEquals("Minor issues", result["grammar"]!!.reasoning)
        }
    }

    @Nested
    inner class VariableMappingTest {
        @Test
        fun `evaluator with no variable mapping`() {
            val evaluator = makeSimpleEvaluator()
            assertEquals(null, evaluator.variableMapping)
        }

        @Test
        fun `evaluator with variable mapping`() {
            val mapping: VariableMapping = mapOf(
                "custom_output" to { input, _, _ -> input.data.uppercase() }
            )
            val evaluator = object : BaseEvaluator(
                mapOf("test" to makePassFailCriteria()),
                variableMapping = mapping
            ) {
                override fun evaluate(
                    result: LocalEvaluatorResultParameter,
                    data: LocalData
                ): Map<String, LocalEvaluatorReturn> {
                    return mapOf("test" to LocalEvaluatorReturn(score = 1))
                }
            }

            assertEquals(1, evaluator.variableMapping!!.size)
            val mappingFn = evaluator.variableMapping!!["custom_output"]!!
            val result = mappingFn(
                VariableMappingInput(data = "hello"),
                emptyMap(),
                null
            )
            assertEquals("HELLO", result)
        }
    }
}
