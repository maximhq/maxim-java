package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunUtilsTest {

    // ─── getVariablesFromRow ────────────────────────────────────────────

    @Nested
    inner class GetVariablesFromRowTest {
        @Test
        fun `extracts VARIABLE columns as text`() {
            val row = mapOf<String, Any?>("name" to "Alice", "age" to "30")
            val structure = mapOf("name" to "VARIABLE", "age" to "VARIABLE")
            val vars = getVariablesFromRow(row, structure)

            assertEquals(2, vars.size)
            assertEquals("text", vars["name"]!!.type)
            assertEquals("Alice", vars["name"]!!.payload)
            assertEquals("text", vars["age"]!!.type)
            assertEquals("30", vars["age"]!!.payload)
        }

        @Test
        fun `extracts NULLABLE_VARIABLE skipping nulls`() {
            val row = mapOf<String, Any?>("name" to "Bob", "nickname" to null)
            val structure = mapOf("name" to "VARIABLE", "nickname" to "NULLABLE_VARIABLE")
            val vars = getVariablesFromRow(row, structure)

            assertEquals(1, vars.size) // nickname skipped because null
            assertEquals("Bob", vars["name"]!!.payload)
        }

        @Test
        fun `extracts FILE_URL_VARIABLE as file type`() {
            val row = mapOf<String, Any?>("doc" to "https://example.com/file.pdf")
            val structure = mapOf("doc" to "FILE_URL_VARIABLE")
            val vars = getVariablesFromRow(row, structure)

            assertEquals(1, vars.size)
            assertEquals("file", vars["doc"]!!.type)
            val payload = vars["doc"]!!.payload
            assertTrue(payload is Map<*, *>)
        }

        @Test
        fun `skips FILE_URL_VARIABLE with null value`() {
            val row = mapOf<String, Any?>("doc" to null)
            val structure = mapOf("doc" to "FILE_URL_VARIABLE")
            val vars = getVariablesFromRow(row, structure)
            assertTrue(vars.isEmpty())
        }

        @Test
        fun `skips FILE_URL_VARIABLE with empty string`() {
            val row = mapOf<String, Any?>("doc" to "  ")
            val structure = mapOf("doc" to "FILE_URL_VARIABLE")
            val vars = getVariablesFromRow(row, structure)
            assertTrue(vars.isEmpty())
        }

        @Test
        fun `VARIABLE with null value defaults to empty string`() {
            val row = mapOf<String, Any?>("x" to null)
            val structure = mapOf("x" to "VARIABLE")
            val vars = getVariablesFromRow(row, structure)

            assertEquals("text", vars["x"]!!.type)
            assertEquals("", vars["x"]!!.payload)
        }

        @Test
        fun `ignores INPUT EXPECTED_OUTPUT and other special columns`() {
            val row = mapOf<String, Any?>(
                "input" to "question",
                "expected" to "answer",
                "ctx" to "context",
                "var1" to "value1"
            )
            val structure = mapOf(
                "input" to "INPUT",
                "expected" to "EXPECTED_OUTPUT",
                "ctx" to "CONTEXT_TO_EVALUATE",
                "var1" to "VARIABLE"
            )
            val vars = getVariablesFromRow(row, structure)

            assertEquals(1, vars.size) // Only var1
            assertNotNull(vars["var1"])
            assertNull(vars["input"])
        }

        @Test
        fun `handles empty row and structure`() {
            val vars = getVariablesFromRow(emptyMap(), emptyMap())
            assertTrue(vars.isEmpty())
        }
    }

    // ─── getInputExpectedOutputAndContextFromRow ────────────────────────

    @Nested
    inner class GetInputExpectedOutputTest {
        @Test
        fun `extracts all fields when present`() {
            val row = mapOf<String, Any?>(
                "input" to "What is AI?",
                "expected" to "Artificial Intelligence",
                "context" to "Technology",
                "scenario" to "General knowledge",
                "steps" to "1. Define 2. Explain"
            )
            val fields = getInputExpectedOutputAndContextFromRow(
                "input", "expected", "context", "scenario", "steps", row
            )

            assertEquals("What is AI?", fields.input)
            assertEquals("Artificial Intelligence", fields.expectedOutput)
            assertEquals("Technology", fields.contextToEvaluate)
            assertEquals("General knowledge", fields.scenario)
            assertEquals("1. Define 2. Explain", fields.expectedSteps)
        }

        @Test
        fun `returns null for missing keys`() {
            val row = mapOf<String, Any?>("input" to "test")
            val fields = getInputExpectedOutputAndContextFromRow(
                "input", "missing_key", null, null, null, row
            )

            assertEquals("test", fields.input)
            assertNull(fields.expectedOutput) // key not in row
            assertNull(fields.contextToEvaluate) // null key
            assertNull(fields.scenario)
            assertNull(fields.expectedSteps)
        }

        @Test
        fun `returns null for all null keys`() {
            val row = mapOf<String, Any?>("input" to "test")
            val fields = getInputExpectedOutputAndContextFromRow(null, null, null, null, null, row)

            assertNull(fields.input)
            assertNull(fields.expectedOutput)
            assertNull(fields.contextToEvaluate)
            assertNull(fields.scenario)
            assertNull(fields.expectedSteps)
        }

        @Test
        fun `handles null values in row`() {
            val row = mapOf<String, Any?>("input" to null)
            val fields = getInputExpectedOutputAndContextFromRow("input", null, null, null, null, row)
            assertNull(fields.input)
        }
    }

    // ─── getAllKeysByValue ───────────────────────────────────────────────

    @Nested
    inner class GetAllKeysByValueTest {
        @Test
        fun `returns matching keys`() {
            val map = mapOf("a" to "INPUT", "b" to "VARIABLE", "c" to "INPUT")
            val keys = getAllKeysByValue(map, "INPUT")
            assertEquals(2, keys.size)
            assertTrue(keys.contains("a"))
            assertTrue(keys.contains("c"))
        }

        @Test
        fun `returns empty list for no matches`() {
            val map = mapOf("a" to "INPUT")
            val keys = getAllKeysByValue(map, "OUTPUT")
            assertTrue(keys.isEmpty())
        }

        @Test
        fun `returns empty list for null map`() {
            val keys = getAllKeysByValue(null, "INPUT")
            assertTrue(keys.isEmpty())
        }

        @Test
        fun `returns empty list for empty map`() {
            val keys = getAllKeysByValue(emptyMap(), "INPUT")
            assertTrue(keys.isEmpty())
        }
    }

    // ─── runLocalEvaluations ────────────────────────────────────────────

    @Nested
    inner class RunLocalEvaluationsTest {
        private fun makePassFailCriteria() = PassFailCriteria(
            onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
            forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
        )

        @Test
        fun `runs single evaluator`() = runBlocking {
            val evaluator = object : BaseEvaluator(mapOf("quality" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return mapOf("quality" to LocalEvaluatorReturn(score = 0.9, reasoning = "Good"))
                }
            }

            val results = runLocalEvaluations(
                listOf(evaluator),
                mapOf("input" to "test"),
                LocalEvaluatorResultParameter(output = "output")
            )

            assertEquals(1, results.size)
            assertEquals("quality", results[0].name)
            assertEquals(0.9, results[0].result.score)
            assertEquals("Good", results[0].result.reasoning)
        }

        @Test
        fun `runs multiple evaluators in parallel`() = runBlocking {
            val eval1 = object : BaseEvaluator(mapOf("fluency" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return mapOf("fluency" to LocalEvaluatorReturn(score = 0.8))
                }
            }
            val eval2 = object : BaseEvaluator(mapOf("relevance" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return mapOf("relevance" to LocalEvaluatorReturn(score = 0.95))
                }
            }

            val results = runLocalEvaluations(
                listOf(eval1, eval2),
                emptyMap(),
                LocalEvaluatorResultParameter(output = "test")
            )

            assertEquals(2, results.size)
            val names = results.map { it.name }.toSet()
            assertTrue(names.contains("fluency"))
            assertTrue(names.contains("relevance"))
        }

        @Test
        fun `handles evaluator with multiple criteria`() = runBlocking {
            val evaluator = object : BaseEvaluator(
                mapOf("grammar" to makePassFailCriteria(), "spelling" to makePassFailCriteria())
            ) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return mapOf(
                        "grammar" to LocalEvaluatorReturn(score = 0.7),
                        "spelling" to LocalEvaluatorReturn(score = 0.9)
                    )
                }
            }

            val results = runLocalEvaluations(
                listOf(evaluator),
                emptyMap(),
                LocalEvaluatorResultParameter(output = "test")
            )

            assertEquals(2, results.size)
        }

        @Test
        fun `handles evaluator that throws exception`() = runBlocking {
            val evaluator = object : BaseEvaluator(mapOf("broken" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    throw RuntimeException("Evaluation failed!")
                }
            }

            val results = runLocalEvaluations(
                listOf(evaluator),
                emptyMap(),
                LocalEvaluatorResultParameter(output = "test")
            )

            assertEquals(1, results.size)
            assertEquals("broken", results[0].name)
            assertEquals("Err", results[0].result.score)
            assertTrue(results[0].result.reasoning!!.contains("Evaluation failed!"))
        }

        @Test
        fun `returns empty list for no evaluators`() = runBlocking {
            val results = runLocalEvaluations(
                emptyList(),
                emptyMap(),
                LocalEvaluatorResultParameter(output = "test")
            )
            assertTrue(results.isEmpty())
        }

        @Test
        fun `passes data entry to evaluator`() = runBlocking {
            var receivedData: LocalData? = null
            val evaluator = object : BaseEvaluator(mapOf("test" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    receivedData = data
                    return mapOf("test" to LocalEvaluatorReturn(score = 1))
                }
            }

            val dataEntry = mapOf<String, Any?>("key1" to "val1", "key2" to 42)
            runLocalEvaluations(
                listOf(evaluator),
                dataEntry,
                LocalEvaluatorResultParameter(output = "test")
            )

            assertNotNull(receivedData)
            assertEquals("val1", receivedData!!["key1"])
            assertEquals(42, receivedData!!["key2"])
        }
    }
}
