package ai.getmaxim.sdk.evaluators

import ai.getmaxim.sdk.models.*
import java.util.logging.Logger

/**
 * Base class for custom local evaluators.
 *
 * Extend this class to implement your own evaluation logic. Each evaluator can
 * produce scores for one or more named evaluation criteria.
 *
 * Example:
 * ```kotlin
 * class MyEvaluator : BaseEvaluator(
 *     passFailCriteria = mapOf(
 *         "relevance" to PassFailCriteria(
 *             onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
 *             forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
 *         )
 *     )
 * ) {
 *     override fun evaluate(result: LocalEvaluatorResultParameter, data: LocalData): Map<String, LocalEvaluatorReturn> {
 *         // Your evaluation logic
 *         return mapOf("relevance" to LocalEvaluatorReturn(score = 0.8, reasoning = "Relevant output"))
 *     }
 * }
 * ```
 */
abstract class BaseEvaluator(
    passFailCriteria: Map<String, PassFailCriteria>,
    val variableMapping: VariableMapping? = null
) {
    private val logger = Logger.getLogger(BaseEvaluator::class.java.name)

    val names: List<String> = passFailCriteria.keys.toList()
    val passFailCriteria: Map<String, PassFailCriteria> = passFailCriteria

    init {
        require(passFailCriteria.isNotEmpty()) { "passFailCriteria must not be empty" }
    }

    /**
     * Evaluate the result against the data entry.
     *
     * @param result The processed output data to evaluate
     * @param data The original data row
     * @return Map of evaluator name to score/reasoning
     */
    abstract fun evaluate(
        result: LocalEvaluatorResultParameter,
        data: LocalData
    ): Map<String, LocalEvaluatorReturn>

    /**
     * Guarded evaluate that validates returned keys match registered names.
     */
    fun guardedEvaluate(
        result: LocalEvaluatorResultParameter,
        data: LocalData
    ): Map<String, LocalEvaluatorReturn> {
        val response = evaluate(result, data)
        val invalidNames = response.keys.filter { it !in names }
        if (invalidNames.isNotEmpty()) {
            throw IllegalArgumentException(
                "Evaluator returned results for unknown names: $invalidNames. " +
                "Each key returned by evaluate() must have a corresponding PassFailCriteria. " +
                "Registered names: $names"
            )
        }
        return response
    }
}
