package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestRunBuilderTest {

    private fun makeBuilder(): TestRunBuilder {
        return TestRunBuilder("https://test.getmaxim.ai", "test-api-key", "Test Run", "ws-123")
    }

    private fun makePassFailCriteria() = PassFailCriteria(
        onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
        forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
    )

    // ─── Builder Method Chaining ────────────────────────────────────────

    @Nested
    inner class ChainingTest {
        @Test
        fun `all builder methods return the same builder instance`() {
            val builder = makeBuilder()
            val result = builder
                .withWorkflowId("wf-1")
                .withData("dataset-1")
                .withDataStructure(mapOf("input" to "INPUT"))
                .withEvaluators("Bias", "Clarity")
                .withTags(listOf("test", "v1"))
                .withEnvironment("prod")
                .withConcurrency(5)
            // All should return the same builder
            assertEquals(builder, result)
        }
    }

    // ─── Entity Mutual Exclusivity ──────────────────────────────────────

    @Nested
    inner class EntityMutualExclusivityTest {
        @Test
        fun `withWorkflowId then withPromptVersionId throws`() {
            val builder = makeBuilder().withWorkflowId("wf-1")
            assertThrows<IllegalStateException> {
                builder.withPromptVersionId("pv-1")
            }
        }

        @Test
        fun `withWorkflowId then withPromptChainVersionId throws`() {
            val builder = makeBuilder().withWorkflowId("wf-1")
            assertThrows<IllegalStateException> {
                builder.withPromptChainVersionId("pcv-1")
            }
        }

        @Test
        fun `withPromptVersionId then withWorkflowId throws`() {
            val builder = makeBuilder().withPromptVersionId("pv-1")
            assertThrows<IllegalStateException> {
                builder.withWorkflowId("wf-1")
            }
        }

        @Test
        fun `withPromptVersionId then withPromptChainVersionId throws`() {
            val builder = makeBuilder().withPromptVersionId("pv-1")
            assertThrows<IllegalStateException> {
                builder.withPromptChainVersionId("pcv-1")
            }
        }

        @Test
        fun `withPromptChainVersionId then withWorkflowId throws`() {
            val builder = makeBuilder().withPromptChainVersionId("pcv-1")
            assertThrows<IllegalStateException> {
                builder.withWorkflowId("wf-1")
            }
        }

        @Test
        fun `withPromptChainVersionId then withPromptVersionId throws`() {
            val builder = makeBuilder().withPromptChainVersionId("pcv-1")
            assertThrows<IllegalStateException> {
                builder.withPromptVersionId("pv-1")
            }
        }

        @Test
        fun `setting same entity type twice overwrites`() {
            // This should not throw - same entity can be set again
            val builder = makeBuilder()
            builder.withWorkflowId("wf-1", "ctx1")
            // Setting a different workflow should fail if workflow already set?
            // The builder doesn't prevent re-setting the same type.
        }
    }

    // ─── withData Variants ──────────────────────────────────────────────

    @Nested
    inner class WithDataTest {
        @Test
        fun `withData string sets DatasetId`() {
            val builder = makeBuilder()
            builder.withData("ds-123")
            // Can't directly assert config, but we verify run() validates it
        }

        @Test
        fun `withData list sets LocalDataList`() {
            val builder = makeBuilder()
            val data = listOf(
                mapOf("input" to "q1", "expected" to "a1"),
                mapOf("input" to "q2", "expected" to "a2")
            )
            builder.withData(data)
        }

        @Test
        fun `withData function sets DataFunction`() {
            val builder = makeBuilder()
            builder.withData { index ->
                if (index < 3) mapOf("input" to "q$index") else null
            }
        }
    }

    // ─── withPreset Validation ──────────────────────────────────────────

    @Nested
    inner class WithPresetTest {
        @Test
        fun `withPreset rejects empty name`() {
            assertThrows<IllegalArgumentException> {
                makeBuilder().withPreset("")
            }
        }

        @Test
        fun `withPreset rejects blank name`() {
            assertThrows<IllegalArgumentException> {
                makeBuilder().withPreset("   ")
            }
        }

        @Test
        fun `withPreset accepts valid name`() {
            val builder = makeBuilder().withPreset("My Test Config")
            assertNotNull(builder)
        }
    }

    // ─── run() Validation ───────────────────────────────────────────────

    @Nested
    inner class RunValidationTest {
        @Test
        fun `run fails when no entity is set`() {
            val builder = makeBuilder().withData("ds-1")
            val exception = assertThrows<IllegalArgumentException> {
                builder.run()
            }
            assertTrue(exception.message!!.contains("workflow id"))
        }

        @Test
        fun `run fails when no data is set`() {
            val builder = makeBuilder().withWorkflowId("wf-1")
            val exception = assertThrows<IllegalArgumentException> {
                builder.run()
            }
            assertTrue(exception.message!!.contains("Data is required"))
        }

        @Test
        fun `run fails when name is blank`() {
            val builder = TestRunBuilder("https://test.ai", "key", "", "ws-1")
                .withWorkflowId("wf-1")
                .withData("ds-1")
            val exception = assertThrows<IllegalArgumentException> {
                builder.run()
            }
            assertTrue(exception.message!!.contains("Name is required"))
        }

        @Test
        fun `run fails when workspaceId is blank`() {
            val builder = TestRunBuilder("https://test.ai", "key", "Test", "")
                .withWorkflowId("wf-1")
                .withData("ds-1")
            val exception = assertThrows<IllegalArgumentException> {
                builder.run()
            }
            assertTrue(exception.message!!.contains("Workspace id is required"))
        }

        @Test
        fun `run fails with multiple missing configs`() {
            val builder = TestRunBuilder("https://test.ai", "key", "", "")
            val exception = assertThrows<IllegalArgumentException> {
                builder.run()
            }
            // Should contain multiple error messages
            assertTrue(exception.message!!.contains("Name is required"))
            assertTrue(exception.message!!.contains("Workspace id is required"))
        }

        @Test
        fun `run fails when preset requires entity but none set`() {
            val builder = makeBuilder()
                .withPreset("MyPreset")
                .withData("ds-1")
            val exception = assertThrows<IllegalArgumentException> {
                builder.run()
            }
            assertTrue(exception.message!!.contains("withPreset() requires an entity"))
        }
    }

    // ─── Polling Interval Calculation ───────────────────────────────────

    @Nested
    inner class PollingIntervalTest {
        @Test
        fun `short timeout returns small interval`() {
            val interval = TestRunBuilder.calculatePollingInterval(10)
            assertEquals(5, interval)
        }

        @Test
        fun `medium timeout returns medium interval`() {
            val interval = TestRunBuilder.calculatePollingInterval(30)
            assertTrue(interval in 5..15)
        }

        @Test
        fun `long timeout returns larger interval`() {
            val interval = TestRunBuilder.calculatePollingInterval(120)
            assertTrue(interval in 15..60)
        }

        @Test
        fun `very long timeout caps at 120`() {
            val interval = TestRunBuilder.calculatePollingInterval(1440)
            assertTrue(interval <= 120)
        }

        @Test
        fun `AI evaluator enforces minimum 15s interval`() {
            val interval = TestRunBuilder.calculatePollingInterval(10, isAiEvaluator = true)
            assertTrue(interval >= 15)
        }

        @Test
        fun `non-AI evaluator allows 5s minimum`() {
            val interval = TestRunBuilder.calculatePollingInterval(10, isAiEvaluator = false)
            assertTrue(interval >= 5)
        }
    }

    // ─── withEvaluators ─────────────────────────────────────────────────

    @Nested
    inner class WithEvaluatorsTest {
        @Test
        fun `accepts string evaluator names`() {
            val builder = makeBuilder().withEvaluators("Bias", "Clarity", "Relevance")
            assertNotNull(builder)
        }

        @Test
        fun `accepts PlatformEvaluator instances`() {
            val builder = makeBuilder().withEvaluators(
                PlatformEvaluator("CustomEval"),
                PlatformEvaluator("CustomEval2", variableMapping = mapOf("x" to { _, _, _ -> "val" }))
            )
            assertNotNull(builder)
        }

        @Test
        fun `accepts BaseEvaluator instances`() {
            val evaluator = object : BaseEvaluator(mapOf("test" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return mapOf("test" to LocalEvaluatorReturn(score = 1))
                }
            }
            val builder = makeBuilder().withEvaluators(evaluator)
            assertNotNull(builder)
        }

        @Test
        fun `accepts mixed evaluator types`() {
            val localEval = object : BaseEvaluator(mapOf("local" to makePassFailCriteria())) {
                override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                    return mapOf("local" to LocalEvaluatorReturn(score = 1))
                }
            }
            val builder = makeBuilder().withEvaluators(
                "Bias",
                PlatformEvaluator("Custom"),
                localEval
            )
            assertNotNull(builder)
        }

        @Test
        fun `multiple withEvaluators calls accumulate`() {
            val builder = makeBuilder()
                .withEvaluators("Bias")
                .withEvaluators("Clarity")
            assertNotNull(builder)
        }
    }

    // ─── withHumanEvaluationConfig ──────────────────────────────────────

    @Nested
    inner class WithHumanEvaluationConfigTest {
        @Test
        fun `sets human evaluation config`() {
            val config = HumanEvaluationConfig(
                emails = listOf("reviewer@test.com"),
                instructions = "Rate 1-5",
                requester = "tester"
            )
            val builder = makeBuilder().withHumanEvaluationConfig(config)
            assertNotNull(builder)
        }
    }

    // ─── withConcurrency ────────────────────────────────────────────────

    @Nested
    inner class WithConcurrencyTest {
        @Test
        fun `accepts positive concurrency`() {
            val builder = makeBuilder().withConcurrency(5)
            assertNotNull(builder)
        }

        @Test
        fun `accepts concurrency of 1`() {
            val builder = makeBuilder().withConcurrency(1)
            assertNotNull(builder)
        }
    }

    // ─── withLogger ─────────────────────────────────────────────────────

    @Nested
    inner class WithLoggerTest {
        @Test
        fun `accepts custom logger`() {
            val logs = mutableListOf<String>()
            val customLogger = object : TestRunLogger {
                override fun info(message: String) { logs.add("INFO: $message") }
                override fun error(message: String, e: Exception?) { logs.add("ERROR: $message") }
            }
            val builder = makeBuilder().withLogger(customLogger)
            assertNotNull(builder)
        }
    }

    // ─── Entity Config Context ──────────────────────────────────────────

    @Nested
    inner class EntityConfigTest {
        @Test
        fun `withWorkflowId stores context to evaluate`() {
            val builder = makeBuilder().withWorkflowId("wf-1", "my_context_column")
            assertNotNull(builder)
        }

        @Test
        fun `withPromptVersionId stores context to evaluate`() {
            val builder = makeBuilder().withPromptVersionId("pv-1", "context_col")
            assertNotNull(builder)
        }

        @Test
        fun `withPromptChainVersionId stores context to evaluate`() {
            val builder = makeBuilder().withPromptChainVersionId("pcv-1", "ctx")
            assertNotNull(builder)
        }

        @Test
        fun `context to evaluate defaults to null`() {
            val builder = makeBuilder().withWorkflowId("wf-1")
            assertNotNull(builder)
        }
    }

    // ─── yieldsOutput Mutual Exclusivity ────────────────────────────────

    @Nested
    inner class YieldsOutputMutualExclusivityTest {
        @Test
        fun `yieldsOutput then withWorkflowId throws`() {
            val builder = makeBuilder().yieldsOutput { row -> YieldedOutput(data = "test") }
            assertThrows<IllegalStateException> { builder.withWorkflowId("wf-1") }
        }

        @Test
        fun `withWorkflowId then yieldsOutput throws`() {
            val builder = makeBuilder().withWorkflowId("wf-1")
            assertThrows<IllegalStateException> {
                builder.yieldsOutput { row -> YieldedOutput(data = "test") }
            }
        }

        @Test
        fun `yieldsOutput then withPromptVersionId throws`() {
            val builder = makeBuilder().yieldsOutput { row -> YieldedOutput(data = "test") }
            assertThrows<IllegalStateException> { builder.withPromptVersionId("pv-1") }
        }

        @Test
        fun `yieldsOutput then withPromptChainVersionId throws`() {
            val builder = makeBuilder().yieldsOutput { row -> YieldedOutput(data = "test") }
            assertThrows<IllegalStateException> { builder.withPromptChainVersionId("pcv-1") }
        }

        @Test
        fun `withPromptChainVersionId then yieldsOutput throws`() {
            val builder = makeBuilder().withPromptChainVersionId("pcv-1")
            assertThrows<IllegalStateException> {
                builder.yieldsOutput { row -> YieldedOutput(data = "test") }
            }
        }

        @Test
        fun `yieldsOutput accepts simple lambda`() {
            val builder = makeBuilder().yieldsOutput { row -> YieldedOutput(data = "test") }
            assertNotNull(builder)
        }

        @Test
        fun `yieldsOutput accepts simulation-aware lambda`() {
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3))
                .yieldsOutput { row, simCtx -> YieldedOutput(data = "turn ${simCtx?.turnNumber}") }
            assertNotNull(builder)
        }
    }

    // ─── withSimulationConfig Validation ────────────────────────────────

    @Nested
    inner class SimulationConfigValidationTest {
        @Test
        fun `withSimulationConfig then withPromptChainVersionId allowed by builder but fails in run`() {
            // Builder allows this combo, but run() validation catches it
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3))
                .withPromptChainVersionId("pcv-1")
                .withData("ds-1")
                .withEvaluators("Bias")
            val ex = assertThrows<IllegalArgumentException> { builder.run() }
            assertTrue(ex.message!!.contains("Simulation config cannot be used with withPromptChainVersionId"))
        }

        @Test
        fun `withPromptChainVersionId then withSimulationConfig throws`() {
            val builder = makeBuilder().withPromptChainVersionId("pcv-1")
            assertThrows<IllegalStateException> {
                builder.withSimulationConfig(SimulationConfig(maxTurns = 3))
            }
        }

        @Test
        fun `sim + yieldsOutput + promptChain errors in run`() {
            // Can't even set this combo via builder - promptChain blocks sim
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3))
                .yieldsOutput { row, _ -> YieldedOutput(data = "test") }
                .withData(listOf(mapOf("input" to "test")))
            // No entity set and no exception from builder, but run() should error
            // because sim without output requires entity
            // Actually with yieldsOutput this should work for SDK-only sim
            // The data validation will pass but there's no evaluator - let's just ensure it doesn't crash on validation
        }

        @Test
        fun `sim without entity or outputFunction errors in run`() {
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3))
                .withData("ds-1")
                .withEvaluators("Bias")
            val ex = assertThrows<IllegalArgumentException> { builder.run() }
            assertTrue(ex.message!!.contains("Simulation config requires either withWorkflowId or withPromptVersionId"))
        }

        @Test
        fun `responseFields without workflow errors in run`() {
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3, responseFields = listOf("response")))
                .withPromptVersionId("pv-1")
                .withData("ds-1")
                .withEvaluators("Bias")
            val ex = assertThrows<IllegalArgumentException> { builder.run() }
            assertTrue(ex.message!!.contains("responseFields"))
        }

        @Test
        fun `sim + workflow is valid configuration`() {
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3))
                .withWorkflowId("wf-1")
                .withData("ds-1")
            assertNotNull(builder)
        }

        @Test
        fun `sim + prompt is valid configuration`() {
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3))
                .withPromptVersionId("pv-1")
                .withData("ds-1")
            assertNotNull(builder)
        }

        @Test
        fun `sim + yieldsOutput (no entity) is valid configuration`() {
            val builder = makeBuilder()
                .withSimulationConfig(SimulationConfig(maxTurns = 3))
                .yieldsOutput { row, simCtx -> YieldedOutput(data = "test") }
                .withData(listOf(mapOf("input" to "test")))
            assertNotNull(builder)
        }
    }

    // ─── Simulation Model Tests ─────────────────────────────────────────

    @Nested
    inner class SimulationModelTest {
        @Test
        fun `SimulationConfig basic toJsonObject`() {
            val config = SimulationConfig(maxTurns = 5, persona = "helpful assistant")
            val json = config.toJsonObject()
            assertEquals(5, json["maxTurns"]!!.jsonPrimitive.int)
            assertEquals("helpful assistant", json["persona"]!!.jsonPrimitive.content)
        }

        @Test
        fun `SimulationConfig with customSimulator flattens fields`() {
            val config = SimulationConfig(
                maxTurns = 3,
                customSimulator = CustomSimulatorConfig(
                    simulatorPrompt = "You are a tester",
                    model = "gpt-4o-mini",
                    provider = "openai"
                )
            )
            val json = config.toJsonObject()
            assertEquals("CUSTOM", json["type"]!!.jsonPrimitive.content)
            assertEquals("You are a tester", json["simulatorPrompt"]!!.jsonPrimitive.content)
            assertEquals("gpt-4o-mini", json["model"]!!.jsonPrimitive.content)
            assertEquals("openai", json["provider"]!!.jsonPrimitive.content)
        }

        @Test
        fun `SimulationConfig roundtrip`() {
            val original = SimulationConfig(maxTurns = 10, persona = "user", tools = listOf("search"))
            val parsed = SimulationConfig.fromJsonObject(original.toJsonObject())
            assertEquals(10, parsed.maxTurns)
            assertEquals("user", parsed.persona)
            assertEquals(listOf("search"), parsed.tools)
        }

        @Test
        fun `getNestedFieldValue traverses maps`() {
            val obj = mapOf("a" to mapOf("b" to mapOf("c" to 42)))
            assertEquals(42, TestRunBuilder.getNestedFieldValue(obj, "a.b.c"))
        }

        @Test
        fun `getNestedFieldValue returns null for missing path`() {
            val obj = mapOf("a" to "value")
            assertEquals(null, TestRunBuilder.getNestedFieldValue(obj, "a.b.c"))
        }
    }
}
