package ai.getmaxim.sdk

import ai.getmaxim.sdk.models.RuleGroupType
import ai.getmaxim.sdk.models.RuleType

data class IncomingQuery(
    val query: String, // Comma-separated conditions with different operators
    val operator: String, // Operator to use for conditions ('and', 'or')
    val exactMatch: Boolean // Whether to match the exact query or not
)

data class QueryObject(
    val id: String,
    val query: RuleGroupType
)

// Function to parse the incoming query into a format compatible with RuleType
fun parseIncomingQuery(incomingQuery: String): List<RuleType> {
    if (incomingQuery.trim().isEmpty()) {
        return emptyList()
    }
    val operators =
        listOf("!=", ">=", "<=", ">", "<", "includes", "does not include", "=") // Ensure longer operators come first
    return incomingQuery.split(",").map { condition ->
        for (op in operators) {
            if (op in condition) {
                val (field, value) = condition.split(op).map { it.trim() }
                var exactMatch = false
                var fieldName = field
                if (field.startsWith("!!")) {
                    exactMatch = true
                    fieldName = field.substring(2)
                }
                // Here we will auto-parse the number values
                val parsedValue = value.toDoubleOrNull() ?: value
                return@map RuleType(field = fieldName, value = parsedValue, operator = op, exactMatch = exactMatch)
            }
        }
        throw IllegalArgumentException("Unsupported operator found in condition \"$condition\"")
    }
}

// Recursive function to evaluate rule groups against incoming query rules
private fun evaluateRuleGroup(ruleGroup: RuleGroupType, incomingQueryRules: List<RuleType>): Boolean {
    // This will keep track of matched fields to be used for exact match
    val matchedRules = mutableListOf<RuleType>()
    val matchResults = ruleGroup.rules.map { rule ->
        when (rule) {
            is RuleGroupType -> evaluateRuleGroup(rule, incomingQueryRules)
            is RuleType -> incomingQueryRules.any { incomingRule ->
                val conditionMet = { fieldRule: RuleType, fieldIncomingRule: RuleType ->
                    // Checking for the type of the value
                    val incomingValue = when (fieldRule.value) {
                        is Number -> fieldIncomingRule.value.toString().toDouble()
                        is Boolean -> fieldIncomingRule.value.toString().toBoolean()
                        else -> fieldIncomingRule.value.toString()
                    }
                    when (fieldRule.operator) {
                        "=" -> when (fieldRule.value) {
                            is Number -> fieldRule.value.toDouble() == incomingValue
                            else -> fieldRule.value == incomingValue
                        }
                        "!=" -> when (fieldRule.value) {
                            is Number -> fieldRule.value.toDouble() != incomingValue
                            else -> fieldRule.value != incomingValue
                        }
                        ">" -> (fieldRule.value as? Number)?.toDouble()!! > (incomingValue as? Number)?.toDouble()!!
                        "<" -> (fieldRule.value as? Number)?.toDouble()!! < (incomingValue as? Number)?.toDouble()!!
                        ">=" -> (fieldRule.value as? Number)?.toDouble()!! >= (incomingValue as? Number)?.toDouble()!!
                        "<=" -> (fieldRule.value as? Number)?.toDouble()!! <= (incomingValue as? Number)?.toDouble()!!
                        "includes" -> (fieldRule.value as? List<*>)?.contains(incomingValue) == true
                        "does not include" -> (fieldRule.value as? List<*>)?.contains(incomingValue)?.not() == true
                        else -> false
                    }
                }
                val result = rule.field == incomingRule.field && conditionMet(rule, incomingRule)
                if (result) {
                    matchedRules.add(incomingRule)
                }
                result
            }

            else -> false
        }
    }
    // Here we will check if every exact match rule is matched
    val exactMatches = incomingQueryRules.all { rule ->
        !rule.exactMatch!! || rule in matchedRules
    }
    if (!exactMatches) {
        return false
    }
    return if (ruleGroup.combinator == "AND") matchResults.all { it } else matchResults.any { it }
}

// Function to find the best match based on the incoming query
fun findBestMatch(objects: List<QueryObject>, incomingQuery: IncomingQuery): QueryObject? {
    var bestMatch: QueryObject? = null
    var maxMatchCount = 0
    val incomingQueryRules = parseIncomingQuery(incomingQuery.query)
    for (obj in objects) {
        val isMatch = evaluateRuleGroup(obj.query, incomingQueryRules)
        if (isMatch) {
            if (incomingQuery.exactMatch) {
                // Make all fields in the incomingQueryRule are matching with the object.query
                if (incomingQueryRules.size != obj.query.rules.size) {
                    continue
                }
            }
            val matchCount = obj.query.rules.size
            // Assume the first match found is the best match for simplicity
            if (matchCount > maxMatchCount) {
                maxMatchCount = matchCount
                bestMatch = obj
            }
        }
    }
    return bestMatch
}

// Function to find all matches based on the incoming query
fun findAllMatches(objects: List<QueryObject>, incomingQuery: IncomingQuery): List<QueryObject> {
    val matches = mutableListOf<QueryObject>()
    val incomingQueryRules = parseIncomingQuery(incomingQuery.query)
    for (obj in objects) {
        val isMatch = evaluateRuleGroup(obj.query, incomingQueryRules)
        if (isMatch) {
            if (incomingQuery.exactMatch) {
                // Make all fields in the incomingQueryRule are matching with the object.query
                if (incomingQueryRules.size != obj.query.rules.size) {
                    continue
                }
            }
            matches.add(obj)
        }
    }
    return matches
}