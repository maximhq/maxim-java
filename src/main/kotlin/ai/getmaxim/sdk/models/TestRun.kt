package ai.getmaxim.sdk.models

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import kotlinx.serialization.*
import kotlinx.serialization.json.*

// Helper: safely get a non-null JsonElement from a JsonObject, treating JsonNull as absent.
// This prevents crashes like "JsonNull is not a JsonObject" when the API returns explicit nulls.
private fun JsonObject.getNonNull(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

// ─── Enums ──────────────────────────────────────────────────────────────────

enum class RunStatus(val value: String) {
    QUEUED("QUEUED"),
    RUNNING("RUNNING"),
    FAILED("FAILED"),
    COMPLETE("COMPLETE"),
    STOPPED("STOPPED");

    companion object {
        fun fromValue(value: String): RunStatus {
            return entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown RunStatus: $value")
        }
    }
}

enum class RunType(val value: String) {
    SINGLE("SINGLE"),
    COMPARISON("COMPARISON")
}

// ─── Serialization Interface ────────────────────────────────────────────────

/**
 * Common interface for types that can be serialized to a JsonObject.
 * Used by TestRun and TestRunWithDatasetEntry so the API layer can accept either.
 */
interface JsonSerializable {
    fun toJsonObject(): JsonObject
}

// ─── Core Types ─────────────────────────────────────────────────────────────

/**
 * Represents a test run created on the Maxim platform.
 */
data class TestRun(
    val id: String,
    val workspaceId: String,
    val evalConfig: JsonObject,
    val humanEvaluationConfig: HumanEvaluationConfig? = null,
    val parentTestRunId: String? = null,
    var environmentName: String? = null,
    val connectedRepoId: String? = null
) : JsonSerializable {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("workspaceId", workspaceId)
            put("evalConfig", evalConfig)
            if (humanEvaluationConfig != null) put("humanEvaluationConfig", humanEvaluationConfig.toJsonObject())
            if (parentTestRunId != null) put("parentTestRunId", parentTestRunId)
            if (environmentName != null) put("environmentName", environmentName)
            if (connectedRepoId != null) put("connectedRepoId", connectedRepoId)
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): TestRun {
            return TestRun(
                id = data["id"]!!.jsonPrimitive.content,
                workspaceId = data["workspaceId"]!!.jsonPrimitive.content,
                evalConfig = data["evalConfig"]!!.jsonObject,
                humanEvaluationConfig = data.getNonNull("humanEvaluationConfig")?.jsonObject?.let {
                    HumanEvaluationConfig.fromJsonObject(it)
                },
                parentTestRunId = data["parentTestRunId"]?.jsonPrimitive?.contentOrNull,
                environmentName = data["environmentName"]?.jsonPrimitive?.contentOrNull,
                connectedRepoId = data["connectedRepoId"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

/**
 * An entry in a test run, containing input/output data and evaluation results.
 */
data class TestRunEntry(
    val id: String? = null,
    val variables: Map<String, TestRunVariable> = emptyMap(),
    val output: String? = null,
    val input: String? = null,
    val expectedOutput: String? = null,
    val contextToEvaluate: Any? = null, // String or List<String>
    val scenario: String? = null,
    val expectedSteps: String? = null,
    val simulationMeta: SimulationMeta? = null,
    val localEvaluationResults: List<LocalEvaluationResultWithId>? = null,
    val sdkVariables: Map<String, Map<String, String>>? = null,
    val connectedTraceId: String? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            if (id != null) put("id", id)
            if (output != null) put("output", output)
            if (input != null) put("input", input)
            if (expectedOutput != null) put("expectedOutput", expectedOutput)
            if (contextToEvaluate != null) {
                when (contextToEvaluate) {
                    is String -> put("contextToEvaluate", contextToEvaluate)
                    is List<*> -> put("contextToEvaluate", buildJsonArray {
                        contextToEvaluate.forEach { add(JsonPrimitive(it?.toString())) }
                    })
                }
            }
            if (scenario != null) put("scenario", scenario)
            if (expectedSteps != null) put("expectedSteps", expectedSteps)
            if (simulationMeta != null) put("simulationMeta", simulationMeta.toJsonObject())

            if (localEvaluationResults != null) {
                put("localEvaluationResults", buildJsonArray {
                    localEvaluationResults.forEach { add(it.toJsonObject()) }
                })
            }

            put("dataEntry", buildJsonObject {
                variables.forEach { (key, variable) ->
                    put(key, variable.toJsonObject())
                }
            })

            // Build meta object for sdkVariables and connectedTraceId
            val hasSdkVars = sdkVariables != null && sdkVariables.isNotEmpty()
            val hasTraceId = connectedTraceId != null
            if (hasSdkVars || hasTraceId) {
                put("meta", buildJsonObject {
                    if (hasSdkVars) {
                        put("sdkVariables", buildJsonObject {
                            sdkVariables!!.forEach { (evaluatorId, varMapping) ->
                                put(evaluatorId, buildJsonObject {
                                    put("type", "json")
                                    put("payload", MaximJson.encodeToString(varMapping))
                                })
                            }
                        })
                    }
                    if (hasTraceId) {
                        put("connectedTraceId", connectedTraceId)
                    }
                })
            }
        }
    }
}

/**
 * A test run combined with dataset entry information for pushing entries from a dataset.
 */
data class TestRunWithDatasetEntry(
    val testRun: TestRun,
    val datasetEntryId: String,
    val datasetId: String
) : JsonSerializable {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("id", testRun.id)
            put("workspaceId", testRun.workspaceId)
            put("evalConfig", testRun.evalConfig)
            if (testRun.humanEvaluationConfig != null) {
                put("humanEvaluationConfig", testRun.humanEvaluationConfig.toJsonObject())
            }
            if (testRun.parentTestRunId != null) put("parentTestRunId", testRun.parentTestRunId)
            if (testRun.environmentName != null) put("environmentName", testRun.environmentName)
            if (testRun.connectedRepoId != null) put("connectedRepoId", testRun.connectedRepoId)
            put("datasetEntryId", datasetEntryId)
            put("datasetId", datasetId)
        }
    }
}

