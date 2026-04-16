package ai.getmaxim.sdk.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Type of evaluator on the Maxim platform.
 */
@Serializable
enum class EvaluatorType(val value: String) {
    @SerialName("AI") AI("AI"),
    @SerialName("Programmatic") PROGRAMMATIC("Programmatic"),
    @SerialName("Statistical") STATISTICAL("Statistical"),
    @SerialName("API") API("API"),
    @SerialName("Human") HUMAN("Human"),
    @SerialName("Local") LOCAL("Local");

    companion object {
        fun fromValue(value: String): EvaluatorType {
            return entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown EvaluatorType: $value")
        }
    }
}

/**
 * Evaluator fetched from the Maxim platform.
 */
@Serializable
data class Evaluator(
    val id: String,
    val name: String,
    val type: EvaluatorType,
    val builtin: Boolean,
    val reversed: Boolean? = false,
    val config: JsonObject? = null,
    val meta: JsonObject? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("name", name)
            put("type", type.value)
            put("builtin", builtin)
            if (reversed != null) put("reversed", reversed)
            if (config != null) put("config", config)
            if (meta != null) put("meta", meta)
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): Evaluator {
            return Evaluator(
                id = data["id"]!!.jsonPrimitive.content,
                name = data["name"]!!.jsonPrimitive.content,
                type = EvaluatorType.fromValue(data["type"]!!.jsonPrimitive.content),
                builtin = data["builtin"]!!.jsonPrimitive.boolean,
                reversed = data["reversed"]?.jsonPrimitive?.booleanOrNull,
                config = data["config"]?.takeIf { it !is JsonNull }?.jsonObject,
                meta = data["meta"]?.takeIf { it !is JsonNull }?.jsonObject
            )
        }
    }
}

/**
 * Return value from a local evaluator's evaluate function.
 */
data class LocalEvaluatorReturn(
    val score: Any, // Int, Boolean, or String
    val reasoning: String? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            when (score) {
                is Boolean -> put("score", score)
                is Int -> put("score", score)
                is Long -> put("score", score)
                is Float -> put("score", score)
                is Double -> put("score", score)
                is String -> put("score", score)
                else -> put("score", score.toString())
            }
            if (reasoning != null) put("reasoning", reasoning)
        }
    }
}

/**
 * Operator type for pass/fail criteria (e.g., ">=", "<", "<=", ">", "=", "!=").
 */
typealias OperatorType = String

/**
 * Pass/fail criteria applied to each individual entry.
 */
data class PassFailCriteriaOnEachEntry(
    val scoreShouldBe: OperatorType,
    val value: Any? // Boolean, Int, Float, or null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("scoreShouldBe", scoreShouldBe)
            if (value != null) {
                when (value) {
                    is Boolean -> put("value", value)
                    is Int -> put("value", value)
                    is Long -> put("value", value)
                    is Float -> put("value", value)
                    is Double -> put("value", value)
                    else -> put("value", value.toString())
                }
            } else {
                put("value", JsonNull)
            }
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): PassFailCriteriaOnEachEntry {
            val valueElement = data["value"]
            val value: Any? = when {
                valueElement == null || valueElement is JsonNull -> null
                valueElement.jsonPrimitive.booleanOrNull != null -> valueElement.jsonPrimitive.boolean
                valueElement.jsonPrimitive.intOrNull != null -> valueElement.jsonPrimitive.int
                valueElement.jsonPrimitive.floatOrNull != null -> valueElement.jsonPrimitive.float
                else -> valueElement.jsonPrimitive.contentOrNull
            }
            return PassFailCriteriaOnEachEntry(
                scoreShouldBe = data["scoreShouldBe"]!!.jsonPrimitive.content,
                value = value
            )
        }
    }
}

/**
 * Pass/fail criteria for the overall test run.
 */
data class PassFailCriteriaForTestrunOverall(
    val overallShouldBe: OperatorType,
    val value: Int,
    val forResult: String // "average" or "percentageOfPassedResults"
) {
    init {
        require(forResult == "average" || forResult == "percentageOfPassedResults") {
            "forResult must be 'average' or 'percentageOfPassedResults'"
        }
    }

    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("overallShouldBe", overallShouldBe)
            put("value", value)
            put("for", forResult)
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): PassFailCriteriaForTestrunOverall {
            return PassFailCriteriaForTestrunOverall(
                overallShouldBe = data["overallShouldBe"]!!.jsonPrimitive.content,
                value = data["value"]!!.jsonPrimitive.int,
                forResult = data["for"]!!.jsonPrimitive.content
            )
        }
    }
}

