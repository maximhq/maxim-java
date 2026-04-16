package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.evaluators.BaseEvaluator
import ai.getmaxim.sdk.models.*
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Maps an evaluator name to its generated ID and pass/fail criteria.
 */
data class EvaluatorNameToIdAndPassFailCriteria(
    val id: String,
    val passFailCriteria: PassFailCriteria?
)

/**
 * CUID generator
 * Produces hyphen-free alphanumeric IDs (e.g. "c17762400000016712340host9716966")
 * that are safe for use in MySQL JSON path expressions.
 */
private val cuidCounter = AtomicInteger(0)

private fun generateCuid(): String {
    val timestamp = (System.currentTimeMillis()).toString().take(8)
    val counter = (cuidCounter.incrementAndGet() % 1000000).toString().padStart(6, '0')
    val random = (Math.random() * 999999).toInt().toString().padStart(6, '0')
    val hostname = try { InetAddress.getLocalHost().hostName } catch (_: Exception) { "unknown" }
    val hostnameHash = (hostname.sumOf { it.code } % 100000)
    val pid = ProcessHandle.current().pid() % 100000
    val fingerprint = "$hostnameHash$pid".padStart(10, '0')
    return "c$timestamp$counter$random$fingerprint"
}

/**
 * Build a map of evaluator names to their IDs and pass/fail criteria for local evaluators.
 * PlatformEvaluator and String evaluators get their IDs from the API, not generated here.
 */
fun getLocalEvaluatorNameToIdAndPassFailCriteriaMap(
    evaluators: List<Any>
): MutableMap<String, EvaluatorNameToIdAndPassFailCriteria> {
    val allEvalNames = mutableListOf<String>()
    val allPassFailCriteria = mutableMapOf<String, PassFailCriteria>()

    for (evaluator in evaluators) {
        when (evaluator) {
            is BaseEvaluator -> {
                allEvalNames.addAll(evaluator.names)
                allPassFailCriteria.putAll(evaluator.passFailCriteria)
            }
            // String and PlatformEvaluator evaluators get their IDs from the API
        }
    }

    val map = mutableMapOf<String, EvaluatorNameToIdAndPassFailCriteria>()
    for (evalName in allEvalNames) {
        map[evalName] = EvaluatorNameToIdAndPassFailCriteria(
            id = generateCuid(),
            passFailCriteria = allPassFailCriteria[evalName]
        )
    }
    return map
}

/**
 * Create an Evaluator config object from a local evaluator's name and pass/fail criteria.
 * This produces the exact JSON format the backend expects.
 */
fun getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(
    id: String,
    name: String,
    passFailCriteria: PassFailCriteria
): Evaluator {
    val entryLevelValue: Any? = passFailCriteria.onEachEntry.value
    val entryLevelDisplayValue = when (entryLevelValue) {
        is Boolean -> if (entryLevelValue) "Yes" else "No"
        else -> entryLevelValue
    }

    val runLevelName = when (passFailCriteria.forTestrunOverall.forResult) {
        "average" -> "meanScore"
        else -> "queriesPassed"
    }

    val config = buildJsonObject {
        put("passFailCriteria", buildJsonObject {
            put("entryLevel", buildJsonObject {
                when (entryLevelDisplayValue) {
                    is String -> put("value", entryLevelDisplayValue)
                    is Int -> put("value", entryLevelDisplayValue)
                    is Long -> put("value", entryLevelDisplayValue)
                    is Float -> put("value", entryLevelDisplayValue)
                    is Double -> put("value", entryLevelDisplayValue)
                    null -> put("value", JsonNull)
                    else -> put("value", entryLevelDisplayValue.toString())
                }
                put("operator", passFailCriteria.onEachEntry.scoreShouldBe)
                put("name", "score")
            })
            put("runLevel", buildJsonObject {
                put("value", passFailCriteria.forTestrunOverall.value)
                put("operator", passFailCriteria.forTestrunOverall.overallShouldBe)
                put("name", runLevelName)
            })
        })
    }

    return Evaluator(
        id = id,
        name = name,
        type = EvaluatorType.LOCAL,
        builtin = false,
        reversed = false,
        config = config
    )
}