// ─── Push Run Config ────────────────────────────────────────────────────────

/**
 * Optional run configuration passed when pushing a test run entry.
 * Contains usage and cost metrics from the entity execution.
 */
data class PushRunConfig(
    val cost: YieldedOutputCost? = null,
    val usage: YieldedOutputTokenUsage? = null
) : JsonSerializable {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            if (cost != null) put("cost", cost.toJsonObject())
            if (usage != null) put("usage", usage.toJsonObject())
        }
    }
}

// ─── Simulation Entry ───────────────────────────────────────────────────────

/**
 * Entry payload for the first turn of a local-execution simulation.
 * Contains initial data to kick off the simulation.
 */
data class SimulationEntry(
    val input: String? = null,
    val scenario: String? = null,
    val expectedSteps: String? = null,
    val contextToEvaluate: Any? = null,
    val dataEntry: JsonObject? = null,
    val persona: String? = null
) : JsonSerializable {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            if (input != null) put("input", input)
            if (scenario != null) put("scenario", scenario)
            if (expectedSteps != null) put("expectedSteps", expectedSteps)
            if (contextToEvaluate != null) {
                when (contextToEvaluate) {
                    is String -> put("contextToEvaluate", contextToEvaluate)
                    is List<*> -> put("contextToEvaluate", buildJsonArray { contextToEvaluate.forEach { add(it.toString()) } })
                }
            }
            if (dataEntry != null) put("dataEntry", dataEntry)
            if (persona != null) put("persona", persona)
        }
    }
}

// ─── Image URL ──────────────────────────────────────────────────────────────

/**
 * An image URL for multimodal prompt execution.
 * Matches the backend schema: { url: string, detail?: string }.
 */
data class ImageURL(
    val url: String,
    val detail: String? = null
) : JsonSerializable {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("url", url)
            if (detail != null) put("detail", detail)
        }
    }
}

// ─── Variable for Test Runs ─────────────────────────────────────────────────

/**
 * A variable in a test run entry. Supports text, json, and file types.
 */
data class TestRunVariable(
    val type: String, // "text", "json", "file"
    val payload: Any // String for text/json, Map for file
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("type", type)
            when (payload) {
                is String -> put("payload", payload)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = payload as Map<String, Any?>
                    put("payload", mapToJsonObject(map))
                }
                else -> put("payload", payload.toString())
            }
        }
    }

    companion object {
        private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
            return buildJsonObject {
                map.forEach { (key, value) ->
                    when (value) {
                        null -> put(key, JsonNull)
                        is String -> put(key, value)
                        is Boolean -> put(key, value)
                        is Int -> put(key, value)
                        is Long -> put(key, value)
                        is Float -> put(key, value)
                        is Double -> put(key, value)
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            put(key, mapToJsonObject(value as Map<String, Any?>))
                        }
                        is List<*> -> put(key, listToJsonArray(value))
                        else -> put(key, value.toString())
                    }
                }
            }
        }

        private fun listToJsonArray(list: List<*>): JsonArray {
            return buildJsonArray {
                list.forEach { item ->
                    when (item) {
                        null -> add(JsonNull)
                        is String -> add(item)
                        is Boolean -> add(item)
                        is Int -> add(item)
                        is Long -> add(item)
                        is Float -> add(item)
                        is Double -> add(item)
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            add(mapToJsonObject(item as Map<String, Any?>))
                        }
                        else -> add(item.toString())
                    }
                }
            }
        }
    }
}

// ─── Entity Configs ─────────────────────────────────────────────────────────

data class WorkflowConfig(
    val id: String,
    var contextToEvaluate: String? = null
)

data class TestRunPromptVersionConfig(
    val id: String,
    var contextToEvaluate: String? = null
)

