package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class SanitizationUtilsTest {

    private fun makePassFailCriteria() = PassFailCriteria(
        onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
        forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
    )

    // ─── sanitizeData ───────────────────────────────────────────────────

    @Nested
    inner class SanitizeDataTest {
        @Test
        fun `accepts valid INPUT as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("q" to "What is AI?")),
                    mapOf("q" to "INPUT")
                )
            }
        }

        @Test
        fun `rejects INPUT as non-string`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("q" to 123)),
                    mapOf("q" to "INPUT")
                )
            }
        }

        @Test
        fun `accepts null INPUT value`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("q" to null)),
                    mapOf("q" to "INPUT")
                )
            }
        }

        @Test
        fun `accepts valid EXPECTED_OUTPUT as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("a" to "answer")),
                    mapOf("a" to "EXPECTED_OUTPUT")
                )
            }
        }

        @Test
        fun `rejects EXPECTED_OUTPUT as non-string`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("a" to listOf("x", "y"))),
                    mapOf("a" to "EXPECTED_OUTPUT")
                )
            }
        }

        @Test
        fun `accepts valid EXPECTED_STEPS as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("s" to "step1,step2")),
                    mapOf("s" to "EXPECTED_STEPS")
                )
            }
        }

        @Test
        fun `rejects EXPECTED_STEPS as non-string`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("s" to 42)),
                    mapOf("s" to "EXPECTED_STEPS")
                )
            }
        }

        @Test
        fun `accepts valid SCENARIO as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("s" to "test scenario")),
                    mapOf("s" to "SCENARIO")
                )
            }
        }

        @Test
        fun `rejects SCENARIO as non-string`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("s" to true)),
                    mapOf("s" to "SCENARIO")
                )
            }
        }

        @Test
        fun `accepts CONTEXT_TO_EVALUATE as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("c" to "some context")),
                    mapOf("c" to "CONTEXT_TO_EVALUATE")
                )
            }
        }

        @Test
        fun `accepts CONTEXT_TO_EVALUATE as list of strings`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("c" to listOf("ctx1", "ctx2"))),
                    mapOf("c" to "CONTEXT_TO_EVALUATE")
                )
            }
        }

        @Test
        fun `rejects CONTEXT_TO_EVALUATE as list of non-strings`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("c" to listOf(1, 2, 3))),
                    mapOf("c" to "CONTEXT_TO_EVALUATE")
                )
            }
        }

        @Test
        fun `rejects CONTEXT_TO_EVALUATE as integer`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("c" to 42)),
                    mapOf("c" to "CONTEXT_TO_EVALUATE")
                )
            }
        }

        @Test
        fun `accepts VARIABLE as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("v" to "value")),
                    mapOf("v" to "VARIABLE")
                )
            }
        }

        @Test
        fun `accepts VARIABLE as list of strings`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("v" to listOf("a", "b"))),
                    mapOf("v" to "VARIABLE")
                )
            }
        }

        @Test
        fun `rejects VARIABLE as integer`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("v" to 99)),
                    mapOf("v" to "VARIABLE")
                )
            }
        }

        @Test
        fun `accepts NULLABLE_VARIABLE as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("v" to "value")),
                    mapOf("v" to "NULLABLE_VARIABLE")
                )
            }
        }

        @Test
        fun `accepts NULLABLE_VARIABLE as null`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("v" to null)),
                    mapOf("v" to "NULLABLE_VARIABLE")
                )
            }
        }

        @Test
        fun `rejects NULLABLE_VARIABLE as integer`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("v" to 42)),
                    mapOf("v" to "NULLABLE_VARIABLE")
                )
            }
        }

        @Test
        fun `accepts FILE_URL_VARIABLE as string`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(mapOf("f" to "https://example.com/file.pdf")),
                    mapOf("f" to "FILE_URL_VARIABLE")
                )
            }
        }

        @Test
        fun `rejects FILE_URL_VARIABLE as non-string`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("f" to 123)),
                    mapOf("f" to "FILE_URL_VARIABLE")
                )
            }
        }

        @Test
        fun `rejects unknown column type`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("x" to "val")),
                    mapOf("x" to "UNKNOWN_TYPE")
                )
            }
        }

        @Test
        fun `rejects column not in data structure`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(mapOf("unknown_col" to "val")),
                    mapOf("other_col" to "INPUT")
                )
            }
        }

        @Test
        fun `validates multiple entries`() {
            assertDoesNotThrow {
                sanitizeData(
                    listOf(
                        mapOf("q" to "Q1", "a" to "A1"),
                        mapOf("q" to "Q2", "a" to "A2"),
                        mapOf("q" to "Q3", "a" to "A3")
                    ),
                    mapOf("q" to "INPUT", "a" to "EXPECTED_OUTPUT")
                )
            }
        }

        @Test
        fun `fails on any invalid entry in list`() {
            assertThrows<IllegalArgumentException> {
                sanitizeData(
                    listOf(
                        mapOf("q" to "valid"),
                        mapOf("q" to 123) // invalid
                    ),
                    mapOf("q" to "INPUT")
                )
            }
        }

        @Test
        fun `accepts empty data list`() {
            assertDoesNotThrow {
                sanitizeData(emptyList(), mapOf("q" to "INPUT"))
            }
        }
    }

    // ─── sanitizeEvaluators ─────────────────────────────────────────────

    @Nested
    inner class SanitizeEvaluatorsTest {
        @Test
        fun `accepts unique string evaluators`() {
            assertDoesNotThrow {
                sanitizeEvaluators(listOf("Bias", "Clarity", "Relevance"))
            }
        }

        @Test
        fun `rejects duplicate string evaluators`() {
            val ex = assertThrows<IllegalArgumentException> {
                sanitizeEvaluators(listOf("Bias", "Clarity", "Bias"))
            }
            assertTrue(ex.message!!.contains("Bias"))
        }

        @Test
        fun `accepts unique PlatformEvaluators`() {
            assertDoesNotThrow {
                sanitizeEvaluators(listOf(PlatformEvaluator("A"), PlatformEvaluator("B")))
            }
        }

        @Test
        fun `rejects duplicate PlatformEvaluators`() {
            assertThrows<IllegalArgumentException> {
                sanitizeEvaluators(listOf(PlatformEvaluator("A"), PlatformEvaluator("A")))
            }
        }

        @Test
        fun `accepts unique BaseEvaluator names`() {
            val eval = object : BaseEvaluator(
                mapOf("metric1" to makePassFailCriteria(), "metric2" to makePassFailCriteria())
            ) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return emptyMap()
                }
            }
            assertDoesNotThrow {
                sanitizeEvaluators(listOf(eval))
            }
        }

        @Test
        fun `rejects duplicate names across BaseEvaluator and string`() {
            val eval = object : BaseEvaluator(mapOf("Bias" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return emptyMap()
                }
            }
            assertThrows<IllegalArgumentException> {
                sanitizeEvaluators(listOf("Bias", eval))
            }
        }

        @Test
        fun `rejects duplicate names across PlatformEvaluator and string`() {
            assertThrows<IllegalArgumentException> {
                sanitizeEvaluators(listOf("Clarity", PlatformEvaluator("Clarity")))
            }
        }

        @Test
        fun `accepts mixed unique evaluators`() {
            val eval = object : BaseEvaluator(mapOf("local_metric" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return emptyMap()
                }
            }
            assertDoesNotThrow {
                sanitizeEvaluators(listOf("Bias", PlatformEvaluator("Clarity"), eval))
            }
        }

        @Test
        fun `accepts empty evaluator list`() {
            assertDoesNotThrow {
                sanitizeEvaluators(emptyList())
            }
        }
    }
}
