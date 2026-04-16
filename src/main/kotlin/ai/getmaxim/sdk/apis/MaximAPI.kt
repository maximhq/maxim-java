package ai.getmaxim.sdk.apis

import ai.getmaxim.sdk.models.*
import org.slf4j.LoggerFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.net.URLEncoder

class MaximAPI {
    companion object {
        internal val logger = LoggerFactory.getLogger(MaximAPI::class.java)
        internal val client = HttpClient(CIO) {
            install(HttpTimeout)
        }

        private suspend inline fun <reified T> call(
            url: String,
            method: String,
            apiKey: String,
            headers: Map<String, String>? = null,
            payload: String? = null,
        ): T {
            return MaximJson.decodeFromString(client.request(url) {
                this.method = HttpMethod.parse(method)
                header("x-maxim-api-key", apiKey)
                headers?.forEach { (key, value) ->
                    header(key, value)
                }
                // Only set body for non-GET requests
                if (method.uppercase() != "GET" && payload != null) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }

                timeout {
                    connectTimeoutMillis = 30000
                    requestTimeoutMillis = 60000
                }
            }.bodyAsText())
        }

        suspend fun getPrompt(baseUrl: String, apiKey: String, id: String): PromptVersionsAndRules? {
            val requestUrl = "$baseUrl/api/sdk/v4/prompts?promptId=$id"
            val response: MaximApiPromptResponse = call(requestUrl, "GET", apiKey)
            return response.data
        }

        suspend fun getPrompts(
            baseUrl: String,
            apiKey: String,
        ): List<PromptWithVersionsAndRules> {
            val response: MaximApiPromptsResponse = call("$baseUrl/api/sdk/v4/prompts", "GET", apiKey)
            return response.data
        }

        suspend fun getPromptChain(baseUrl: String, apiKey: String, id: String): PromptChainWithVersionsAndRules? {
            val requestUrl = "$baseUrl/api/sdk/v4/prompt-chains?promptChainId=$id"
            val response: MaximApiPromptChainResponse = call(requestUrl, "GET", apiKey)
            return response.data
        }

        suspend fun getPromptChains(
            baseUrl: String,
            apiKey: String,
        ): List<PromptChainWithVersionAndRulesAndId> {
            val response: MaximApiPromptChainsResponse = call("$baseUrl/api/sdk/v4/prompt-chains", "GET", apiKey)
            return response.data
        }

        suspend fun getFolder(baseUrl: String, apiKey: String, id: String): Folder? {
            val response: MaximFolderResponse = call("$baseUrl/api/sdk/v3/folders?folderId=$id", "GET", apiKey)
            if (response.error != null) {
                return null
            }
            return response.data
        }

        suspend fun getFolders(baseUrl: String, apiKey: String): List<Folder> {
            val response: MaximFoldersResponse = call("$baseUrl/api/sdk/v3/folders", "GET", apiKey)
            return response.data
        }

        suspend fun addDatasetEntries(
            baseUrl: String,
            apiKey: String,
            datasetId: String,
            datasetEntries: List<DatasetEntry>,
        ) {
            val body = MaximJson.encodeToString(AddDatasetEntriesPayload(datasetId, datasetEntries))
            val response: MaximAPIResponse = call(
                "$baseUrl/api/sdk/v3/datasets/entries",
                "POST",
                apiKey,
                mapOf("Content-Type" to "application/json"),
                body
            )
            if (response.error != null) throw Exception(response.error.message)
        }

        suspend fun doesLogRepositoryExist(baseUrl: String, apiKey: String, loggerId: String): Boolean {
            return try {
                val resp =
                    call<MaximAPIResponse>("$baseUrl/api/sdk/v3/log-repositories?loggerId=$loggerId", "GET", apiKey)
                true
            } catch (e: Exception) {
                false
            }
        }

        suspend fun pushLogs(baseUrl: String, apiKey: String, repositoryId: String, logs: String) {
            val response: MaximAPIResponse = call(
                "$baseUrl/api/sdk/v3/log?id=$repositoryId",
                "POST",
                apiKey,
                mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                logs
            )
            if (response.error != null) throw Exception(response.error.message)
        }

        suspend fun getUploadUrl(
            baseUrl: String,
            apiKey: String,
            key: String,
            mimeType: String,
            size: Int
        ): SignedUrlResponse {
            val response: MaximAPIResponse = call(
                "$baseUrl/api/sdk/v1/log-repositories/attachments/upload-url?key=$key&mimeType=$mimeType&size=$size",
                "GET",
                apiKey,
                mapOf("Accept" to "application/json"),
            )
            if (response.error != null) throw Exception(response.error.message)
            return response.data as SignedUrlResponse
        }

