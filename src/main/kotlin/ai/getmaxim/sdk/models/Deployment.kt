package ai.getmaxim.sdk.models

import kotlinx.serialization.*

@Serializable(with = RuleSerializer::class)
sealed class Rule

@Serializable
@SerialName("rule")
data class RuleType(
    val field: String,
    @Serializable(with = AnySerializer::class)
    val value: Any?,
    val operator: String,
    val valueSource: String? = null,
    val exactMatch: Boolean? = null
) : Rule()


@Serializable
@SerialName("ruleGroup")
data class RuleGroupType(
    val not: Boolean,
    val rules: MutableList<Rule>, // Can be RuleGroup or RuleGroupType
    val combinator: String
) : Rule()

@Serializable
data class DeploymentRules(
    val version: Int,
    val query: RuleGroupType? = null
)

@Serializable
data class VersionSpecificDeploymentConfig(
    val id: String,
    val timestamp: String,
    val rules: DeploymentRules,
    val isFallback: Boolean = false,
)

typealias DeploymentVersionDeploymentConfig = Map<String, List<VersionSpecificDeploymentConfig>>

@Serializable
data class Error(val message: String)

@Serializable
data class MaximAPIResponse(
    @Serializable(with = AnySerializer::class)
    val data: Any? = null,
    val error: Error? = null
)

@Serializable
data class SignedUrlResponse(
    val url: String,
)