data class TestRunPromptChainVersionConfig(
    val id: String,
    var contextToEvaluate: String? = null
)

data class HumanEvaluationConfig(
    val emails: List<String>,
    val instructions: String? = null,
    val requester: String? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("emails", buildJsonArray { emails.forEach { add(it) } })
            if (instructions != null) put("instructions", instructions)
            if (requester != null) put("requester", requester)
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): HumanEvaluationConfig {
            return HumanEvaluationConfig(
                emails = data["emails"]!!.jsonArray.map { it.jsonPrimitive.content },
                instructions = data["instructions"]?.jsonPrimitive?.contentOrNull,
                requester = data["requester"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

// ─── Status & Result Types ──────────────────────────────────────────────────

data class TestRunStatus(
    val totalEntries: Int,
    val runningEntries: Int,
    val queuedEntries: Int,
    val failedEntries: Int,
    val completedEntries: Int,
    val stoppedEntries: Int,
    val testRunStatus: RunStatus
) {
    fun toDisplayMap(): Map<String, Any> {
        return mapOf(
            "totalEntries" to totalEntries,
            "runningEntries" to runningEntries,
            "queuedEntries" to queuedEntries,
            "failedEntries" to failedEntries,
            "completedEntries" to completedEntries,
            "stoppedEntries" to stoppedEntries,
            "testRunStatus" to testRunStatus.value
        )
    }

    companion object {
        fun fromJsonObject(data: JsonObject): TestRunStatus {
            return TestRunStatus(
                totalEntries = data["total"]!!.jsonPrimitive.int,
                runningEntries = data["running"]!!.jsonPrimitive.int,
                queuedEntries = data["queued"]!!.jsonPrimitive.int,
                failedEntries = data["failed"]!!.jsonPrimitive.int,
                completedEntries = data["completed"]!!.jsonPrimitive.int,
                stoppedEntries = data["stopped"]!!.jsonPrimitive.int,
                testRunStatus = RunStatus.fromValue(data["testRunStatus"]!!.jsonPrimitive.content)
            )
        }
    }
}

data class EvaluatorMeanScore(
    val score: Any, // Float, Boolean, or String
    val outOf: Double? = null,
    val isPass: Boolean? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): EvaluatorMeanScore {
            val scoreElement = data["score"]!!.jsonPrimitive
            val score: Any = when {
                scoreElement.booleanOrNull != null -> scoreElement.boolean
                scoreElement.doubleOrNull != null -> scoreElement.double
                else -> scoreElement.content
            }
            return EvaluatorMeanScore(
                score = score,
                outOf = data["outOf"]?.jsonPrimitive?.doubleOrNull,
                isPass = data["pass"]?.jsonPrimitive?.booleanOrNull
            )
        }
    }
}

data class TestRunTokenUsage(
    val total: Int,
    val input: Int,
    val completion: Int
) {
    companion object {
        fun fromJsonObject(data: JsonObject): TestRunTokenUsage {
            return TestRunTokenUsage(
                total = data["total"]!!.jsonPrimitive.int,
                input = data["input"]!!.jsonPrimitive.int,
                completion = data["completion"]!!.jsonPrimitive.int
            )
        }
    }
}

data class TestRunCost(
    val total: Double,
    val input: Double,
    val completion: Double
) {
    companion object {
        fun fromJsonObject(data: JsonObject): TestRunCost {
            return TestRunCost(
                total = data["total"]!!.jsonPrimitive.double,
                input = data["input"]!!.jsonPrimitive.double,
                completion = data["completion"]!!.jsonPrimitive.double
            )
        }
    }
}

data class TestRunLatency(
    val min: Double,
    val max: Double,
    val p50: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double,
    val mean: Double,
    val standardDeviation: Double,
    val total: Double
) {
    companion object {
        fun fromJsonObject(data: JsonObject): TestRunLatency {
            return TestRunLatency(
                min = data["min"]!!.jsonPrimitive.double,
                max = data["max"]!!.jsonPrimitive.double,
                p50 = data["p50"]!!.jsonPrimitive.double,
                p90 = data["p90"]!!.jsonPrimitive.double,
                p95 = data["p95"]!!.jsonPrimitive.double,
                p99 = data["p99"]!!.jsonPrimitive.double,
                mean = data["mean"]!!.jsonPrimitive.double,
                standardDeviation = data["standardDeviation"]!!.jsonPrimitive.double,
                total = data["total"]!!.jsonPrimitive.double
            )
        }
    }
}

data class TestRunResultObj(
    val name: String,
    val individualEvaluatorMeanScore: Map<String, EvaluatorMeanScore>,
    val usage: TestRunTokenUsage? = null,
    val cost: TestRunCost? = null,
    val latency: TestRunLatency? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): TestRunResultObj {
            return TestRunResultObj(
                name = data["name"]!!.jsonPrimitive.content,
                individualEvaluatorMeanScore = data["individualEvaluatorMeanScore"]!!.jsonObject.mapValues {
                    EvaluatorMeanScore.fromJsonObject(it.value.jsonObject)
                },
                usage = data.getNonNull("usage")?.jsonObject?.let { TestRunTokenUsage.fromJsonObject(it) },
                cost = data.getNonNull("cost")?.jsonObject?.let { TestRunCost.fromJsonObject(it) },
                latency = data.getNonNull("latency")?.jsonObject?.let { TestRunLatency.fromJsonObject(it) }
            )
        }
    }
}

