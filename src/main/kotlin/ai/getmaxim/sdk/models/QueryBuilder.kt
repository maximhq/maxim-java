package ai.getmaxim.sdk.models

import kotlinx.serialization.Serializable

@Serializable
enum class QueryRuleType {
    DeploymentVar, Tag
}

@Serializable
data class QueryRule(
    val query: String,
    val operator: Operator,
    val exactMatch: Boolean,
    val scopes: Scopes
)


typealias Scopes = Map<String, String>

@Serializable
enum class Operator {
    AND, OR
}

@Serializable
class QueryBuilder {
    private var query: String = ""
    private var scopes: MutableMap<String, String> = mutableMapOf()
    private var operator: Operator = Operator.AND
    private var isExactMatch: Boolean = false

    fun and(): QueryBuilder {
        operator = Operator.AND
        return this
    }

    fun or(): QueryBuilder {
        operator = Operator.OR
        return this
    }

    fun folder(folderId: String): QueryBuilder {
        scopes["folder"] = folderId
        return this
    }

    fun exactMatch(): QueryBuilder {
        isExactMatch = true
        return this
    }

    fun deploymentVar(key: String, value: Any, enforce: Boolean = true): QueryBuilder {
        if (query.isNotEmpty()) query += ","
        query += "${if (enforce) "!!" else ""}$key=$value"
        return this
    }

    fun tag(key: String, value: Any, enforce: Boolean = false): QueryBuilder {
        if (query.isNotEmpty()) query += ","
        query += "${if (enforce) "!!" else ""}$key=$value"
        return this
    }

    fun build(): QueryRule {
        if (query.trim().isEmpty()) {
            throw IllegalStateException("Cannot build an empty query. Please add at least one rule (deploymentVar or tag).")
        }
        return QueryRule(
            query = query,
            operator = operator,
            exactMatch = isExactMatch,
            scopes = scopes.toMap()
        )
    }
}