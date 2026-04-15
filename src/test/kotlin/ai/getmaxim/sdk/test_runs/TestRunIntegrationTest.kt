package ai.getmaxim.sdk.test_runs
import ai.getmaxim.sdk.Config
import ai.getmaxim.sdk.Maxim
import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests that run against the Maxim platform.
 *
 * ## How to run
 *
 * Set the following environment variables before running:
 *
 * ```bash
 * export MAXIM_API_KEY="your-api-key"
 * export MAXIM_BASE_URL="https://app.getmaxim.ai"       # optional, defaults to this
 * export MAXIM_WORKSPACE_ID="your-workspace-id"
 * export MAXIM_DATASET_ID="your-dataset-id"
 * export MAXIM_WORKFLOW_ID="your-workflow-id"
 * export MAXIM_PROMPT_VERSION_ID="your-prompt-version-id"
 * export MAXIM_PROMPT_CHAIN_VERSION_ID="your-prompt-chain-version-id"
 * ```
 *
 * Then run via Gradle (requires Java 17):
 *
 * ```bash
 * ./gradlew test --tests "ai.getmaxim.sdk.test_runs.TestRunIntegrationTest" \
 *   -PsigningPassword=dummy
 * ```
 *
 * Or run a single test:
 *
 * ```bash
 * ./gradlew test --tests "ai.getmaxim.sdk.test_runs.TestRunIntegrationTest.test create test run with workflow id" \
 *   -PsigningPassword=dummy
 * ```
 */
@ExperimentalSerializationApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestRunIntegrationTest {

    private lateinit var maxim: Maxim
    private lateinit var apiKey: String
    private lateinit var baseUrl: String
    private lateinit var workspaceId: String
    private lateinit var datasetId: String
    private lateinit var workflowId: String
    private lateinit var promptVersionId: String
    private lateinit var promptChainVersionId: String

    private val logger = object : TestRunLogger {
        override fun info(message: String) = println(message)
        override fun error(message: String, e: Exception?) = println("ERROR: $message ${e ?: ""}")
    }

    private fun env(key: String): String {
        val denv = dotenv { ignoreIfMissing = true }
        return denv[key]
            ?: System.getenv(key)
            ?: throw IllegalStateException("Missing $key — set it in .env or as an environment variable")
    }

    private fun env(key: String, default: String): String {
        val denv = dotenv { ignoreIfMissing = true }
        return denv[key] ?: System.getenv(key) ?: default
    }

    @BeforeAll
    fun setUp() {
        apiKey = env("MAXIM_API_KEY")
        baseUrl = env("MAXIM_BASE_URL", "https://app.getmaxim.ai")
        workspaceId = env("MAXIM_WORKSPACE_ID")
        datasetId = env("MAXIM_DATASET_ID")
        workflowId = env("MAXIM_WORKFLOW_ID")
        promptVersionId = env("MAXIM_PROMPT_VERSION_ID")
        promptChainVersionId = env("MAXIM_PROMPT_CHAIN_VERSION_ID")

        maxim = Maxim(Config(apiKey = apiKey, baseUrl = baseUrl, debug = true))
    }

    @AfterAll
    fun tearDown() {
        if (::maxim.isInitialized) {
            maxim.cleanup().get()
        }
    }

    // ─── Workflow Tests ─────────────────────────────────────────────────

    @Test
    fun `test create test run with workflow id`() {
        val result = maxim.createTestRun("java-sdk-workflow-test", workspaceId)
            .withWorkflowId(workflowId)
            .withData(datasetId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        assertNotNull(result.testRunResult.link)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test create test run with workflow and local data`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "What is machine learning?"),
            mapOf<String, Any?>("input" to "Explain neural networks"),
            mapOf<String, Any?>("input" to "What is deep learning?"),
        )

        val result = maxim.createTestRun("java-sdk-workflow-local-data", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .withWorkflowId(workflowId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test create test run with workflow and local evaluators`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "test"),
            mapOf<String, Any?>("input" to "example"),
            mapOf<String, Any?>("input" to "sample"),
        )

        val customEvaluator = object : BaseEvaluator(
            mapOf(
                "always_pass" to PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry(">", 0),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">", 0, "average")
                ),
                "boolean_check" to PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry("=", true),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 80, "percentageOfPassedResults")
                )
            )
        ) {
            override fun evaluate(
                result: LocalEvaluatorResultParameter,
                data: LocalData
            ): Map<String, LocalEvaluatorReturn> {
                return mapOf(
                    "always_pass" to LocalEvaluatorReturn(score = 1, reasoning = "Always passes"),
                    "boolean_check" to LocalEvaluatorReturn(score = true, reasoning = "Checked")
                )
            }
        }

        val result = maxim.createTestRun("java-sdk-workflow-local-evals", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .withWorkflowId(workflowId)
            .withEvaluators("Bias", customEvaluator)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        assertTrue(result.failedEntryIndices.isEmpty())
        println("Report: ${result.testRunResult.link}")
    }

    // ─── Prompt Version Tests ───────────────────────────────────────────

    @Test
    fun `test create test run with prompt version id`() {
        val result = maxim.createTestRun("java-sdk-prompt-version-test", workspaceId)
            .withPromptVersionId(promptVersionId)
            .withData(datasetId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        assertNotNull(result.testRunResult.link)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test create test run with prompt version and local data`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "What is AI?"),
            mapOf<String, Any?>("input" to "Tell me about GPT"),
            mapOf<String, Any?>("input" to "Explain transformers"),
        )

        val result = maxim.createTestRun("java-sdk-prompt-local-data", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .withPromptVersionId(promptVersionId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // // ─── Prompt Chain Version Tests ─────────────────────────────────────

    @Test
    fun `test create test run with prompt chain version id`() {
        val result = maxim.createTestRun("java-sdk-prompt-chain-test", workspaceId)
            .withPromptChainVersionId(promptChainVersionId)
            .withData(datasetId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        assertNotNull(result.testRunResult.link)
        println("Report: ${result.testRunResult.link}")
    }

    // ─── Local Data with Multiple Column Types ──────────────────────────

    @Test
    fun `test create test run with multiple column types`() {
        val data = listOf(
            mapOf<String, Any?>(
                "input" to "Analyze this text",
                "context" to "Technical writing",
                "expected_output" to "A detailed analysis"
            ),
            mapOf<String, Any?>(
                "input" to "Summarize this",
                "context" to "News article",
                "expected_output" to "A brief summary"
            ),
        )

        val dataStructure = mapOf(
            "input" to "INPUT",
            "context" to "VARIABLE",
            "expected_output" to "EXPECTED_OUTPUT"
        )

        val result = maxim.createTestRun("java-sdk-multi-column-test", workspaceId)
            .withDataStructure(dataStructure)
            .withData(data)
            .withWorkflowId(workflowId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // // ─── Image Variables ────────────────────────────────────────────────

    @Test
    fun `test create test run with image variables and workflow`() {
        val data = listOf(
            mapOf<String, Any?>(
                "input" to "Analyze this image and text",
                "image_url" to "https://images.unsplash.com/photo-1494871262121-49703fd34e2b?fm=jpg&q=60&w=3000",
                "context" to "Product description analysis",
                "expected_output" to "Detailed analysis result"
            ),
            mapOf<String, Any?>(
                "input" to "Compare these visual elements",
                "image_url" to "https://www.thoughtco.com/thmb/i3i0DhTooFFhVLjnBcwJhT5z9Q0=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/what-are-the-elements-of-art-182704_FINAL-9a30cee7896f4d3a9e078274851d5382.png",
                "context" to "Visual comparison task",
                "expected_output" to "Comparison summary"
            ),
        )

        val dataStructure = mapOf(
            "input" to "INPUT",
            "image_url" to "FILE_URL_VARIABLE",
            "context" to "VARIABLE",
            "expected_output" to "EXPECTED_OUTPUT"
        )

        val result = maxim.createTestRun("java-sdk-image-vars-workflow", workspaceId)
            .withDataStructure(dataStructure)
            .withData(data)
            .withEvaluators("Bias", "Clarity")
            .withWorkflowId(workflowId)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test create test run with image variables and prompt version`() {
        val data = listOf(
            mapOf<String, Any?>(
                "input" to "Analyze this image",
                "image_url" to "https://images.unsplash.com/photo-1494871262121-49703fd34e2b?fm=jpg&q=60&w=3000",
                "context" to "Image analysis",
                "expected_output" to "Analysis result"
            ),
        )

        val dataStructure = mapOf(
            "input" to "INPUT",
            "image_url" to "FILE_URL_VARIABLE",
            "context" to "VARIABLE",
            "expected_output" to "EXPECTED_OUTPUT"
        )

        val result = maxim.createTestRun("java-sdk-image-vars-prompt", workspaceId)
            .withDataStructure(dataStructure)
            .withData(data)
            .withEvaluators("Bias")
            .withPromptVersionId(promptVersionId)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // // ─── Tags and Environment ───────────────────────────────────────────

    @Test
    fun `test create test run with tags and environment`() {
        val result = maxim.createTestRun("java-sdk-tags-env-test", workspaceId)
            .withWorkflowId(workflowId)
            .withData(datasetId)
            .withConcurrency(2)
            .withEvaluators("Bias")
            .withTags(listOf("java-sdk", "integration-test", "v1"))
            .withEnvironment("staging")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // // ─── Multiple Platform Evaluators ───────────────────────────────────

    @Test
    fun `test create test run with multiple evaluators`() {
        val result = maxim.createTestRun("java-sdk-multi-eval-test", workspaceId)
            .withWorkflowId(workflowId)
            .withData(datasetId)
            .withEvaluators("Bias", "Clarity")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        // Verify both evaluators have scores
        for (resultObj in result.testRunResult.result) {
            assertTrue(resultObj.individualEvaluatorMeanScore.isNotEmpty())
            println("Evaluator scores: ${resultObj.individualEvaluatorMeanScore.keys}")
        }
        println("Report: ${result.testRunResult.link}")
    }

    // // ─── Data Function ──────────────────────────────────────────────────

    @Test
    fun `test create test run with data function`() {
        val inputs = listOf("What is AI?", "Explain ML", "What are LLMs?")

        val result = maxim.createTestRun("java-sdk-data-fn-test", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData { index ->
                if (index < inputs.size) mapOf("input" to inputs[index]) else null
            }
            .withWorkflowId(workflowId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        assertEquals(0, result.failedEntryIndices.size)
        println("Report: ${result.testRunResult.link}")
    }

    // ─── yields_output Tests ────────────────────────────────────────────

    @Test
    fun `test yields_output with dataset and platform eval`() {
        // Dataset columns are "Input", "Expected Steps", "Scenario" (capital I)
        val result = maxim.createTestRun("java-sdk-yields-output-dataset", workspaceId)
            .withDataStructure(mapOf("Input" to "INPUT", "Scenario" to "SCENARIO", "Expected Steps" to "EXPECTED_STEPS"))
            .yieldsOutput { row -> YieldedOutput(data = "Response for: ${row["Input"] ?: row["input"] ?: "unknown"}") }
            .withData(datasetId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test yields_output with local data and platform eval`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "What is AI?"),
            mapOf<String, Any?>("input" to "Explain ML"),
            mapOf<String, Any?>("input" to "What are LLMs?"),
        )
        val result = maxim.createTestRun("java-sdk-yields-output-local", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .yieldsOutput { row -> YieldedOutput(data = "AI answer for: ${row["input"]}") }
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test yields_output with local data and local eval`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "Hello"),
            mapOf<String, Any?>("input" to "World"),
        )
        val localEval = object : BaseEvaluator(mapOf(
            "length_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 1),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 50, "average")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                val score = if (result.output.length > 5) 1 else 0
                return mapOf("length_check" to LocalEvaluatorReturn(score = score, reasoning = "Output length: ${result.output.length}"))
            }
        }

        val result = maxim.createTestRun("java-sdk-yields-output-local-eval", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .yieldsOutput { row -> YieldedOutput(data = "Response to ${row["input"]}") }
            .withEvaluators(localEval)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }
    

    // ─── Prompt Version Tests ───────────────────────────────────────────

    @Test
    fun `test prompt version with dataset and platform eval`() {
        val result = maxim.createTestRun("java-sdk-prompt-dataset-platform-eval", workspaceId)
            .withDataStructure(mapOf("Input" to "INPUT", "Expected Output" to "EXPECTED_OUTPUT"))
            .withPromptVersionId(promptVersionId)
            .withData(datasetId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test prompt version with local data and local eval`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "What is AI?"),
            mapOf<String, Any?>("input" to "Explain ML"),
        )
        val localEval = object : BaseEvaluator(mapOf(
            "output_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 1),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 50, "average")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                val score = if (result.output.isNotEmpty()) 1 else 0
                return mapOf("output_check" to LocalEvaluatorReturn(score = score, reasoning = "Output present: ${result.output.isNotEmpty()}"))
            }
        }

        val result = maxim.createTestRun("java-sdk-prompt-local-data-local-eval", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withPromptVersionId(promptVersionId)
            .withData(data)
            .withEvaluators(localEval)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test prompt version with local data and local plus platform eval`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "What is AI?"),
            mapOf<String, Any?>("input" to "Explain ML"),
        )
        val localEval = object : BaseEvaluator(mapOf(
            "length_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 1),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 50, "average")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                val score = if (result.output.length > 5) 1 else 0
                return mapOf("length_check" to LocalEvaluatorReturn(score = score, reasoning = "Output length: ${result.output.length}"))
            }
        }

        val result = maxim.createTestRun("java-sdk-prompt-local-plus-platform-eval", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withPromptVersionId(promptVersionId)
            .withData(data)
            .withEvaluators(localEval, "Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // ─── yields_output with local + platform eval combined ──────────────

    @Test
    fun `test yields_output with local data and local plus platform eval`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "Hello"),
            mapOf<String, Any?>("input" to "World"),
        )
        val localEval = object : BaseEvaluator(mapOf(
            "length_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 1),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 50, "average")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                val score = if (result.output.length > 5) 1 else 0
                return mapOf("length_check" to LocalEvaluatorReturn(score = score, reasoning = "Output length: ${result.output.length}"))
            }
        }

        val result = maxim.createTestRun("java-sdk-yields-output-local-plus-platform", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .yieldsOutput { row -> YieldedOutput(data = "Response to ${row["input"]}") }
            .withEvaluators(localEval, "Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // ─── Simulation Tests ───────────────────────────────────────────────


    @Test
    fun `test simulation with yields_output local execution`() {
        val data = listOf(
            mapOf<String, Any?>(
                "input" to "Hello, I need help with my order",
                "Scenario" to "Customer support inquiry about order",
                "Expected Steps" to "1. Greet customer\n2. Ask about order details"
            ),
            mapOf<String, Any?>(
                "input" to "Can you check my account?",
                "Scenario" to "Account inquiry",
                "Expected Steps" to "1. Acknowledge request\n2. Provide account info"
            ),
        )

        val result = maxim.createTestRun("java-sdk-sim-yields-output", workspaceId)
            .withSimulationConfig(SimulationConfig(maxTurns = 3))
            .withDataStructure(mapOf("input" to "INPUT", "Scenario" to "SCENARIO", "Expected Steps" to "EXPECTED_STEPS"))
            .withData(data)
            .yieldsOutput { row, simCtx ->
                val response = if (simCtx == null) {
                    "Welcome! How can I help you?"
                } else {
                    "Turn ${simCtx.turnNumber}: I understand your request about '${simCtx.currentUserInput["input"] ?: ""}'"
                }
                YieldedOutput(data = response)
            }
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test yields_output with PlatformEvaluator variable mapping`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "What is machine learning?"),
            mapOf<String, Any?>("input" to "Explain deep learning"),
        )

        val result = maxim.createTestRun("java-sdk-yields-output-var-mapping", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .yieldsOutput { row ->
                YieldedOutput(data = "ML is a subset of AI that uses data to learn patterns. Input was: ${row["input"]}")
            }
            .withConcurrency(2)
            .withEvaluators(
                PlatformEvaluator("Bias", variableMapping = mapOf(
                    "output" to { input, _, _ -> input.data }
                ))
            )
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test simulation with workflow and dataset`() {
        val result = maxim.createTestRun("java-sdk-sim-workflow-dataset", workspaceId)
            .withSimulationConfig(SimulationConfig(maxTurns = 3))
            .withDataStructure(mapOf("Input" to "INPUT", "Scenario" to "SCENARIO", "Expected Steps" to "EXPECTED_STEPS"))
            .withWorkflowId(workflowId)
            .withData(datasetId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test simulation with prompt and dataset`() {
        val result = maxim.createTestRun("java-sdk-sim-prompt-dataset", workspaceId)
            .withSimulationConfig(SimulationConfig(maxTurns = 3))
            .withDataStructure(mapOf("Input" to "INPUT", "Scenario" to "SCENARIO", "Expected Steps" to "EXPECTED_STEPS"))
            .withPromptVersionId(promptVersionId)
            .withData(datasetId)
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test simulation with workflow and local data and local plus platform eval`() {
        val data = listOf(
            mapOf<String, Any?>(
                "input" to "Tell me about space exploration",
                "Scenario" to "Question about space",
                "Expected Steps" to "1. Ask about space\n2. Provide answer about space exploration"
            ),
            mapOf<String, Any?>(
                "input" to "What is a galaxy?",
                "Scenario" to "Question about galaxies",
                "Expected Steps" to "1. Ask about galaxies\n2. Explain what a galaxy is"
            ),
        )
        val localEval = object : BaseEvaluator(mapOf(
            "sim_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 0),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 0, "average")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                return mapOf("sim_check" to LocalEvaluatorReturn(score = 1, reasoning = "Sim outputs: ${result.simulationOutputs?.size ?: 0}"))
            }
        }

        val result = maxim.createTestRun("java-sdk-sim-workflow-local-plus-platform-evals", workspaceId)
            .withSimulationConfig(SimulationConfig(maxTurns = 3))
            .withDataStructure(mapOf("input" to "INPUT", "Scenario" to "SCENARIO", "Expected Steps" to "EXPECTED_STEPS"))
            .withData(data)
            .withWorkflowId(workflowId)
            .withEvaluators(localEval, "Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test simulation with yields_output and local eval`() {
        val data = listOf(
            mapOf<String, Any?>(
                "input" to "Hello, I need help with my order",
                "Scenario" to "Customer support inquiry about order",
                "Expected Steps" to "1. Greet customer\n2. Ask about order details"
            ),
            mapOf<String, Any?>(
                "input" to "Can you check my account?",
                "Scenario" to "Account inquiry",
                "Expected Steps" to "1. Acknowledge request\n2. Provide account info"
            ),
        )
        val localEval = object : BaseEvaluator(mapOf(
            "sim_output_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 0),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 0, "average")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                val hasOutputs = (result.simulationOutputs?.size ?: 0) > 0
                return mapOf("sim_output_check" to LocalEvaluatorReturn(
                    score = if (hasOutputs) 1 else 0,
                    reasoning = "Simulation outputs count: ${result.simulationOutputs?.size ?: 0}"
                ))
            }
        }

        val result = maxim.createTestRun("java-sdk-sim-yields-output-local-eval", workspaceId)
            .withSimulationConfig(SimulationConfig(maxTurns = 3))
            .withDataStructure(mapOf("input" to "INPUT", "Scenario" to "SCENARIO", "Expected Steps" to "EXPECTED_STEPS"))
            .withData(data)
            .yieldsOutput { row, simCtx ->
                val response = if (simCtx == null) {
                    "Welcome! How can I help you?"
                } else {
                    "Turn ${simCtx.turnNumber}: I understand your request about '${simCtx.currentUserInput["input"] ?: ""}'"
                }
                YieldedOutput(data = response)
            }
            .withEvaluators(localEval)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test simulation with yields_output local execution and platform eval`() {
        val data = listOf(
            mapOf<String, Any?>(
                "input" to "Hello, I need help with my order",
                "Scenario" to "Customer support inquiry about order",
                "Expected Steps" to "1. Greet customer\n2. Ask about order details"
            ),
            mapOf<String, Any?>(
                "input" to "Can you check my account?",
                "Scenario" to "Account inquiry",
                "Expected Steps" to "1. Acknowledge request\n2. Provide account info"
            ),
        )

        val result = maxim.createTestRun("java-sdk-sim-yields-output-platform-eval", workspaceId)
            .withSimulationConfig(SimulationConfig(maxTurns = 3))
            .withDataStructure(mapOf("input" to "INPUT", "Scenario" to "SCENARIO", "Expected Steps" to "EXPECTED_STEPS"))
            .withData(data)
            .yieldsOutput { row, simCtx ->
                val response = if (simCtx == null) {
                    "Welcome! How can I help you?"
                } else {
                    "Turn ${simCtx.turnNumber}: I understand your request about '${simCtx.currentUserInput["input"] ?: ""}'"
                }
                YieldedOutput(data = response)
            }
            .withEvaluators("Bias")
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // ─── Preset Tests ───────────────────────────────────────────────────

    @Test
    fun `test with preset and workflow`() {
        val result = maxim.createTestRun("java-sdk-preset-workflow-test", workspaceId)
            .withPreset("Workflow sample test config")
            .withWorkflowId(workflowId)
            .withHumanEvaluationConfig(HumanEvaluationConfig(
                emails = listOf("aryan.deshmukh@getmaxim.ai"),
                instructions = "Is the information factually correct?"
            ))
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    @Test
    fun `test with preset and prompt version`() {
        val result = maxim.createTestRun("java-sdk-preset-prompt-test", workspaceId)
            .withPreset("Prompt sample test config")
            .withPromptVersionId(promptVersionId)
            .withHumanEvaluationConfig(HumanEvaluationConfig(
                emails = listOf("aryan.deshmukh@getmaxim.ai"),
                instructions = "Is the information factually correct?"
            ))
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // Presets with simulation configs in presets.

    // @Test
    // fun `test with preset and workflow`() {
    //     val result = maxim.createTestRun("java-sim-sdk-preset-workflow-test", workspaceId)
    //         .withPreset("Customer support evaluation")
    //         .withWorkflowId(workflowId)
    //         .withHumanEvaluationConfig(HumanEvaluationConfig(
    //             emails = listOf("aryan.deshmukh@getmaxim.ai"),
    //             instructions = "Is the information factually correct?"
    //         ))
    //         .withLogger(logger)
    //         .run()

    //     assertNotNull(result)
    //     println("Report: ${result.testRunResult.link}")
    // }

    // @Test
    // fun `test with preset and prompt version`() {
    //     val result = maxim.createTestRun("java-sim-sdk-preset-prompt-test", workspaceId)
    //         .withPromptVersionId(promptVersionId)
    //         .withPreset("[SAMPLE] Customer Support Assistant Test Run")
    //         .withLogger(logger)
    //         .run()

    //     assertNotNull(result)
    //     println("Report: ${result.testRunResult.link}")
    // }


    // ─── Multiple Local Evaluator Metrics ────────────────────────────────

    @Test
    fun `test yields_output with multiple local eval metrics`() {
        val data = listOf(
            mapOf<String, Any?>("input" to "Hello world"),
            mapOf<String, Any?>("input" to "Test input"),
        )
        val multiEval = object : BaseEvaluator(mapOf(
            "length_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 1),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 50, "average")
            ),
            "contains_input" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry("=", true),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 80, "percentageOfPassedResults")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                val lengthScore = if (result.output.length > 5) 1 else 0
                val containsInput = result.input?.let { result.output.contains(it, ignoreCase = true) } ?: false
                return mapOf(
                    "length_check" to LocalEvaluatorReturn(score = lengthScore, reasoning = "Output length: ${result.output.length}"),
                    "contains_input" to LocalEvaluatorReturn(score = containsInput, reasoning = "Contains input: $containsInput")
                )
            }
        }

        val result = maxim.createTestRun("java-sdk-yields-output-multi-local-eval", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData(data)
            .yieldsOutput { row -> YieldedOutput(data = "Response to ${row["input"]}") }
            .withEvaluators(multiEval)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

    // // ─── Data Function Tests ─────────────────────────────────────────────

    @Test
    fun `test yields_output with data function and local eval`() {
        val inputs = listOf("What is AI?", "Explain ML")

        val localEval = object : BaseEvaluator(mapOf(
            "output_check" to PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry(">=", 1),
                forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 50, "average")
            )
        )) {
            override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
                val score = if (result.output.isNotEmpty()) 1 else 0
                return mapOf("output_check" to LocalEvaluatorReturn(score = score, reasoning = "Has output: ${result.output.isNotEmpty()}"))
            }
        }

        val result = maxim.createTestRun("java-sdk-yields-output-data-fn-local-eval", workspaceId)
            .withDataStructure(mapOf("input" to "INPUT"))
            .withData { index ->
                if (index < inputs.size) mapOf("input" to inputs[index]) else null
            }
            .yieldsOutput { row -> YieldedOutput(data = "Answer for: ${row["input"]}") }
            .withEvaluators(localEval)
            .withLogger(logger)
            .run()

        assertNotNull(result)
        println("Report: ${result.testRunResult.link}")
    }

}