data class TestRunResult(
    var link: String,
    val result: List<TestRunResultObj>
) {
    companion object {
        fun fromJsonObject(data: JsonObject): TestRunResult {
            return TestRunResult(
                link = data["link"]!!.jsonPrimitive.content,
                result = data["result"]!!.jsonArray.map {
                    TestRunResultObj.fromJsonObject(it.jsonObject)
                }
            )
        }
    }
}

data class RunResult(
    val testRunResult: TestRunResult,
    val failedEntryIndices: List<Int>
)

// ─── Output Types ───────────────────────────────────────────────────────────

data class YieldedOutput(
    val data: String,
    val retrievedContextToEvaluate: Any? = null, // String or List<String>
    val simulationOutputs: List<String>? = null,
    val messages: List<Any>? = null,
    val simulationMeta: SimulationMeta? = null,
    val simulationResponse: Map<String, Any?>? = null,
    val toolCalls: List<Any>? = null,
    val meta: YieldedOutputMeta? = null
)

data class YieldedOutputMeta(
    val entityType: String? = null,
    val entityId: String? = null,
    val usage: YieldedOutputTokenUsage? = null,
    val cost: YieldedOutputCost? = null
)

data class YieldedOutputTokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val latency: Double? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("prompt_tokens", promptTokens)
            put("completion_tokens", completionTokens)
            put("total_tokens", totalTokens)
            if (latency != null) put("latency", latency)
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): YieldedOutputTokenUsage {
            val promptTokens = data["promptTokens"]?.jsonPrimitive?.intOrNull
                ?: data["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            val completionTokens = data["completionTokens"]?.jsonPrimitive?.intOrNull
                ?: data["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            val totalTokens = data["totalTokens"]?.jsonPrimitive?.intOrNull
                ?: data["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            return YieldedOutputTokenUsage(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                latency = data["latency"]?.jsonPrimitive?.doubleOrNull
            )
        }
    }
}

data class YieldedOutputCost(
    val inputCost: Double,
    val outputCost: Double,
    val totalCost: Double
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("input", inputCost)
            put("output", outputCost)
            put("total", totalCost)
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): YieldedOutputCost {
            return YieldedOutputCost(
                inputCost = data["input"]!!.jsonPrimitive.double,
                outputCost = data["output"]!!.jsonPrimitive.double,
                totalCost = data["total"]!!.jsonPrimitive.double
            )
        }
    }
}

// ─── Preset Types ───────────────────────────────────────────────────────────