        suspend fun uploadToSignedUrl(url: String, data: ByteArray, mimeType: String) {
            val response = client.put(url) {
                setBody(data)
                header(HttpHeaders.ContentType, mimeType)
                header(HttpHeaders.ContentLength, data.size.toString())
            }
            if (response.status != HttpStatusCode.OK) throw Exception(response.bodyAsText())
        }
    }

    /**
     * Instance-based API for test run operations.
     * Reuses the shared Ktor HttpClient from the companion object.
     */
    class TestRunAPI(private val baseUrl: String, private val apiKey: String) {

        private suspend fun callGet(url: String): JsonObject {
            val responseText = client.request(url) {
                method = HttpMethod.Get
                header("x-maxim-api-key", apiKey)
                timeout {
                    connectTimeoutMillis = 30000
                    requestTimeoutMillis = 60000
                }
            }.bodyAsText()
            logger.debug("GET {} -> {}", url)
            return MaximJson.decodeFromString<JsonObject>(responseText)
        }

        private suspend fun callPost(url: String, body: JsonObject): JsonObject {
            val bodyStr = body.toString()
            val responseText = client.request(url) {
                method = HttpMethod.Post
                header("x-maxim-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(bodyStr)
                timeout {
                    connectTimeoutMillis = 30000
                    requestTimeoutMillis = 120000
                }
            }.bodyAsText()
            logger.debug("POST {} -> {}", url)
            return MaximJson.decodeFromString<JsonObject>(responseText)
        }

        private fun checkError(response: JsonObject) {
            val error = response["error"]?.jsonObject
            if (error != null) {
                val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown API error"
                logger.error("API error: {} | full response: {}", message, response.toString().take(1000))
                throw Exception(message)
            }
        }

        // ─── Test Run CRUD ──────────────────────────────────────────────

        suspend fun createTestRun(
            name: String,
            workspaceId: String,
            runType: RunType,
            workflowId: String? = null,
            promptVersionId: String? = null,
            promptChainVersionId: String? = null,
            evaluatorConfig: List<Evaluator>,
            requiresLocalRun: Boolean,
            tags: List<String>? = null,
            humanEvaluationConfig: HumanEvaluationConfig? = null,
            connectedRepoId: String? = null,
            testConfigId: String? = null,
            simulationConfig: SimulationConfig? = null
        ): TestRun {
            val body = buildJsonObject {
                put("name", name)
                put("workspaceId", workspaceId)
                put("runType", runType.value)
                if (workflowId != null) put("workflowId", workflowId)
                if (promptVersionId != null) put("promptVersionId", promptVersionId)
                if (promptChainVersionId != null) put("promptChainVersionId", promptChainVersionId)
                put("evaluatorConfig", buildJsonArray {
                    evaluatorConfig.forEach { add(it.toJsonObject()) }
                })
                put("requiresLocalRun", requiresLocalRun)
                if (tags != null) put("tags", buildJsonArray { tags.forEach { add(it) } })
                if (humanEvaluationConfig != null) put("humanEvaluationConfig", humanEvaluationConfig.toJsonObject())
                if (connectedRepoId != null) put("connectedRepoId", connectedRepoId)
                if (testConfigId != null) put("testConfigId", testConfigId)
                if (simulationConfig != null) put("simulationConfig", simulationConfig.toJsonObject())
            }
            val response = callPost("$baseUrl/api/sdk/v2/test-run/create", body)
            checkError(response)
            return TestRun.fromJsonObject(response["data"]!!.jsonObject)
        }

        suspend fun createTestRunEntry(testRun: TestRun): Map<String, String> {
            val body = buildJsonObject {
                put("testRun", testRun.toJsonObject())
            }
            val response = callPost("$baseUrl/api/sdk/v1/test-run/test-run-entry/create", body)
            checkError(response)
            val data = response["data"]!!.jsonObject
            return data.mapValues { it.value.jsonPrimitive.content }
        }

        suspend fun pushTestRunEntry(
            testRun: JsonSerializable,
            entry: TestRunEntry,
            runConfig: PushRunConfig? = null,
            localSimulation: Boolean? = null
        ) {
            val body = buildJsonObject {
                put("testRun", testRun.toJsonObject())
                put("entry", entry.toJsonObject())
                if (runConfig != null) put("runConfig", runConfig.toJsonObject())
                if (localSimulation == true) put("localSimulation", true)
            }
            val response = callPost("$baseUrl/api/sdk/v4/test-run/push", body)
            checkError(response)
        }

        suspend fun markTestRunProcessed(testRunId: String) {
            val body = buildJsonObject { put("testRunId", testRunId) }
            val response = callPost("$baseUrl/api/sdk/v1/test-run/mark-processed", body)
            checkError(response)
        }

        suspend fun markTestRunFailed(testRunId: String) {
            val body = buildJsonObject { put("testRunId", testRunId) }
            val response = callPost("$baseUrl/api/sdk/v1/test-run/mark-failed", body)
            checkError(response)
        }

        suspend fun getTestRunStatus(testRunId: String): TestRunStatus {
            val response = callGet("$baseUrl/api/sdk/v1/test-run/status?testRunId=$testRunId")
            checkError(response)
            val data = response["data"]!!.jsonObject
            // The API returns entryStatus + testRunStatus
            val entryStatus = data["entryStatus"]?.jsonObject ?: data
            val statusObj = buildJsonObject {
                put("total", entryStatus["total"] ?: JsonPrimitive(0))
                put("running", entryStatus["running"] ?: JsonPrimitive(0))
                put("queued", entryStatus["queued"] ?: JsonPrimitive(0))
                put("failed", entryStatus["failed"] ?: JsonPrimitive(0))
                put("completed", entryStatus["completed"] ?: JsonPrimitive(0))
                put("stopped", entryStatus["stopped"] ?: JsonPrimitive(0))
                put("testRunStatus", data["testRunStatus"] ?: JsonPrimitive("QUEUED"))
            }
            return TestRunStatus.fromJsonObject(statusObj)
        }

        suspend fun getTestRunFinalResult(testRunId: String): TestRunResult {
            val response = callGet("$baseUrl/api/sdk/v1/test-run/result?testRunId=$testRunId")
            checkError(response)
            return TestRunResult.fromJsonObject(response["data"]!!.jsonObject)
        }

        // ─── Evaluator / Preset ─────────────────────────────────────────

        suspend fun fetchPlatformEvaluator(name: String, workspaceId: String): Evaluator {
            val encodedName = URLEncoder.encode(name, "UTF-8")
            val response = callGet("$baseUrl/api/sdk/v1/evaluators?name=$encodedName&workspaceId=$workspaceId")
            checkError(response)
            return Evaluator.fromJsonObject(response["data"]!!.jsonObject)
        }

        suspend fun fetchPreset(
            name: String,
            workspaceId: String,
            entityId: String,
            entityType: String
        ): Preset {
            val encodedName = URLEncoder.encode(name, "UTF-8")
            val response = callGet(
                "$baseUrl/api/sdk/v1/test-configs?name=$encodedName&workspaceId=$workspaceId&entityId=$entityId&entityType=$entityType"
            )
            checkError(response)
            return Preset.fromJsonObject(response["data"]!!.jsonObject)
        }

        // ─── Dataset ────────────────────────────────────────────────────

        suspend fun attachDatasetToTestRun(testRunId: String, datasetId: String) {
            val body = buildJsonObject {
                put("testRunId", testRunId)
                put("datasetId", datasetId)
            }
            val response = callPost("$baseUrl/api/sdk/v1/test-run/attach-dataset", body)
            checkError(response)
        }

        suspend fun getDatasetStructure(datasetId: String): Map<String, String> {
            val response = callGet("$baseUrl/api/sdk/v1/datasets/structure?datasetId=$datasetId")
            checkError(response)
            val data = response["data"]!!.jsonObject
            return data.mapValues { it.value.jsonPrimitive.content }
        }

        suspend fun getDatasetTotalRows(datasetId: String): Int {
            val response = callGet("$baseUrl/api/sdk/v1/datasets/total-rows?datasetId=$datasetId")
            checkError(response)
            return response["data"]!!.jsonPrimitive.int
        }

        suspend fun getDatasetRow(datasetId: String, row: Int): DatasetRow? {
            val response = callGet("$baseUrl/api/sdk/v2/datasets/row?datasetId=$datasetId&row=$row")
            checkError(response)
            val data = response["data"]
            if (data == null || data is JsonNull) return null
            return DatasetRow.fromJsonObject(data.jsonObject)
        }

        // ─── Execution ──────────────────────────────────────────────────

        suspend fun executeWorkflowForData(
            workflowId: String,
            dataEntry: Map<String, Any?>,
            contextToEvaluate: String? = null
        ): ExecuteWorkflowResponse {
            val body = buildJsonObject {
                put("workflowId", workflowId)
                put("dataEntry", buildJsonObject {
                    dataEntry.forEach { (key, value) ->
                        when (value) {
                            null -> put(key, JsonNull)
                            is String -> put(key, value)
                            is List<*> -> put(key, buildJsonArray { value.forEach { add(it.toString()) } })
                            else -> put(key, value.toString())
                        }
                    }
                })
                if (contextToEvaluate != null) put("contextToEvaluate", contextToEvaluate)
            }
            val response = callPost("$baseUrl/api/sdk/v1/test-run/execute/workflow", body)
            checkError(response)
            return ExecuteWorkflowResponse.fromJsonObject(response["data"]!!.jsonObject)
        }

        suspend fun executePromptForData(
            promptVersionId: String,
            input: String,
            variables: Map<String, TestRunVariable>,
            contextToEvaluate: String? = null
        ): ExecutePromptResponse {
            val body = buildJsonObject {
                put("promptVersionId", promptVersionId)
                put("input", input)
                put("dataEntry", buildJsonObject {
                    variables.forEach { (key, variable) ->
                        // Convert to v2 execute prompt format: {type: "text", payload: {text: value}}
                        if (variable.type == "text") {
                            put(key, buildJsonObject {
                                put("type", "text")
                                put("payload", buildJsonObject {
                                    put("text", variable.payload.toString())
                                })
                            })
                        } else {
                            put(key, variable.toJsonObject())
                        }
                    }
                })
                if (contextToEvaluate != null) put("contextToEvaluate", contextToEvaluate)
            }
            val response = callPost("$baseUrl/api/sdk/v2/test-run/execute/prompt", body)
            checkError(response)
            return ExecutePromptResponse.fromJsonObject(response["data"]!!.jsonObject)
        }

        suspend fun executePromptChainForData(
            promptChainVersionId: String,
            input: String,
            variables: Map<String, TestRunVariable>,
            contextToEvaluate: String? = null
        ): ExecutePromptResponse {
            val body = buildJsonObject {
                put("promptChainVersionId", promptChainVersionId)
                put("input", input)
                put("dataEntry", buildJsonObject {
                    variables.forEach { (key, variable) ->
                        if (variable.type == "text") {
                            put(key, buildJsonObject {
                                put("type", "text")
                                put("payload", buildJsonObject {
                                    put("text", variable.payload.toString())
                                })
                            })
                        } else {
                            put(key, variable.toJsonObject())
                        }
                    }
                })
                if (contextToEvaluate != null) put("contextToEvaluate", contextToEvaluate)
            }
            val response = callPost("$baseUrl/api/sdk/v2/test-run/execute/prompt-chain", body)
            checkError(response)
            return ExecutePromptResponse.fromJsonObject(response["data"]!!.jsonObject)
        }

        suspend fun runPromptVersion(
            promptVersionId: String,
            input: String,
            imageUrls: List<ImageURL>? = null,
            variables: Map<String, String>? = null
        ): JsonObject? {
            val body = buildJsonObject {
                put("type", "maxim")
                put("promptVersionId", promptVersionId)
                put("input", input)
                if (imageUrls != null) put("imageUrls", buildJsonArray {
                    imageUrls.forEach { add(it.toJsonObject()) }
                })
                if (variables != null) put("variables", buildJsonObject {
                    variables.forEach { (k, v) -> put(k, v) }
                })
            }
            val response = callPost("$baseUrl/api/sdk/v4/prompts/run", body)
            checkError(response)
            return response["data"]?.jsonObject
        }

        suspend fun runPromptChainVersion(
            promptChainVersionId: String,
            input: String,
            variables: Map<String, String>? = null
        ): JsonObject? {
            val body = buildJsonObject {
                put("versionId", promptChainVersionId)
                put("input", input)
                put("variables", buildJsonObject {
                    variables?.forEach { (k, v) -> put(k, v) }
                })
            }
            val response = callPost("$baseUrl/api/sdk/v4/agents/run", body)
            checkError(response)
            return response["data"]?.jsonObject
        }

        // ─── Simulation ─────────────────────────────────────────────────

        private suspend fun executeSimulationStart(
            entityType: String,
            entityId: String,
            testRunId: String,
            workspaceId: String,
            simulationConfig: SimulationConfig,
            datasetEntryId: String? = null,
            input: String? = null,
            scenario: String? = null,
            expectedSteps: String? = null,
            contextToEvaluate: Any? = null,
            dataEntry: JsonObject? = null,
            environmentName: String? = null
        ): ExecuteSimulationStartResponse {
            val entityKey = if (entityType == "prompt") "promptVersionId" else "workflowId"
            val body = buildJsonObject {
                put("testRunId", testRunId)
                put(entityKey, entityId)
                put("workspaceId", workspaceId)
                put("simulationConfig", simulationConfig.toJsonObject())
                if (datasetEntryId != null) put("datasetEntryId", datasetEntryId)
                if (environmentName != null) put("environmentName", environmentName)
                val entry = buildJsonObject {
                    if (input != null) put("input", input)
                    if (scenario != null) put("scenario", scenario)
                    if (expectedSteps != null) put("expectedSteps", expectedSteps)
                    if (contextToEvaluate != null) {
                        when (contextToEvaluate) {
                            is String -> if (contextToEvaluate.isNotBlank()) put("contextToEvaluate", contextToEvaluate)
                            is List<*> -> if (contextToEvaluate.isNotEmpty()) put("contextToEvaluate",
                                buildJsonArray { contextToEvaluate.forEach { add(it.toString()) } })
                        }
                    }
                    if (dataEntry != null) put("dataEntry", dataEntry)
                }
                if (entry.isNotEmpty()) put("entry", entry)
            }
            val response = callPost("$baseUrl/api/sdk/v2/test-run/execute/simulation/$entityType", body)
            checkError(response)
            return ExecuteSimulationStartResponse.fromJsonObject(response["data"]!!.jsonObject)
        }

        private suspend fun getSimulationStatus(
            entityType: String,
            workspaceId: String,
            testRunEntryId: String
        ): JsonObject {
            val response = callGet(
                "$baseUrl/api/sdk/v2/test-run/execute/simulation/$entityType?workspaceId=$workspaceId&testRunEntryId=$testRunEntryId"
            )
            checkError(response)
            return response["data"]?.jsonObject ?: response
        }

        suspend fun executeSimulationPromptStart(
            testRunId: String,
            promptVersionId: String,
            workspaceId: String,
            simulationConfig: SimulationConfig,
            datasetEntryId: String? = null,
            input: String? = null,
            scenario: String? = null,
            expectedSteps: String? = null,
            contextToEvaluate: Any? = null,
            dataEntry: JsonObject? = null,
            environmentName: String? = null
        ): ExecuteSimulationStartResponse {
            return executeSimulationStart("prompt", promptVersionId, testRunId, workspaceId,
                simulationConfig, datasetEntryId, input, scenario, expectedSteps, contextToEvaluate, dataEntry, environmentName)
        }

        suspend fun getSimulationPromptStatus(workspaceId: String, testRunEntryId: String): JsonObject {
            return getSimulationStatus("prompt", workspaceId, testRunEntryId)
        }

        suspend fun executeSimulationWorkflowStart(
            testRunId: String,
            workflowId: String,
            workspaceId: String,
            simulationConfig: SimulationConfig,
            datasetEntryId: String? = null,
            input: String? = null,
            scenario: String? = null,
            expectedSteps: String? = null,
            contextToEvaluate: Any? = null,
            dataEntry: JsonObject? = null,
            environmentName: String? = null
        ): ExecuteSimulationStartResponse {
            return executeSimulationStart("workflow", workflowId, testRunId, workspaceId,
                simulationConfig, datasetEntryId, input, scenario, expectedSteps, contextToEvaluate, dataEntry, environmentName)
        }

        suspend fun getSimulationWorkflowStatus(workspaceId: String, testRunEntryId: String): JsonObject {
            return getSimulationStatus("workflow", workspaceId, testRunEntryId)
        }

        suspend fun executeSimulationLocalExecution(
            testRunId: String,
            workspaceId: String,
            simulationConfig: SimulationConfig,
            datasetEntryId: String? = null,
            entry: SimulationEntry? = null,
            conversationHistory: List<SimulationConversationTurn>? = null,
            testRunEntryId: String? = null
        ): LocalExecutionResponse {
            val body = buildJsonObject {
                put("testRunId", testRunId)
                put("workspaceId", workspaceId)
                put("simulationConfig", simulationConfig.toJsonObject())
                if (datasetEntryId != null) put("datasetEntryId", datasetEntryId)
                if (entry != null) put("entry", entry.toJsonObject())
                if (conversationHistory != null) put("conversationHistory", buildJsonArray {
                    conversationHistory.forEach { add(it.toJsonObject()) }
                })
                if (testRunEntryId != null) put("testRunEntryId", testRunEntryId)
            }
            val response = callPost("$baseUrl/api/sdk/v2/test-run/simulation/local-execution", body)
            checkError(response)
            return LocalExecutionResponse.fromJsonObject(response["data"]!!.jsonObject)
        }

        suspend fun updateSimulationStatus(testRunEntryId: String, status: String = "FAILED") {
            val body = buildJsonObject {
                put("testRunEntryId", testRunEntryId)
                put("status", status)
            }
            val response = callPost("$baseUrl/api/sdk/v2/test-run/simulation/update-status", body)
            checkError(response)
        }

    }
}