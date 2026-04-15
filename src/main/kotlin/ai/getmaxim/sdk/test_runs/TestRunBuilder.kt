package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.apis.MaximAPI
import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.math.*

/**
 * Builder for creating and executing test runs on the Maxim platform.
 */
class TestRunBuilder internal constructor(
    baseUrl: String,
    apiKey: String,
    name: String,
    workspaceId: String
) {
    private val config = TestRunConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        name = name,
        workspaceId = workspaceId
    )
    private val api = MaximAPI.TestRunAPI(baseUrl, apiKey)
    private var presetName: String? = null

    // ─── Builder Methods ────────────────────────────────────────────────

    fun withData(datasetId: String): TestRunBuilder {
        config.data = TestRunData.DatasetId(datasetId)
        return this
    }

    fun withData(data: List<Map<String, Any?>>): TestRunBuilder {
        config.data = TestRunData.LocalDataList(data)
        return this
    }

    fun withData(dataFn: (Int) -> Map<String, Any?>?): TestRunBuilder {
        config.data = TestRunData.DataFunction(dataFn)
        return this
    }

    fun withDataStructure(data: Map<String, String>): TestRunBuilder {
        config.dataStructure = data
        return this
    }

    fun withEvaluators(vararg evaluators: Any): TestRunBuilder {
        config.evaluators.addAll(evaluators.toList())
        return this
    }

    fun withTags(tags: List<String>): TestRunBuilder {
        config.tags = tags
        return this
    }

    fun withHumanEvaluationConfig(humanEvaluationConfig: HumanEvaluationConfig): TestRunBuilder {
        config.humanEvaluationConfig = humanEvaluationConfig
        return this
    }

    fun withEnvironment(environmentName: String): TestRunBuilder {
        config.environmentName = environmentName
        return this
    }

    fun withWorkflowId(workflowId: String, contextToEvaluate: String? = null): TestRunBuilder {
        if (config.promptVersion != null) throw IllegalStateException(
            "Prompt version id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        if (config.promptChainVersion != null) throw IllegalStateException(
            "Prompt chain version id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        if (config.outputFunction != null && config.simulationConfig == null) throw IllegalStateException(
            "yields_output is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        config.workflow = WorkflowConfig(id = workflowId, contextToEvaluate = contextToEvaluate)
        return this
    }

    fun withPromptVersionId(promptVersionId: String, contextToEvaluate: String? = null): TestRunBuilder {
        if (config.workflow != null) throw IllegalStateException(
            "Workflow id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        if (config.promptChainVersion != null) throw IllegalStateException(
            "Prompt chain version id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        if (config.outputFunction != null && config.simulationConfig == null) throw IllegalStateException(
            "yields_output is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        config.promptVersion = TestRunPromptVersionConfig(id = promptVersionId, contextToEvaluate = contextToEvaluate)
        return this
    }

    fun withPromptChainVersionId(promptChainVersionId: String, contextToEvaluate: String? = null): TestRunBuilder {
        if (config.workflow != null) throw IllegalStateException(
            "Workflow id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        if (config.promptVersion != null) throw IllegalStateException(
            "Prompt version id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        if (config.outputFunction != null) throw IllegalStateException(
            "yields_output is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
        )
        config.promptChainVersion = TestRunPromptChainVersionConfig(id = promptChainVersionId, contextToEvaluate = contextToEvaluate)
        return this
    }

    fun withPreset(presetName: String): TestRunBuilder {
        require(presetName.isNotBlank()) { "Preset name must be a non-empty string." }
        this.presetName = presetName
        return this
    }

    fun withConcurrency(concurrency: Int): TestRunBuilder {
        require(concurrency >= 1) { "Concurrency must be at least 1, got $concurrency" }
        config.concurrency = concurrency
        return this
    }

    fun withLogger(logger: TestRunLogger): TestRunBuilder {
        config.logger = logger
        return this
    }

    /**
     * Set the simulation configuration for the test run.
     * Cannot be used with withPromptChainVersionId.
     */
    fun withSimulationConfig(simulationConfig: SimulationConfig): TestRunBuilder {
        if (config.promptChainVersion != null) throw IllegalStateException(
            "Simulation config cannot be used with withPromptChainVersionId. Use withWorkflowId or withPromptVersionId instead."
        )
        config.simulationConfig = simulationConfig
        return this
    }

    /**
     * Set the output function for the test run.
     * For simulation mode, the function receives (data, simulationContext).
     * For non-simulation mode, simulationContext is null.
     */
    fun yieldsOutput(
        outputFunction: (Map<String, Any?>, SimulationContext?) -> YieldedOutput
    ): TestRunBuilder {
        if (config.simulationConfig != null) {
            if (config.promptVersion != null || config.workflow != null) {
                throw IllegalStateException(
                    "simulation_config with yields_output cannot be used with withPromptVersionId or withWorkflowId. " +
                    "For local-execution simulation, omit withPromptVersionId and withWorkflowId."
                )
            }
        }
        if (config.simulationConfig == null) {
            if (config.workflow != null) throw IllegalStateException(
                "Workflow id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
            )
            if (config.promptChainVersion != null) throw IllegalStateException(
                "Prompt chain version id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
            )
            if (config.promptVersion != null) throw IllegalStateException(
                "Prompt version id is already set. You can use either one of withPromptVersionId, withPromptChainVersionId, withWorkflowId, or yieldsOutput in a test run."
            )
        }
        config.outputFunction = outputFunction
        return this
    }

    /**
     * Convenience overload for non-simulation yields_output.
     * The function receives just the data row.
     */
    fun yieldsOutput(
        outputFunction: (Map<String, Any?>) -> YieldedOutput
    ): TestRunBuilder {
        return yieldsOutput { row, _ -> outputFunction(row) }
    }

    // ─── Run ────────────────────────────────────────────────────────────

    fun run(timeoutInMinutes: Int = 10): RunResult? {
        return runBlocking { runInternal(timeoutInMinutes) }
    }

    private suspend fun runInternal(timeoutInMinutes: Int): RunResult? {
        try {
            val errors = mutableListOf<String>()
            config.logger.info("Validating test run config...")

            if (config.name.isBlank()) errors.add("Name is required to run a test.")
            if (config.workspaceId.isBlank()) errors.add("Workspace id is required to run a test.")

            // Resolve preset before remaining validation
            if (presetName != null) {
                var entityId: String? = null
                var entityType: String? = null
                when {
                    config.workflow != null -> { entityId = config.workflow!!.id; entityType = "WORKFLOW" }
                    config.promptVersion != null -> { entityId = config.promptVersion!!.id; entityType = "PROMPT" }
                    config.promptChainVersion != null -> { entityId = config.promptChainVersion!!.id; entityType = "PROMPT_CHAIN" }
                }
                if (entityId == null || entityType == null) {
                    errors.add("withPreset() requires an entity to be set first via withWorkflowId(), withPromptVersionId(), or withPromptChainVersionId().")
                } else {
                    config.logger.info("Fetching preset '$presetName' for $entityType '$entityId'...")
                    try {
                        val preset = api.fetchPreset(presetName!!, config.workspaceId, entityId, entityType)
                        resolvePreset(preset)
                        config.logger.info("Preset '$presetName' resolved successfully.")
                    } catch (e: Exception) {
                        errors.add("Failed to fetch preset '$presetName': ${e.message}")
                    }
                }
            }

            // Entity/output function check
            if (config.outputFunction == null && config.workflow == null && config.promptVersion == null && config.promptChainVersion == null) {
                errors.add(
                    "One of output function (by calling yieldsOutput) or workflow id (by calling withWorkflowId) " +
                    "or prompt version id (by calling withPromptVersionId) or prompt chain version id (by calling withPromptChainVersionId) is required to run a test."
                )
            }
            if (config.data == null) errors.add("Data is required to run a test. Use withData() to set it.")

            // Simulation-specific validation
            if (config.simulationConfig != null) {
                if (config.outputFunction != null) {
                    if (config.promptChainVersion != null) errors.add(
                        "Simulation config with yields_output cannot use withPromptChainVersionId. Use withPromptVersionId or withWorkflowId, or omit both for SDK-only simulation."
                    )
                    if (config.promptVersion != null && config.workflow != null) errors.add(
                        "Simulation config with yields_output cannot use both withPromptVersionId and withWorkflowId. Set at most one (or neither for SDK-only simulation)."
                    )
                } else {
                    if (config.promptChainVersion != null) errors.add(
                        "Simulation config cannot be used with withPromptChainVersionId. Use withWorkflowId or withPromptVersionId instead."
                    )
                    if (config.workflow == null && config.promptVersion == null) errors.add(
                        "Simulation config requires either withWorkflowId or withPromptVersionId to be set."
                    )
                }
                if (config.simulationConfig!!.responseFields != null && config.simulationConfig!!.responseFields!!.isNotEmpty() && config.workflow == null) {
                    errors.add("responseFields in simulationConfig can only be used with withWorkflowId, not with withPromptVersionId.")
                }
            }

            if (errors.isNotEmpty()) throw IllegalArgumentException("Missing required configuration for test\n" + errors.joinToString("\n"))

            // Sanitize
            config.logger.info("Sanitizing data...")
            val data = config.data!!
            if (data is TestRunData.LocalDataList && config.dataStructure != null) sanitizeData(data.data, config.dataStructure!!)
            config.logger.info("Sanitizing evaluators...")
            sanitizeEvaluators(config.evaluators)

            // Build evaluator configs
            val evaluatorConfigs = mutableListOf<Evaluator>()
            val evaluatorNameToIdMap = getLocalEvaluatorNameToIdAndPassFailCriteriaMap(config.evaluators)
            for (evaluator in config.evaluators) {
                when (evaluator) {
                    is String -> {
                        config.logger.info("Verifying if $evaluator is added to the workspace...")
                        val ec = api.fetchPlatformEvaluator(evaluator, config.workspaceId)
                        evaluatorConfigs.add(ec)
                    }
                    is PlatformEvaluator -> {
                        config.logger.info("Verifying if ${evaluator.name} is added to the workspace...")
                        val ec = api.fetchPlatformEvaluator(evaluator.name, config.workspaceId)
                        evaluatorConfigs.add(ec)
                        if (evaluator.name !in evaluatorNameToIdMap) {
                            evaluatorNameToIdMap[evaluator.name] = EvaluatorNameToIdAndPassFailCriteria(ec.id, null)
                        }
                    }
                    is BaseEvaluator -> {
                        for (name in evaluator.names) {
                            val info = evaluatorNameToIdMap[name]!!
                            evaluatorConfigs.add(getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(info.id, name, info.passFailCriteria!!))
                        }
                    }
                }
            }

            if (evaluatorConfigs.any { it.type == EvaluatorType.HUMAN } && config.humanEvaluationConfig == null) {
                throw IllegalArgumentException("Human evaluator found in evaluators, but no human evaluation config was provided.")
            }

            val failedEntryIndices = ConcurrentLinkedQueue<Int>()
            val allEntriesProcessed = CountDownLatch(1)

            try {
                config.logger.info("Creating test run: ${config.name}")
                val requiresLocalRun = config.evaluators.any { it is BaseEvaluator } || config.outputFunction != null

                // Default responseFields for workflow + simulation
                var simulationConfigToSend = config.simulationConfig
                if (config.workflow != null && simulationConfigToSend != null
                    && (simulationConfigToSend.responseFields.isNullOrEmpty())) {
                    simulationConfigToSend = simulationConfigToSend.copy(responseFields = listOf("response"))
                }

                val testRun = api.createTestRun(
                    name = config.name, workspaceId = config.workspaceId, runType = RunType.SINGLE,
                    workflowId = config.workflow?.id, promptVersionId = config.promptVersion?.id,
                    promptChainVersionId = config.promptChainVersion?.id,
                    evaluatorConfig = evaluatorConfigs, requiresLocalRun = requiresLocalRun,
                    tags = config.tags, humanEvaluationConfig = config.humanEvaluationConfig,
                    testConfigId = config.testConfigId, simulationConfig = simulationConfigToSend
                )
                if (config.environmentName != null) testRun.environmentName = config.environmentName

                try {
                    api.markTestRunProcessed(testRun.id)
                    val onAllDispatched: suspend () -> Unit = {
                        allEntriesProcessed.countDown()
                    }
                    when (data) {
                        is TestRunData.DatasetId -> runTestWithDatasetId(testRun, data.id, { failedEntryIndices.add(it) }, onAllDispatched, evaluatorNameToIdMap)
                        is TestRunData.LocalDataList -> runTestWithLocalData(testRun, { i -> if (i < data.data.size) data.data[i] else null }, { failedEntryIndices.add(it) }, onAllDispatched, evaluatorNameToIdMap)
                        is TestRunData.DataFunction -> runTestWithLocalData(testRun, data.fn, { failedEntryIndices.add(it) }, onAllDispatched, evaluatorNameToIdMap)
                    }
                } catch (e: Exception) {
                    api.markTestRunFailed(testRun.id)
                    throw e
                }

                // Poll for completion
                val pollingInterval = calculatePollingInterval(timeoutInMinutes, evaluatorConfigs.any { it.type == EvaluatorType.AI })
                val maxIterations = ceil((timeoutInMinutes * 60.0) / pollingInterval).toInt()
                var pollCount = 0
                var syncCheckCount = 0
                config.logger.info("Waiting for test run to complete...")
                config.logger.info("Polling interval: $pollingInterval seconds")

                var status: TestRunStatus? = null
                while (true) {
                    syncCheckCount++
                    status = api.getTestRunStatus(testRun.id)
                    if (syncCheckCount > 5 && status.totalEntries == 0) {
                        config.logger.info("No entries were pushed to the test run. Exiting...")
                        break
                    }
                    val displayMap = status.toDisplayMap()
                    val statusLine = displayMap.entries.filter { it.key != "testRunStatus" }.joinToString(" | ") { "${it.key}: ${it.value}" }
                    val header = " Test run status: ${status.testRunStatus.value} "
                    val boxWidth = maxOf(50, statusLine.length + 4, header.length + 4)
                    config.logger.info("┌${"─".repeat(boxWidth)}┐")
                    config.logger.info("│${header.padStart((boxWidth + header.length) / 2).padEnd(boxWidth)}│")
                    config.logger.info("├${"─".repeat(boxWidth)}┤")
                    config.logger.info("│ ${statusLine.padEnd(boxWidth - 2)} │")
                    config.logger.info("└${"─".repeat(boxWidth)}┘\n")
                    if (pollCount > maxIterations) throw Exception(
                        "Test run is taking over timeout period ($timeoutInMinutes minutes) to complete, please check the report: ${config.baseUrl}/workspace/${config.workspaceId}/testrun/${testRun.id}"
                    )
                    if (status.testRunStatus == RunStatus.FAILED || status.testRunStatus == RunStatus.STOPPED) break
                    if (status.testRunStatus == RunStatus.COMPLETE && allEntriesProcessed.count == 0L &&
                        status.totalEntries != 0 &&
                        status.totalEntries == status.completedEntries + status.failedEntries + status.stoppedEntries) {
                        config.logger.info("All entries processed. Test run completed.")
                        break
                    }
                    delay(pollingInterval * 1000L)
                    pollCount++
                }
                if (status?.testRunStatus == RunStatus.FAILED) throw Exception("Test run failed, please check the report on our web portal: ${config.baseUrl}/workspace/${config.workspaceId}/testrun/${testRun.id}")
                if (status?.testRunStatus == RunStatus.STOPPED) throw Exception("Test run was stopped, please check the report on our web portal: ${config.baseUrl}/workspace/${config.workspaceId}/testrun/${testRun.id}")

                val testRunResult = api.getTestRunFinalResult(testRun.id)
                testRunResult.link = config.baseUrl + testRunResult.link
                config.logger.info("Test run \"${config.name}\" completed successfully!\nView the report here: ${testRunResult.link}")
                return RunResult(testRunResult, failedEntryIndices.toList())
            } catch (e: Exception) {
                config.logger.error("\n\nError while running test: ", e)
                throw e
            }
        } catch (e: Exception) {
            config.logger.error("\n\nError while running test: ", e)
            throw e
        }
    }

    // ─── Preset Resolution ──────────────────────────────────────────────

    private fun resolvePreset(preset: Preset) {
        if (config.data == null && !preset.datasets.isNullOrEmpty()) {
            val ds = preset.datasets[0]
            config.data = if (ds.splitId != null) TestRunData.DatasetId(ds.splitId) else TestRunData.DatasetId(ds.id)
            if (preset.datasets.size > 1) config.logger.info("Preset '${preset.name}' has ${preset.datasets.size} datasets; using '${ds.name}'. Override with .withData().")
        }
        if (config.evaluators.isEmpty() && !preset.evaluators.isNullOrEmpty()) config.evaluators = preset.evaluators.map { it.name }.toMutableList()
        if (config.environmentName == null && preset.environmentName != null) config.environmentName = preset.environmentName
        if (config.simulationConfig == null && preset.simulationConfig != null) {
            var simConfig = preset.simulationConfig
            if (config.workflow == null && simConfig.responseFields != null) simConfig = simConfig.copy(responseFields = null)
            config.simulationConfig = simConfig
        }
        if (!preset.contextToEvaluate.isNullOrEmpty()) {
            val ctxColumn = preset.contextToEvaluate.firstOrNull { it.type == "DATASET_COLUMN" }?.payload
            if (ctxColumn != null) {
                when {
                    config.workflow != null && config.workflow!!.contextToEvaluate == null -> config.workflow!!.contextToEvaluate = ctxColumn
                    config.promptVersion != null && config.promptVersion!!.contextToEvaluate == null -> config.promptVersion!!.contextToEvaluate = ctxColumn
                    config.promptChainVersion != null && config.promptChainVersion!!.contextToEvaluate == null -> config.promptChainVersion!!.contextToEvaluate = ctxColumn
                }
            }
        }
        config.testConfigId = preset.id
    }

    // ─── Run with Local Data ────────────────────────────────────────────

    private suspend fun runTestWithLocalData(
        testRun: TestRun, getRow: (Int) -> Map<String, Any?>?,
        onEntryFailed: (Int) -> Unit, onDatasetFinished: suspend () -> Unit,
        evaluatorNameToIdMap: MutableMap<String, EvaluatorNameToIdAndPassFailCriteria>
    ) = coroutineScope {
        val semaphore = Semaphore(config.concurrency ?: 10)
        val ds = config.dataStructure
        val inputKey = getAllKeysByValue(ds, "INPUT").firstOrNull()
        val expectedOutputKey = getAllKeysByValue(ds, "EXPECTED_OUTPUT").firstOrNull()
        val ctxKey = getAllKeysByValue(ds, "CONTEXT_TO_EVALUATE").firstOrNull()
        val scenarioKey = getAllKeysByValue(ds, "SCENARIO").firstOrNull()
        val stepsKey = getAllKeysByValue(ds, "EXPECTED_STEPS").firstOrNull()

        val hasLocalEvals = config.evaluators.any { it is BaseEvaluator }
        val hasPlatformMapping = config.evaluators.any { it is PlatformEvaluator && it.variableMapping != null }
        val needsProcessing = hasLocalEvals || hasPlatformMapping
            || config.outputFunction != null
            || (config.simulationConfig != null && (config.workflow != null || config.promptVersion != null))

        val jobs = mutableListOf<Job>()
        var index = 0
        while (true) {
            semaphore.acquire()
            val row = getRow(index)
            if (row == null) { semaphore.release(); break }
            val currentIndex = index++
            val job = launch {
                try {
                    val fields = getInputExpectedOutputAndContextFromRow(inputKey, expectedOutputKey, ctxKey, scenarioKey, stepsKey, row)
                    if (needsProcessing) {
                        // createTestRunEntry only when NOT simulation
                        val testRunEntryResponse = if (config.simulationConfig == null) {
                            config.logger.info("[Entry $currentIndex] Creating test run entry...")
                            api.createTestRunEntry(testRun)
                        } else null

                        config.logger.info("[Entry $currentIndex] Processing entry...")
                        val result = processEntry(currentIndex, fields.input, fields.expectedOutput, fields.contextToEvaluate,
                            fields.scenario, fields.expectedSteps, { row }, evaluatorNameToIdMap,
                            entryId = testRunEntryResponse?.get("id"), testRunId = testRun.id, workspaceId = testRun.workspaceId)
                        config.logger.info("[Entry $currentIndex] Processed. Output=${result.entry.output?.take(100)}, localEvalResults=${result.entry.localEvaluationResults?.size ?: 0}")

                        // Push logic
                        val isLocalSim = config.simulationConfig != null && config.outputFunction != null
                        val isSimEndpoint = config.simulationConfig != null && config.outputFunction == null
                        val hasLocalResults = !result.entry.localEvaluationResults.isNullOrEmpty()
                        val shouldPush = !isSimEndpoint || hasLocalResults

                        if (shouldPush) {
                            var pushTestRun: JsonSerializable = testRun
                            if (isSimEndpoint) pushTestRun = testRun.copy(evalConfig = filterEvalConfigToLocal(testRun.evalConfig))
                            val runConfig = if (isLocalSim) null else if (result.meta != null) PushRunConfig(
                                cost = result.meta.cost,
                                usage = result.meta.usage
                            ) else null
                            config.logger.info("[Entry $currentIndex] Pushing entry...")
                            api.pushTestRunEntry(pushTestRun, result.entry, runConfig, if (isLocalSim) true else null)
                            config.logger.info("[Entry $currentIndex] Pushed successfully.")
                        }
                    } else {
                        val variables = if (ds != null) getVariablesFromRow(row, ds) else emptyMap()
                        val entry = TestRunEntry(variables = variables, input = fields.input, expectedOutput = fields.expectedOutput,
                            contextToEvaluate = fields.contextToEvaluate, scenario = fields.scenario, expectedSteps = fields.expectedSteps)
                        api.pushTestRunEntry(testRun, entry)
                    }
                } catch (e: Exception) {
                    config.logger.error("Error at entry [$currentIndex]: ${e.message}", e)
                    onEntryFailed(currentIndex)
                } finally { semaphore.release() }
            }
            jobs.add(job)
        }
        jobs.forEach { it.join() }
        onDatasetFinished()
    }

    // ─── Run with Dataset ID ────────────────────────────────────────────

    private suspend fun runTestWithDatasetId(
        testRun: TestRun, datasetId: String,
        onEntryFailed: (Int) -> Unit, onDatasetFinished: suspend () -> Unit,
        evaluatorNameToIdMap: MutableMap<String, EvaluatorNameToIdAndPassFailCriteria>
    ) = coroutineScope {
        val semaphore = Semaphore(config.concurrency ?: 10)
        val dataStructure = api.getDatasetStructure(datasetId)
        api.attachDatasetToTestRun(testRun.id, datasetId)
        val totalRows = api.getDatasetTotalRows(datasetId)
        val inputKey = getAllKeysByValue(dataStructure, "INPUT").firstOrNull()
        val expectedOutputKey = getAllKeysByValue(dataStructure, "EXPECTED_OUTPUT").firstOrNull()
        val ctxKey = getAllKeysByValue(dataStructure, "CONTEXT_TO_EVALUATE").firstOrNull()
        val scenarioKey = getAllKeysByValue(dataStructure, "SCENARIO").firstOrNull()
        val stepsKey = getAllKeysByValue(dataStructure, "EXPECTED_STEPS").firstOrNull()

        val hasLocalEvals = config.evaluators.any { it is BaseEvaluator }
        val hasPlatformMapping = config.evaluators.any { it is PlatformEvaluator && it.variableMapping != null }
        val needsProcessing = hasLocalEvals || hasPlatformMapping
            || config.outputFunction != null
            || (config.simulationConfig != null && (config.workflow != null || config.promptVersion != null))

        val jobs = mutableListOf<Job>()
        for (index in 0 until totalRows) {
            semaphore.acquire()
            val row = try { api.getDatasetRow(datasetId, index) } catch (e: Exception) {
                config.logger.error("Error fetching row $index: ${e.message}", e)
                semaphore.release(); onEntryFailed(index); continue
            }
            if (row == null) { semaphore.release(); break }
            val currentIndex = index
            val job = launch {
                try {
                    val rowData: Map<String, Any?> = row.data
                    val fields = getInputExpectedOutputAndContextFromRow(inputKey, expectedOutputKey, ctxKey, scenarioKey, stepsKey, rowData)
                    val variables = getVariablesFromRow(rowData, dataStructure)
                    val testRunWithDataset = TestRunWithDatasetEntry(testRun, row.id, datasetId)

                    if (needsProcessing) {
                        val testRunEntryResponse = if (config.simulationConfig == null) api.createTestRunEntry(testRun) else null
                        val result = processEntry(currentIndex, fields.input, fields.expectedOutput, fields.contextToEvaluate,
                            fields.scenario, fields.expectedSteps, { rowData }, evaluatorNameToIdMap,
                            entryId = testRunEntryResponse?.get("id"), testRunId = testRun.id, workspaceId = testRun.workspaceId,
                            datasetEntryId = row.id, dataStructureOverride = dataStructure)

                        val isLocalSim = config.simulationConfig != null && config.outputFunction != null
                        val isSimEndpoint = config.simulationConfig != null && config.outputFunction == null
                        val hasLocalResults = !result.entry.localEvaluationResults.isNullOrEmpty()
                        val shouldPush = !isSimEndpoint || hasLocalResults

                        if (shouldPush) {
                            // Always use TestRunWithDatasetEntry in dataset path
                            val pushObj: JsonSerializable = if (isSimEndpoint) {
                                val filtered = testRun.copy(evalConfig = filterEvalConfigToLocal(testRun.evalConfig))
                                TestRunWithDatasetEntry(filtered, row.id, datasetId)
                            } else {
                                testRunWithDataset
                            }
                            val runConfig = if (isLocalSim) null else if (result.meta != null) PushRunConfig(
                                cost = result.meta.cost,
                                usage = result.meta.usage
                            ) else null
                            api.pushTestRunEntry(pushObj, result.entry, runConfig, if (isLocalSim) true else null)
                        }
                    } else {
                        val entry = TestRunEntry(variables = variables, input = fields.input, expectedOutput = fields.expectedOutput,
                            contextToEvaluate = fields.contextToEvaluate, scenario = fields.scenario, expectedSteps = fields.expectedSteps)
                        api.pushTestRunEntry(testRunWithDataset, entry)
                    }
                } catch (e: Exception) {
                    config.logger.error("Error at entry [$currentIndex]: ${e.message}", e)
                    onEntryFailed(currentIndex)
                } finally { semaphore.release() }
            }
            jobs.add(job)
        }
        jobs.forEach { it.join() }
        onDatasetFinished()
    }

    // ─── Process Entry (6 branches) ─────────────────────────────────────

    private suspend fun processEntry(
        index: Int, input: String?, expectedOutput: String?, contextToEvaluate: Any?,
        scenario: String?, expectedSteps: String?, getRow: (Int) -> Map<String, Any?>?,
        evaluatorNameToIdMap: MutableMap<String, EvaluatorNameToIdAndPassFailCriteria>,
        entryId: String? = null, testRunId: String? = null, workspaceId: String? = null,
        datasetEntryId: String? = null, dataStructureOverride: DataStructure? = null
    ): ProcessedEntry {
        val row = getRow(index) ?: throw IllegalArgumentException("Dataset entry $index is missing")
        val dataStructure = dataStructureOverride ?: config.dataStructure
        val variables = if (dataStructure != null) getVariablesFromRow(row, dataStructure) else emptyMap()

        var yieldedOutput: YieldedOutput? = null

        // Branch 1: simulation + outputFunction → local simulation loop
        if (config.simulationConfig != null && config.outputFunction != null) {
            config.logger.info("  Running local simulation...")
            yieldedOutput = runSimulationWithLocalOutput(row, testRunId!!, workspaceId!!, datasetEntryId,
                input, scenario, expectedSteps, contextToEvaluate, config.outputFunction!!)
        }
        // Branch 2: outputFunction only (no simulation)
        else if (config.outputFunction != null) {
            config.logger.info("  Calling yields_output function...")
            yieldedOutput = config.outputFunction!!(row, null)
        }
        // Branch 3: workflow + simulation → server-side simulation
        else if (config.workflow != null && config.simulationConfig != null) {
            config.logger.info("  Starting workflow simulation...")
            val dataEntry = convertDataEntryToVariableFormat(row)
            val ctxForSim = contextToEvaluate ?: config.workflow!!.contextToEvaluate
            val startResp = api.executeSimulationWorkflowStart(testRunId!!, config.workflow!!.id, workspaceId!!,
                config.simulationConfig!!, datasetEntryId, input, scenario, expectedSteps, ctxForSim, dataEntry, config.environmentName)
            val simOutput = pollSimulationResult(
                { api.getSimulationWorkflowStatus(startResp.workspaceId, startResp.testRunEntryId) },
                { ExecuteSimulationWorkflowForDataResponse.fromJsonObject(it) }
            )
            val outputs = simOutput.outputs ?: emptyList()
            yieldedOutput = YieldedOutput(
                data = outputs.lastOrNull() ?: simOutput.output ?: "",
                simulationOutputs = outputs.ifEmpty { null },
                retrievedContextToEvaluate = simOutput.contextToEvaluate,
                messages = simOutput.messages,
                simulationMeta = SimulationMeta(sessionId = simOutput.sessionId, simulationId = simOutput.simulationId,
                    messages = simOutput.messages ?: emptyList(), testRunEntryId = simOutput.testRunEntryId),
                meta = YieldedOutputMeta(entityType = "WORKFLOW", entityId = config.workflow!!.id, usage = simOutput.usage, cost = simOutput.cost)
            )
        }
        // Branch 4: prompt + simulation → server-side simulation
        else if (config.promptVersion != null && config.simulationConfig != null) {
            config.logger.info("  Starting prompt simulation...")
            val dataEntry = convertDataEntryToVariableFormat(row)
            val ctxForSim = contextToEvaluate ?: config.promptVersion!!.contextToEvaluate
            val startResp = api.executeSimulationPromptStart(testRunId!!, config.promptVersion!!.id, workspaceId!!,
                config.simulationConfig!!, datasetEntryId, input, scenario, expectedSteps, ctxForSim, dataEntry, config.environmentName)
            val simOutput = pollSimulationResult(
                { api.getSimulationPromptStatus(startResp.workspaceId, startResp.testRunEntryId) },
                { ExecuteSimulationPromptForDataResponse.fromJsonObject(it) }
            )
            val outputs = simOutput.outputs ?: emptyList()
            yieldedOutput = YieldedOutput(
                data = outputs.lastOrNull() ?: simOutput.output ?: "",
                simulationOutputs = outputs.ifEmpty { null },
                retrievedContextToEvaluate = simOutput.contextToEvaluate,
                messages = simOutput.messages,
                simulationMeta = SimulationMeta(sessionId = simOutput.sessionId, simulationId = simOutput.simulationId,
                    messages = simOutput.messages ?: emptyList(), testRunEntryId = simOutput.testRunEntryId),
                meta = YieldedOutputMeta(entityType = "PROMPT", entityId = config.promptVersion!!.id, usage = simOutput.usage, cost = simOutput.cost)
            )
        }
        // Branch 5-7: existing entity execution (no simulation, no outputFunction)
        else {
            yieldedOutput = executeEntity(row, dataStructure, DataRowFields(input, expectedOutput, contextToEvaluate, scenario, expectedSteps), variables)
        }

        // Run local evaluations
        val localEvaluators = config.evaluators.filterIsInstance<BaseEvaluator>()
        var localEvaluationResults: List<LocalEvaluationResultWithId>? = null
        if (localEvaluators.isNotEmpty() && yieldedOutput != null) {
            val processedData = LocalEvaluatorResultParameter(
                output = yieldedOutput.data, input = input, expectedOutput = expectedOutput,
                contextToEvaluate = contextToEvaluate, simulationOutputs = yieldedOutput.simulationOutputs
            )
            val results = runLocalEvaluations(localEvaluators, row, processedData)
            localEvaluationResults = results.map {
                LocalEvaluationResultWithId(evaluatorNameToIdMap[it.name]?.id ?: "", it.result, it.name, it.passFailCriteria, it.output)
            }
        }

        // Compute sdk_variables for platform evaluators
        val sdkVariables = if (yieldedOutput != null) computeSdkVariablesForPlatformEvaluators(yieldedOutput, row, input, contextToEvaluate, evaluatorNameToIdMap) else null

        // For simulation endpoint entries, use the testRunEntryId from the simulation response
        val effectiveEntryId = entryId ?: yieldedOutput?.simulationMeta?.testRunEntryId

        val entry = TestRunEntry(id = effectiveEntryId, variables = variables, output = yieldedOutput?.data, input = input,
            expectedOutput = expectedOutput, contextToEvaluate = contextToEvaluate, scenario = scenario,
            expectedSteps = expectedSteps, simulationMeta = yieldedOutput?.simulationMeta,
            localEvaluationResults = localEvaluationResults, sdkVariables = sdkVariables)
        return ProcessedEntry(entry, yieldedOutput?.meta)
    }

    // ─── Entity Execution (branches 5-7) ────────────────────────────────

    private suspend fun executeEntity(row: Map<String, Any?>, dataStructure: DataStructure?, fields: DataRowFields, variables: Map<String, TestRunVariable>): YieldedOutput? {
        if (config.promptVersion != null) {
            config.logger.info("  Executing prompt version ${config.promptVersion!!.id}...")
            val r = api.executePromptForData(config.promptVersion!!.id, fields.input ?: "", variables, config.promptVersion!!.contextToEvaluate)
            return YieldedOutput(data = r.output ?: "", retrievedContextToEvaluate = r.contextToEvaluate ?: config.promptVersion!!.contextToEvaluate,
                meta = YieldedOutputMeta("PROMPT", config.promptVersion!!.id, r.usage, r.cost))
        } else if (config.promptChainVersion != null) {
            config.logger.info("  Executing prompt chain version ${config.promptChainVersion!!.id}...")
            val r = api.executePromptChainForData(config.promptChainVersion!!.id, fields.input ?: "", variables, config.promptChainVersion!!.contextToEvaluate)
            return YieldedOutput(data = r.output ?: "", retrievedContextToEvaluate = r.contextToEvaluate ?: config.promptChainVersion!!.contextToEvaluate,
                meta = YieldedOutputMeta("PROMPT_CHAIN", config.promptChainVersion!!.id, r.usage, r.cost))
        } else if (config.workflow != null) {
            config.logger.info("  Executing workflow ${config.workflow!!.id}...")
            val r = api.executeWorkflowForData(config.workflow!!.id, row, config.workflow!!.contextToEvaluate)
            return YieldedOutput(data = r.output ?: "", retrievedContextToEvaluate = r.contextToEvaluate,
                meta = YieldedOutputMeta("WORKFLOW", config.workflow!!.id, YieldedOutputTokenUsage(0, 0, 0, r.latency)))
        }
        return null
    }

    // ─── Local Simulation Loop ──────────────────────────────────────────

    private suspend fun runSimulationWithLocalOutput(
        row: Map<String, Any?>, testRunId: String, workspaceId: String, datasetEntryId: String?,
        inputVal: String?, scenario: String?, expectedSteps: String?, contextToEvaluate: Any?,
        outputFunction: (Map<String, Any?>, SimulationContext?) -> YieldedOutput
    ): YieldedOutput {
        val simConfig = config.simulationConfig ?: throw IllegalStateException("simulationConfig is required")
        if (simConfig.stopTrigger != null) {
            require(simConfig.stopTrigger is Map<*, *>) { "stop_trigger must be a map" }
            val field = simConfig.stopTrigger["field"]
            require(field is String && field.isNotBlank()) { "stop_trigger.field must be a non-empty string" }
        }
        val maxTurns = simConfig.maxTurns ?: 10
        val conversationHistory = mutableListOf<SimulationConversationTurn>()
        val simulationOutputs = mutableListOf<String>()
        var testRunEntryId: String? = null
        var sessionId: String? = null
        var simulationId: String? = null
        var stopReason: String? = null
        var isComplete = false
        var turnNumber = 0
        var totalPromptTokens = 0; var totalCompletionTokens = 0; var totalTokens = 0
        var totalInputCost = 0.0; var totalOutputCost = 0.0; var totalCost = 0.0

        // Resolve persona: dataset column > simulation config
        var resolvedPersona: Any? = null
        for ((key, value) in row) {
            if (key.lowercase() == "persona" && value != null) {
                val s = value.toString().trim()
                if (s.isNotEmpty()) { resolvedPersona = s; break }
            }
        }
        if (resolvedPersona == null && simConfig.persona != null) {
            resolvedPersona = when (val p = simConfig.persona) {
                is String -> p
                is Map<*, *> -> if (p["type"] == "DATASET_COLUMN") {
                    val col = p["payload"]?.toString() ?: ""
                    if (col.isNotEmpty()) row[col]?.toString()?.trim()?.ifEmpty { null } else null
                } else p
                else -> p
            }
        }
        val resolvedSimConfig = simConfig.copy(maxTurns = maxTurns, persona = resolvedPersona)
        val variableEntry = convertDataEntryToVariableFormat(row)

        try {
            val startTime = System.currentTimeMillis()
            while (turnNumber < maxTurns && !isComplete) {
                turnNumber++
                var entryPayload: SimulationEntry? = null
                if (turnNumber == 1) {
                    entryPayload = SimulationEntry(
                        input = inputVal,
                        scenario = scenario,
                        expectedSteps = expectedSteps,
                        contextToEvaluate = contextToEvaluate,
                        dataEntry = variableEntry,
                        persona = resolvedPersona as? String
                    )
                }
                val turnResult = api.executeSimulationLocalExecution(
                    testRunId, workspaceId, resolvedSimConfig,
                    if (turnNumber == 1) datasetEntryId else null, entryPayload,
                    if (turnNumber > 1) conversationHistory else null, testRunEntryId
                )
                if (turnNumber == 1) {
                    testRunEntryId = turnResult.testRunEntryId ?: throw Exception("testRunEntryId required on first turn")
                    sessionId = turnResult.sessionId; simulationId = turnResult.simulationId
                }
                turnResult.usage?.let { totalPromptTokens += it.promptTokens; totalCompletionTokens += it.completionTokens; totalTokens += it.totalTokens }
                turnResult.cost?.let { totalInputCost += it.inputCost; totalOutputCost += it.outputCost; totalCost += it.totalCost }
                if (turnResult.stopReason != null) { stopReason = turnResult.stopReason; config.logger.info("Simulation stopped: $stopReason"); isComplete = true; break }
                if (turnResult.isComplete) { isComplete = true; break }

                val simContext = SimulationContext(conversationHistory.toList(), turnResult.userInput ?: emptyMap(), turnNumber, totalCost, totalTokens)
                val assistantOutput = outputFunction(row, simContext)
                simulationOutputs.add(assistantOutput.data)
                val normalizedRequest = mapOf<String, Any?>("input" to (turnResult.userInput?.get("input")?.toString() ?: ""))
                val response = mapOf<String, Any?>("output" to assistantOutput.data, "tool_calls" to (assistantOutput.toolCalls ?: emptyList<Any>()))
                conversationHistory.add(SimulationConversationTurn(turnNumber, normalizedRequest, response))

                if (simConfig.stopTrigger != null) {
                    val field = simConfig.stopTrigger["field"]?.toString() ?: ""
                    val fieldValue = getNestedFieldValue(assistantOutput, field)
                    if (fieldValue == simConfig.stopTrigger["value"]) { isComplete = true; break }
                }
            }
            val totalLatency = (System.currentTimeMillis() - startTime).toDouble()
            val lastTurn = conversationHistory.lastOrNull()?.let { mapOf<String, Any?>("turn" to it.turn, "request" to it.request, "response" to it.response) }
            return YieldedOutput(
                data = simulationOutputs.lastOrNull() ?: "", simulationOutputs = simulationOutputs,
                simulationMeta = SimulationMeta(sessionId = sessionId, simulationId = simulationId, testRunEntryId = testRunEntryId,
                    messages = conversationHistory, lastTurn = lastTurn, stopReason = stopReason,
                    usage = YieldedOutputTokenUsage(totalPromptTokens, totalCompletionTokens, totalTokens, totalLatency),
                    cost = YieldedOutputCost(totalInputCost, totalOutputCost, totalCost))
            )
        } catch (e: Exception) {
            if (testRunEntryId != null) try { api.updateSimulationStatus(testRunEntryId, "FAILED") } catch (_: Exception) {}
            throw e
        }
    }

    // ─── Simulation Polling ─────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> pollSimulationResult(fetchStatus: suspend () -> JsonObject, parseData: (JsonObject) -> T): T {
        val pollingInterval = calculatePollingInterval(SIMULATION_POLL_TIMEOUT_MINUTES)
        val maxIterations = ceil((SIMULATION_POLL_TIMEOUT_MINUTES * 60.0) / pollingInterval).toInt()
        for (i in 0 until maxIterations) {
            val resp = fetchStatus()
            val status = resp["status"]?.jsonPrimitive?.contentOrNull ?: resp["runStatus"]?.jsonPrimitive?.contentOrNull ?: resp["run_status"]?.jsonPrimitive?.contentOrNull
            if (status == RunStatus.FAILED.value) throw Exception("Simulation failed")
            if (status == RunStatus.COMPLETE.value || status == RunStatus.STOPPED.value) {
                val data = resp["data"]?.jsonObject ?: buildJsonObject {
                    resp.forEach { (k, v) -> if (k !in setOf("status", "runStatus", "run_status")) put(k, v) }
                }
                if (data.isEmpty()) throw Exception("Simulation completed but no data returned")
                return parseData(data)
            }
            delay(pollingInterval * 1000L)
        }
        throw Exception("Simulation did not complete within $SIMULATION_POLL_TIMEOUT_MINUTES minutes")
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun computeSdkVariablesForPlatformEvaluators(
        yieldedOutput: YieldedOutput, row: Map<String, Any?>, inputValue: String?, contextToEvaluate: Any?,
        evaluatorNameToIdMap: Map<String, EvaluatorNameToIdAndPassFailCriteria>
    ): Map<String, Map<String, String>>? {
        val sdkVariables = mutableMapOf<String, Map<String, String>>()
        val vmi = VariableMappingInput(data = yieldedOutput.data, retrievedContextToEvaluate = yieldedOutput.retrievedContextToEvaluate ?: contextToEvaluate)
        val vi: VersionInfo? = when {
            config.workflow != null -> VersionInfo(config.workflow!!.id, "workflow")
            config.promptVersion != null -> VersionInfo(config.promptVersion!!.id, "prompt")
            config.promptChainVersion != null -> VersionInfo(config.promptChainVersion!!.id, "promptChain")
            else -> null
        }
        for (evaluator in config.evaluators) {
            val variableMapping: VariableMapping?
            val evaluatorName: String?
            when {
                evaluator is BaseEvaluator && evaluator.variableMapping != null -> {
                    variableMapping = evaluator.variableMapping
                    evaluatorName = evaluator.names.firstOrNull()
                }
                evaluator is PlatformEvaluator && evaluator.variableMapping != null -> {
                    variableMapping = evaluator.variableMapping
                    evaluatorName = evaluator.name
                }
                else -> continue
            }
            if (evaluatorName != null) {
                val info = evaluatorNameToIdMap[evaluatorName] ?: continue
                val result = mutableMapOf<String, String>()
                for ((k, fn) in variableMapping) {
                    try { fn(vmi, row, vi)?.let { result[k] = it } } catch (e: Exception) { config.logger.error("Error in variable mapping '$k': ${e.message}", e) }
                }
                if (result.isNotEmpty()) sdkVariables[info.id] = result
            }
        }
        return sdkVariables.ifEmpty { null }
    }

    private fun filterEvalConfigToLocal(evalConfig: JsonObject): JsonObject {
        val evals = evalConfig["evals"]?.jsonArray ?: return evalConfig
        val localOnly = evals.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == EvaluatorType.LOCAL.value }
        return buildJsonObject {
            evalConfig.forEach { (key, value) ->
                if (key == "evals") put("evals", buildJsonArray { localOnly.forEach { add(it) } })
                else put(key, value)
            }
        }
    }

    companion object {
        private const val SIMULATION_POLL_TIMEOUT_MINUTES = 30

        fun getNestedFieldValue(obj: Any?, fieldPath: String): Any? {
            val keys = fieldPath.split(".")
            var value: Any? = obj
            for (key in keys) {
                value = when (value) {
                    is Map<*, *> -> value[key]
                    else -> try { val f = value!!::class.java.getDeclaredField(key); f.isAccessible = true; f.get(value) } catch (_: Exception) { null }
                }
                if (value == null) return null
            }
            return value
        }

        fun calculatePollingInterval(timeoutMinutes: Int, isAiEvaluator: Boolean = false): Int {
            val points = listOf(10 to 5, 15 to 5, 30 to 10, 60 to 15, 120 to 30, 1440 to 120)
            var lp = points[0]; var up = points.last()
            for (i in 0 until points.size - 1) { if (points[i].first <= timeoutMinutes && timeoutMinutes <= points[i + 1].first) { lp = points[i]; up = points[i + 1]; break } }
            val (x1, y1) = lp; val (x2, y2) = up
            if (x1 == x2) return y1
            val t = (timeoutMinutes.toDouble() - x1) / (x2 - x1)
            val interpolated = y1 + (y2 - y1) * t.pow(2.0)
            return interpolated.roundToInt().coerceIn(if (isAiEvaluator) 15 else 5, 120)
        }
    }
}