data class Preset(
    val id: String,
    val name: String,
    val description: String? = null,
    val datasets: List<PresetDataset>? = null,
    val evaluators: List<PresetEvaluator>? = null,
    val simulationConfig: SimulationConfig? = null,
    val contextToEvaluate: List<ContextToEvaluateEntry>? = null,
    val attachedDataSources: List<Map<String, String>>? = null,
    val environmentName: String? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): Preset {
            return Preset(
                id = data["id"]!!.jsonPrimitive.content,
                name = data["name"]!!.jsonPrimitive.content,
                description = data["description"]?.jsonPrimitive?.contentOrNull,
                datasets = data.getNonNull("datasets")?.jsonArray?.map { PresetDataset.fromJsonObject(it.jsonObject) },
                evaluators = data.getNonNull("evaluators")?.jsonArray?.map { PresetEvaluator.fromJsonObject(it.jsonObject) },
                simulationConfig = data.getNonNull("simulationConfig")?.jsonObject?.let { SimulationConfig.fromJsonObject(it) },
                contextToEvaluate = data.getNonNull("contextToEvaluate")?.jsonArray?.map {
                    ContextToEvaluateEntry.fromJsonObject(it.jsonObject)
                },
                attachedDataSources = data.getNonNull("attachedDataSources")?.jsonArray?.map { element ->
                    element.jsonObject.mapValues { it.value.jsonPrimitive.content }
                },
                environmentName = data["environmentName"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

data class PresetDataset(
    val id: String,
    val name: String,
    val splitId: String? = null,
    val splitName: String? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): PresetDataset {
            return PresetDataset(
                id = data["id"]!!.jsonPrimitive.content,
                name = data["name"]!!.jsonPrimitive.content,
                splitId = data["splitId"]?.jsonPrimitive?.contentOrNull,
                splitName = data["splitName"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

data class PresetEvaluator(
    val id: String,
    val name: String,
    val meta: JsonObject? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): PresetEvaluator {
            return PresetEvaluator(
                id = data["id"]!!.jsonPrimitive.content,
                name = data["name"]!!.jsonPrimitive.content,
                meta = data.getNonNull("meta")?.jsonObject
            )
        }
    }
}

data class ContextToEvaluateEntry(
    val type: String,
    val payload: String
) {
    companion object {
        fun fromJsonObject(data: JsonObject): ContextToEvaluateEntry {
            return ContextToEvaluateEntry(
                type = data["type"]!!.jsonPrimitive.content,
                payload = data["payload"]!!.jsonPrimitive.content
            )
        }
    }
}

// ─── Execution Response Types ───────────────────────────────────────────────

data class ExecuteWorkflowResponse(
    val output: String? = null,
    val contextToEvaluate: String? = null,
    val latency: Double = 0.0
) {
    companion object {
        fun fromJsonObject(data: JsonObject): ExecuteWorkflowResponse {
            return ExecuteWorkflowResponse(
                output = data["output"]?.jsonPrimitive?.contentOrNull,
                contextToEvaluate = data["contextToEvaluate"]?.jsonPrimitive?.contentOrNull,
                latency = data["latency"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            )
        }
    }
}

data class ExecutePromptResponse(
    val output: String? = null,
    val contextToEvaluate: String? = null,
    val usage: YieldedOutputTokenUsage? = null,
    val cost: YieldedOutputCost? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): ExecutePromptResponse {
            return ExecutePromptResponse(
                output = data["output"]?.jsonPrimitive?.contentOrNull,
                contextToEvaluate = data["contextToEvaluate"]?.jsonPrimitive?.contentOrNull,
                usage = data.getNonNull("usage")?.jsonObject?.let { YieldedOutputTokenUsage.fromJsonObject(it) },
                cost = data.getNonNull("cost")?.jsonObject?.let { YieldedOutputCost.fromJsonObject(it) }
            )
        }
    }
}

// ─── Data Types ─────────────────────────────────────────────────────────────

/**
 * Sealed class representing the data source for a test run.
 */
sealed class TestRunData {
    /** Use a dataset ID or split ID from the platform. */
    data class DatasetId(val id: String) : TestRunData()
    /** Use local data provided as a list of maps. */
    data class LocalDataList(val data: List<Map<String, Any?>>) : TestRunData()
    /** Use a function that provides data row by row (return null to stop). */
    data class DataFunction(val fn: (Int) -> Map<String, Any?>?) : TestRunData()
}

// ─── Internal Config ────────────────────────────────────────────────────────

/**
 * Internal configuration state for the TestRunBuilder. Not user-facing.
 */
data class TestRunConfig(
    val baseUrl: String,
    val apiKey: String,
    val name: String,
    val workspaceId: String,
    var evaluators: MutableList<Any> = mutableListOf(), // String, BaseEvaluator, or PlatformEvaluator
    var workflow: WorkflowConfig? = null,
    var promptVersion: TestRunPromptVersionConfig? = null,
    var promptChainVersion: TestRunPromptChainVersionConfig? = null,
    var tags: List<String>? = null,
    var dataStructure: DataStructure? = null,
    var data: TestRunData? = null,
    var testConfigId: String? = null,
    var logger: TestRunLogger = ConsoleLogger(),
    var humanEvaluationConfig: HumanEvaluationConfig? = null,
    var concurrency: Int? = null,
    var environmentName: String? = null,
    var outputFunction: ((Map<String, Any?>, SimulationContext?) -> YieldedOutput)? = null,
    var simulationConfig: SimulationConfig? = null
)

// ─── Logger ─────────────────────────────────────────────────────────────────

/**
 * Logger interface for test run progress messages.
 */
interface TestRunLogger {
    fun info(message: String)
    fun error(message: String, e: Exception? = null)
}

/**
 * Default logger that prints to stdout.
 */
class ConsoleLogger : TestRunLogger {
    override fun info(message: String) {
        println(message)
    }

    override fun error(message: String, e: Exception?) {
        if (e != null) {
            println("$message $e")
        } else {
            println(message)
        }
    }
}

// ─── Internal Helper ────────────────────────────────────────────────────────

data class ProcessedEntry(
    val entry: TestRunEntry,
    val meta: YieldedOutputMeta? = null
)

// ─── Dataset Row ────────────────────────────────────────────────────────────

/**
 * A row from a dataset, as returned by the dataset API.
 */
data class DatasetRow(
    val id: String,
    val data: Map<String, String>
) {
    companion object {
        fun fromJsonObject(json: JsonObject): DatasetRow {
            return DatasetRow(
                id = json["id"]!!.jsonPrimitive.content,
                data = json["data"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
            )
        }
    }
}

// ─── Simulation Models ────────────────────────────────────────────────────

/**
 * A single turn in a simulation conversation history.
 */
data class SimulationConversationTurn(
    val turn: Int,
    val request: Map<String, Any?>,
    val response: Map<String, Any?>,
    val reasoning: String? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("turn", turn)
            put("request", mapToJsonElement(request))
            put("response", mapToJsonElement(response))
            if (reasoning != null) put("reasoning", reasoning)
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): SimulationConversationTurn {
            return SimulationConversationTurn(
                turn = data["turn"]!!.jsonPrimitive.int,
                request = jsonObjectToMap(data["request"]!!.jsonObject),
                response = jsonObjectToMap(data["response"]!!.jsonObject),
                reasoning = data["reasoning"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

/**
 * Metadata returned from simulation endpoints.
 */
data class SimulationMeta(
    val sessionId: String? = null,
    val simulationId: String? = null,
    val messages: List<Any>? = null,
    val trace: List<Any>? = null,
    val turns: List<Any>? = null,
    val testRunEntryId: String? = null,
    val lastTurn: Map<String, Any?>? = null,
    val stopReason: String? = null,
    val usage: YieldedOutputTokenUsage? = null,
    val cost: YieldedOutputCost? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            if (sessionId != null) put("sessionId", sessionId)
            if (simulationId != null) put("simulationId", simulationId)
            if (messages != null) put("messages", buildJsonArray {
                messages.forEach { msg ->
                    when (msg) {
                        is SimulationConversationTurn -> add(msg.toJsonObject())
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            add(mapToJsonElement(msg as Map<String, Any?>))
                        }
                        else -> add(JsonPrimitive(msg.toString()))
                    }
                }
            })
            if (trace != null) put("trace", anyListToJsonArray(trace))
            if (turns != null) put("turns", anyListToJsonArray(turns))
            if (testRunEntryId != null) put("testRunEntryId", testRunEntryId)
            if (lastTurn != null) put("lastTurn", mapToJsonElement(lastTurn))
            if (stopReason != null) put("stopReason", stopReason)
            if (usage != null) put("usage", buildJsonObject {
                put("prompt_tokens", usage.promptTokens)
                put("completion_tokens", usage.completionTokens)
                put("total_tokens", usage.totalTokens)
                if (usage.latency != null) put("latency", usage.latency)
            })
            if (cost != null) put("cost", cost.toJsonObject())
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): SimulationMeta {
            return SimulationMeta(
                sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull,
                simulationId = data["simulationId"]?.jsonPrimitive?.contentOrNull,
                messages = data.getNonNull("messages")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                trace = data.getNonNull("trace")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                turns = data.getNonNull("turns")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                testRunEntryId = data["testRunEntryId"]?.jsonPrimitive?.contentOrNull,
                lastTurn = data.getNonNull("lastTurn")?.jsonObject?.let { jsonObjectToMap(it) },
                stopReason = data["stopReason"]?.jsonPrimitive?.contentOrNull,
                usage = data.getNonNull("usage")?.jsonObject?.let { YieldedOutputTokenUsage.fromJsonObject(it) },
                cost = data.getNonNull("cost")?.jsonObject?.let { YieldedOutputCost.fromJsonObject(it) }
            )
        }
    }
}

/**
 * Context passed to the user's output function during simulation turns.
 */
data class SimulationContext(
    val conversationHistory: List<SimulationConversationTurn>,
    val currentUserInput: Map<String, Any?>,
    val turnNumber: Int,
    val totalCost: Double,
    val totalTokens: Int
)

/**
 * Configuration for a custom simulator.
 */
data class CustomSimulatorConfig(
    val simulatorPrompt: String,
    val model: String,
    val provider: String,
    val variables: Map<String, String>? = null,
    val variableBindings: Map<String, Any?>? = null,
    val modelParameters: Map<String, Any?>? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): CustomSimulatorConfig {
            return CustomSimulatorConfig(
                simulatorPrompt = (data.getNonNull("simulatorPrompt") ?: data.getNonNull("simulator_prompt"))?.jsonPrimitive?.content ?: "",
                model = data.getNonNull("model")?.jsonPrimitive?.content ?: "",
                provider = data.getNonNull("provider")?.jsonPrimitive?.content ?: "",
                variables = data.getNonNull("variables")?.jsonObject?.mapValues { it.value.jsonPrimitive.content },
                variableBindings = data.getNonNull("variableBindings")?.jsonObject?.let { jsonObjectToMap(it) }
                    ?: data.getNonNull("variable_bindings")?.jsonObject?.let { jsonObjectToMap(it) },
                modelParameters = data.getNonNull("modelParameters")?.jsonObject?.let { jsonObjectToMap(it) }
                    ?: data.getNonNull("model_parameters")?.jsonObject?.let { jsonObjectToMap(it) }
            )
        }
    }
}

/**
 * Configuration for simulation in test runs.
 */
data class SimulationConfig(
    val persona: Any? = null, // String or Map<String,String> with type/payload
    val maxTurns: Int? = null,
    val tools: List<String>? = null,
    val context: Map<String, Any?>? = null,
    val responseFields: List<String>? = null,
    val environmentId: String? = null,
    val stopTrigger: Map<String, Any?>? = null,
    val additionalInstructions: String? = null,
    val customSimulator: CustomSimulatorConfig? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            if (persona != null) {
                when (persona) {
                    is String -> put("persona", persona)
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        put("persona", mapToJsonElement(persona as Map<String, Any?>))
                    }
                }
            }
            if (maxTurns != null) put("maxTurns", maxTurns)
            if (tools != null) put("tools", buildJsonArray { tools.forEach { add(it) } })
            if (context != null) put("context", mapToJsonElement(context))
            if (responseFields != null) put("responseFields", buildJsonArray { responseFields.forEach { add(it) } })
            if (environmentId != null) put("environmentId", environmentId)
            if (stopTrigger != null) put("stopTrigger", mapToJsonElement(stopTrigger))
            if (additionalInstructions != null) put("additionalInstructions", additionalInstructions)
            if (customSimulator != null) {
                put("type", "CUSTOM")
                put("simulatorPrompt", customSimulator.simulatorPrompt)
                put("model", customSimulator.model)
                put("provider", customSimulator.provider)
                if (customSimulator.variables != null) put("variables", buildJsonObject {
                    customSimulator.variables.forEach { (k, v) -> put(k, v) }
                })
                if (customSimulator.variableBindings != null) put("variableBindings", mapToJsonElement(customSimulator.variableBindings))
                if (customSimulator.modelParameters != null) put("modelParameters", mapToJsonElement(customSimulator.modelParameters))
            }
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): SimulationConfig {
            val customSimData = data.getNonNull("customSimulator")?.jsonObject
                ?: if (data.getNonNull("type")?.jsonPrimitive?.contentOrNull == "CUSTOM") data else null
            val customSimulator = customSimData?.let { CustomSimulatorConfig.fromJsonObject(it) }
            val persona: Any? = data.getNonNull("persona")?.let { element ->
                when {
                    element is JsonPrimitive -> element.contentOrNull
                    element is JsonObject -> jsonObjectToMap(element)
                    else -> null
                }
            }
            return SimulationConfig(
                persona = persona,
                maxTurns = data.getNonNull("maxTurns")?.jsonPrimitive?.intOrNull,
                tools = data.getNonNull("tools")?.jsonArray?.map { it.jsonPrimitive.content },
                context = data.getNonNull("context")?.jsonObject?.let { jsonObjectToMap(it) },
                responseFields = data.getNonNull("responseFields")?.jsonArray?.map { it.jsonPrimitive.content },
                environmentId = data.getNonNull("environmentId")?.jsonPrimitive?.contentOrNull,
                stopTrigger = data.getNonNull("stopTrigger")?.jsonObject?.let { jsonObjectToMap(it) },
                additionalInstructions = data.getNonNull("additionalInstructions")?.jsonPrimitive?.contentOrNull,
                customSimulator = customSimulator
            )
        }
    }
}