/**
 * Combined pass/fail criteria for both entry-level and run-level evaluation.
 */
data class PassFailCriteria(
    val onEachEntry: PassFailCriteriaOnEachEntry,
    val forTestrunOverall: PassFailCriteriaForTestrunOverall
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("onEachEntry", onEachEntry.toJsonObject())
            put("forTestrunOverall", forTestrunOverall.toJsonObject())
        }
    }

    companion object {
        fun fromJsonObject(data: JsonObject): PassFailCriteria {
            return PassFailCriteria(
                onEachEntry = PassFailCriteriaOnEachEntry.fromJsonObject(data["onEachEntry"]!!.jsonObject),
                forTestrunOverall = PassFailCriteriaForTestrunOverall.fromJsonObject(data["forTestrunOverall"]!!.jsonObject)
            )
        }
    }
}

/**
 * Parameters passed to a local evaluator's evaluate function.
 */
data class LocalEvaluatorResultParameter(
    val output: String,
    val input: String? = null,
    val expectedOutput: String? = null,
    val contextToEvaluate: Any? = null, // String or List<String>
    val simulationOutputs: List<String>? = null
)

/**
 * Result from a local evaluation.
 */
data class LocalEvaluationResult(
    val result: LocalEvaluatorReturn,
    val name: String,
    val passFailCriteria: PassFailCriteria,
    val output: String? = null
)

/**
 * Result from a local evaluation with an assigned ID.
 */
data class LocalEvaluationResultWithId(
    val id: String,
    val result: LocalEvaluatorReturn,
    val name: String,
    val passFailCriteria: PassFailCriteria,
    val output: String? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("result", result.toJsonObject())
            put("name", name)
            put("passFailCriteria", passFailCriteria.toJsonObject())
            if (output != null) put("output", output)
        }
    }
}

/**
 * Input data for variable mapping functions.
 */
data class VariableMappingInput(
    val data: String,
    val retrievedContextToEvaluate: Any? = null, // String or List<String>
    val messages: List<Any>? = null,
    val meta: Map<String, Any>? = null,
    val extra: Map<String, Any>? = null
) {
    companion object {
        fun fromYieldedOutput(
            output: Any,
            inputValue: String? = null,
            contextToEvaluate: Any? = null
        ): VariableMappingInput {
            // output is expected to be a YieldedOutput-like object
            // We access its properties reflectively or by convention
            val data = when {
                output is Map<*, *> -> output["data"]?.toString() ?: ""
                else -> {
                    try {
                        val dataField = output::class.java.getDeclaredField("data")
                        dataField.isAccessible = true
                        dataField.get(output)?.toString() ?: ""
                    } catch (_: Exception) {
                        output.toString()
                    }
                }
            }
            return VariableMappingInput(
                data = data,
                retrievedContextToEvaluate = contextToEvaluate
            )
        }
    }
}

/**
 * Version info for variable mapping context.
 */
data class VersionInfo(
    val id: String? = null,
    val type: String // "workflow", "prompt", or "promptChain"
)

/**
 * Function type for variable mapping.
 * Takes (input, row data, version info) and returns a mapped string value or null.
 */
typealias VariableMappingFunction = (VariableMappingInput, Map<String, Any?>, VersionInfo?) -> String?

/**
 * Map of variable names to their mapping functions.
 */
typealias VariableMapping = Map<String, VariableMappingFunction>

/**
 * A row of local data (column name to value).
 */
typealias LocalData = Map<String, Any?>

/**
 * Data structure definition (column name to column type like "INPUT", "EXPECTED_OUTPUT", "VARIABLE", etc.).
 */
typealias DataStructure = Map<String, String>

/**
 * A platform evaluator with optional variable mapping.
 * Use this when you want to customize how data is passed to a platform evaluator's variables.
 */
data class PlatformEvaluator(
    val name: String,
    val variableMapping: VariableMapping? = null
)
