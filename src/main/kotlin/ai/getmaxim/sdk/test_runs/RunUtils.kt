package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * Extracted fields from a data row based on the data structure mapping.
 */
data class DataRowFields(
    val input: String? = null,
    val expectedOutput: String? = null,
    val contextToEvaluate: Any? = null, // String or List<String>
    val scenario: String? = null,
    val expectedSteps: String? = null
)

/**
 * Get variables from a data row based on the data structure definition.
 * Maps column types to TestRunVariable instances.
 */
fun getVariablesFromRow(
    row: LocalData,
    dataStructure: DataStructure
): Map<String, TestRunVariable> {
    val variables = mutableMapOf<String, TestRunVariable>()

    for ((columnName, columnType) in dataStructure) {
        when (columnType) {
            "FILE_URL_VARIABLE" -> {
                val urlVal = row[columnName] ?: continue
                val urlStr = urlVal.toString().trim()
                if (urlStr.isEmpty()) continue
                variables[columnName] = TestRunVariable(
                    type = "file",
                    payload = mapOf("files" to listOf(mapOf("url" to urlStr, "type" to "url")))
                )
            }
            "VARIABLE" -> {
                val value = row[columnName]
                variables[columnName] = TestRunVariable(
                    type = "text",
                    payload = value?.toString() ?: ""
                )
            }
            "NULLABLE_VARIABLE" -> {
                val value = row[columnName] ?: continue
                variables[columnName] = TestRunVariable(
                    type = "text",
                    payload = value.toString()
                )
            }
            // INPUT, EXPECTED_OUTPUT, CONTEXT_TO_EVALUATE, SCENARIO, EXPECTED_STEPS
            // are handled separately via getInputExpectedOutputAndContextFromRow
        }
    }
    return variables
}

/**
 * Extract input, expected output, context, scenario, and expected steps from a data row.
 */
fun getInputExpectedOutputAndContextFromRow(
    inputKey: String?,
    expectedOutputKey: String?,
    contextToEvaluateKey: String?,
    scenarioKey: String?,
    expectedStepsKey: String?,
    row: LocalData
): DataRowFields {
    val input = if (inputKey != null && row.containsKey(inputKey)) {
        row[inputKey]?.toString()
    } else null

    val expectedOutput = if (expectedOutputKey != null && row.containsKey(expectedOutputKey)) {
        row[expectedOutputKey]?.toString()
    } else null

    val contextToEvaluate = if (contextToEvaluateKey != null && row.containsKey(contextToEvaluateKey)) {
        row[contextToEvaluateKey]?.toString()
    } else null

    val scenario = if (scenarioKey != null && row.containsKey(scenarioKey)) {
        row[scenarioKey]?.toString()
    } else null

    val expectedSteps = if (expectedStepsKey != null && row.containsKey(expectedStepsKey)) {
        row[expectedStepsKey]?.toString()
    } else null

    return DataRowFields(
        input = input,
        expectedOutput = expectedOutput,
        contextToEvaluate = contextToEvaluate,
        scenario = scenario,
        expectedSteps = expectedSteps
    )
}

/**
 * Run local evaluations in parallel using coroutines.
 */
suspend fun runLocalEvaluations(
    evaluators: List<BaseEvaluator>,
    dataEntry: LocalData,
    processedData: LocalEvaluatorResultParameter
): List<LocalEvaluationResult> = coroutineScope {
    val deferreds = evaluators.map { evaluator ->
        async(Dispatchers.Default) {
            try {
                Result.success(evaluator.guardedEvaluate(processedData, dataEntry))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    val evaluatorResults = deferreds.awaitAll()
    val results = mutableListOf<LocalEvaluationResult>()

    for (i in evaluators.indices) {
        val evaluator = evaluators[i]
        val evalResult = evaluatorResults[i]

        if (evalResult.isSuccess) {
            val combinedResults = evalResult.getOrThrow()
            for ((name, result) in combinedResults) {
                results.add(
                    LocalEvaluationResult(
                        name = name,
                        passFailCriteria = evaluator.passFailCriteria[name]!!,
                        result = result,
                        output = processedData.output
                    )
                )
            }
        } else {
            val err = evalResult.exceptionOrNull()!!
            for (name in evaluator.names) {
                results.add(
                    LocalEvaluationResult(
                        name = name,
                        passFailCriteria = evaluator.passFailCriteria[name]!!,
                        result = LocalEvaluatorReturn(
                            score = "Err",
                            reasoning = "Error while running combined evaluator with names ${evaluator.names}: ${err.message}"
                        ),
                        output = processedData.output
                    )
                )
            }
        }
    }
    results
}

/**
 * Get all keys from a map that have a specific value.
 */
fun getAllKeysByValue(map: Map<String, String>?, value: String): List<String> {
    if (map == null) return emptyList()
    return map.entries.filter { it.value == value }.map { it.key }
}

/**
 * Convert a data entry map to the {type, payload} variable format expected by simulation APIs.
 */
fun convertDataEntryToVariableFormat(dataEntry: Map<String, Any?>): JsonObject {
    return buildJsonObject {
        dataEntry.forEach { (key, value) ->
            when {
                value is Map<*, *> && value.containsKey("type") && value.containsKey("payload") -> {
                    // Already in variable format, pass through
                    put(key, mapToJsonElement(
                        @Suppress("UNCHECKED_CAST")
                        value as Map<String, Any?>
                    ))
                }
                value == null -> put(key, buildJsonObject {
                    put("type", "text")
                    put("payload", "")
                })
                value is String -> put(key, buildJsonObject {
                    put("type", "text")
                    put("payload", value)
                })
                value is List<*> && value.all { it is Map<*, *> && (it as Map<*, *>).containsKey("url") } -> {
                    // List of file objects with url
                    put(key, buildJsonObject {
                        put("type", "file")
                        put("payload", buildJsonObject {
                            put("files", buildJsonArray {
                                value.forEach { item ->
                                    if (item is Map<*, *>) {
                                        add(buildJsonObject {
                                            val url = item["url"]?.toString() ?: ""
                                            put("url", url)
                                            put("type", item["type"]?.toString() ?: "url")
                                            if (item.containsKey("name")) put("name", item["name"].toString())
                                            if (item.containsKey("id")) put("id", item["id"].toString())
                                        })
                                    }
                                }
                            })
                        })
                    })
                }
                value is List<*> -> put(key, buildJsonObject {
                    put("type", "text")
                    put("payload", buildJsonArray { value.forEach { add(it?.toString() ?: "") } })
                })
                else -> put(key, buildJsonObject {
                    put("type", "text")
                    put("payload", value.toString())
                })
            }
        }
    }
}