/**
 * Response from starting a simulation (POST execute/simulation/{type}).
 */
data class ExecuteSimulationStartResponse(
    val workspaceId: String,
    val testRunEntryId: String
) {
    companion object {
        fun fromJsonObject(data: JsonObject): ExecuteSimulationStartResponse {
            return ExecuteSimulationStartResponse(
                workspaceId = data["workspaceId"]!!.jsonPrimitive.content,
                testRunEntryId = data["testRunEntryId"]!!.jsonPrimitive.content
            )
        }
    }
}

/**
 * Response from a simulation prompt execution.
 */
data class ExecuteSimulationPromptForDataResponse(
    val output: String? = null,
    val outputs: List<String>? = null,
    val messages: List<Any>? = null,
    val trace: List<Any>? = null,
    val contextToEvaluate: String? = null,
    val sessionId: String? = null,
    val simulationId: String? = null,
    val testRunEntryId: String? = null,
    val latency: Double? = null,
    val usage: YieldedOutputTokenUsage? = null,
    val cost: YieldedOutputCost? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): ExecuteSimulationPromptForDataResponse {
            val outputs = data["outputs"]?.jsonArray?.map { it.jsonPrimitive.content }
            var output = data["output"]?.jsonPrimitive?.contentOrNull
            if (outputs != null && output == null && outputs.isNotEmpty()) {
                output = outputs.last()
            }
            return ExecuteSimulationPromptForDataResponse(
                output = output,
                outputs = outputs,
                messages = data.getNonNull("messages")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                trace = data.getNonNull("trace")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                contextToEvaluate = data["contextToEvaluate"]?.jsonPrimitive?.contentOrNull,
                sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull,
                simulationId = data["simulationId"]?.jsonPrimitive?.contentOrNull,
                testRunEntryId = data["testRunEntryId"]?.jsonPrimitive?.contentOrNull,
                latency = data["latency"]?.jsonPrimitive?.doubleOrNull,
                usage = data.getNonNull("usage")?.jsonObject?.let { YieldedOutputTokenUsage.fromJsonObject(it) },
                cost = data.getNonNull("cost")?.jsonObject?.let { YieldedOutputCost.fromJsonObject(it) }
            )
        }
    }
}

