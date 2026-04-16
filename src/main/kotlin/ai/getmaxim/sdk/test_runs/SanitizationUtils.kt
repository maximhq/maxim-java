package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.PlatformEvaluator

/**
 * Validate that local data entries match their declared data structure types.
 */
fun sanitizeData(
    data: List<Map<String, Any?>>,
    dataStructure: Map<String, String>
) {
    for (dataEntry in data) {
        for ((key, value) in dataEntry) {
            val columnType = dataStructure[key] ?: throw IllegalArgumentException(
                "Unknown column \"$key\" not found in data structure"
            )
            when (columnType) {
                "INPUT", "EXPECTED_OUTPUT", "EXPECTED_STEPS", "SCENARIO" -> {
                    if (value != null && value !is String) {
                        throw IllegalArgumentException(
                            "$columnType column \"$key\" has a data entry which is not a string"
                        )
                    }
                }
                "CONTEXT_TO_EVALUATE" -> {
                    if (value != null && value !is String) {
                        if (value is List<*>) {
                            if (!value.all { it is String }) {
                                throw IllegalArgumentException(
                                    "Context to evaluate column \"$key\" has a data entry which is not a string or an array of strings"
                                )
                            }
                        } else {
                            throw IllegalArgumentException(
                                "Context to evaluate column \"$key\" has a data entry which is not a string or an array"
                            )
                        }
                    }
                }
                "VARIABLE" -> {
                    if (value != null && value !is String) {
                        if (value is List<*>) {
                            if (!value.all { it is String }) {
                                throw IllegalArgumentException(
                                    "Variable column \"$key\" has a data entry which is not a string or an array of strings"
                                )
                            }
                        } else {
                            throw IllegalArgumentException(
                                "Variable column \"$key\" has a data entry which is not a string or an array"
                            )
                        }
                    }
                }
                "NULLABLE_VARIABLE" -> {
                    if (value != null && value !is String) {
                        if (value is List<*>) {
                            if (!value.all { it is String }) {
                                throw IllegalArgumentException(
                                    "Nullable variable column \"$key\" has a data entry which is not null, a string or an array of strings"
                                )
                            }
                        } else {
                            throw IllegalArgumentException(
                                "Nullable variable column \"$key\" has a data entry which is not null, a string or an array"
                            )
                        }
                    }
                }
                "FILE_URL_VARIABLE" -> {
                    if (value != null && value !is String) {
                        throw IllegalArgumentException(
                            "File URL variable column \"$key\" has a data entry which is not a string"
                        )
                    }
                }
                else -> throw IllegalArgumentException(
                    "Unknown column type \"$columnType\" for column \"$key\""
                )
            }
        }
    }
}

/**
 * Validate that evaluator names are unique across all evaluators.
 */
fun sanitizeEvaluators(evaluators: List<Any>) {
    val namesEncountered = mutableSetOf<String>()

    for (evaluator in evaluators) {
        val names: List<String> = when (evaluator) {
            is BaseEvaluator -> evaluator.names
            is PlatformEvaluator -> listOf(evaluator.name)
            is String -> listOf(evaluator)
            else -> continue
        }

        for (name in names) {
            if (name in namesEncountered) {
                throw IllegalArgumentException(
                    "Multiple evaluators with the same name \"$name\" found"
                )
            }
            namesEncountered.add(name)
        }
    }
}