/**
 * Response from a simulation workflow execution (same as prompt + turns).
 */
data class ExecuteSimulationWorkflowForDataResponse(
    val output: String? = null,
    val outputs: List<String>? = null,
    val messages: List<Any>? = null,
    val trace: List<Any>? = null,
    val turns: List<Any>? = null,
    val contextToEvaluate: String? = null,
    val sessionId: String? = null,
    val simulationId: String? = null,
    val testRunEntryId: String? = null,
    val latency: Double? = null,
    val usage: YieldedOutputTokenUsage? = null,
    val cost: YieldedOutputCost? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): ExecuteSimulationWorkflowForDataResponse {
            val outputs = data["outputs"]?.jsonArray?.map { it.jsonPrimitive.content }
            var output = data["output"]?.jsonPrimitive?.contentOrNull
            if (outputs != null && output == null && outputs.isNotEmpty()) {
                output = outputs.last()
            }
            return ExecuteSimulationWorkflowForDataResponse(
                output = output,
                outputs = outputs,
                messages = data.getNonNull("messages")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                trace = data.getNonNull("trace")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                turns = data.getNonNull("turns")?.jsonArray?.mapNotNull { jsonElementToAny(it) },
                contextToEvaluate = data["contextToEvaluate"]?.jsonPrimitive?.contentOrNull,
                sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull,
                simulationId = data["simulationId"]?.jsonPrimitive?.contentOrNull,
                testRunEntryId = data["testRunEntryId"]?.jsonPrimitive?.contentOrNull,
                latency = data["latency"]?.jsonPrimitive?.doubleOrNull,
                usage = data.getNonNull("usage")?.jsonObject?.let { YieldedOutputTokenUsage.fromJsonObject(it) },
                cost = data.getNonNull("cost")?.jsonObject?.let { YieldedOutputCost.fromJsonObject(it) }
            )
        }
    }
}

/**
 * Response from the local-execution simulation endpoint.
 */
data class LocalExecutionResponse(
    val testRunEntryId: String? = null,
    val userInput: Map<String, Any?>? = null,
    val turnNumber: Int = 0,
    val isComplete: Boolean = false,
    val stopReason: String? = null,
    val usage: YieldedOutputTokenUsage? = null,
    val cost: YieldedOutputCost? = null,
    val sessionId: String? = null,
    val simulationId: String? = null
) {
    companion object {
        fun fromJsonObject(data: JsonObject): LocalExecutionResponse {
            val userInputRaw = data["userInput"]
            val userInput: Map<String, Any?>? = when {
                userInputRaw == null || userInputRaw is JsonNull -> null
                userInputRaw is JsonPrimitive -> mapOf("input" to userInputRaw.contentOrNull)
                userInputRaw is JsonObject -> jsonObjectToMap(userInputRaw)
                else -> null
            }
            return LocalExecutionResponse(
                testRunEntryId = data["testRunEntryId"]?.jsonPrimitive?.contentOrNull,
                userInput = userInput,
                turnNumber = data["turnNumber"]?.jsonPrimitive?.intOrNull ?: 0,
                isComplete = data["isComplete"]?.jsonPrimitive?.booleanOrNull ?: false,
                stopReason = data["stopReason"]?.jsonPrimitive?.contentOrNull,
                usage = data.getNonNull("usage")?.jsonObject?.let { YieldedOutputTokenUsage.fromJsonObject(it) },
                cost = data.getNonNull("cost")?.jsonObject?.let { YieldedOutputCost.fromJsonObject(it) },
                sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull,
                simulationId = data["simulationId"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

// ─── JSON Helpers ──────────────────────────────────────────────────────────

internal fun mapToJsonElement(map: Map<String, Any?>): JsonObject {
    return buildJsonObject {
        map.forEach { (key, value) ->
            when (value) {
                null -> put(key, JsonNull)
                is String -> put(key, value)
                is Boolean -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                is Float -> put(key, value)
                is Double -> put(key, value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    put(key, mapToJsonElement(value as Map<String, Any?>))
                }
                is List<*> -> put(key, anyListToJsonArray(value))
                else -> put(key, value.toString())
            }
        }
    }
}

internal fun anyListToJsonArray(list: List<*>): JsonArray {
    return buildJsonArray {
        list.forEach { item ->
            when (item) {
                null -> add(JsonNull)
                is String -> add(item)
                is Boolean -> add(item)
                is Int -> add(item)
                is Long -> add(item)
                is Float -> add(item)
                is Double -> add(item)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    add(mapToJsonElement(item as Map<String, Any?>))
                }
                is List<*> -> add(anyListToJsonArray(item))
                is JsonElement -> add(item)
                else -> add(item.toString())
            }
        }
    }
}

internal fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
    return obj.mapValues { (_, value) -> jsonElementToAny(value) }
}

internal fun jsonElementToAny(element: JsonElement): Any? {
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonObject -> jsonObjectToMap(element)
        is JsonArray -> element.map { jsonElementToAny(it) }
    }
}
