package ai.getmaxim.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromptChain(
    val promptChainId: String,
    val version: Int,
    val versionId: String,
    val nodes: List<NodeWithOrder>?
)

@Serializable
data class NodeWithOrder(
    val order: Int,
    val node: Node
)

@Serializable
sealed class Node {
    data class PromptNode(val prompt: Prompt) : Node()
    data class CodeBlockNode(val code: String) : Node()
    data class ApiNode(val api: Api) : Node() {
        data class Api(
            val url: String,
            val method: HttpMethod,
            val params: List<Param>? = null,
            val headers: List<Header>? = null,
            val body: String? = null
        ) {
            data class Param(val id: String, val key: String, val value: String)
            data class Header(val id: String, val key: String, val value: String)
        }
        enum class HttpMethod {
            GET, POST, PUT, DELETE, PATCH
        }
    }
}

@Serializable
data class PromptChainVersionConfig(
    val nodes: List<NodeWithOrder>
)

@Serializable
data class PromptChainVersion(
    val id: String,
    val version: Int,
    val promptChainId: String,
    val description: String? = null,
    val config: PromptChainVersionConfig? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
open class PromptChainWithVersionsAndRules(
    open val folderId: String,
    open val rules: DeploymentVersionDeploymentConfig,
    open val versions: List<PromptChainVersion>,
    open val fallbackVersion: PromptChainVersion?
)

@Serializable
@SerialName("PromptChainWithVersionAndRulesAndId")
data class PromptChainWithVersionAndRulesAndId(
    val promptChainId: String,
    @SerialName("derivedFolderId")
    override val folderId: String,
    @SerialName("derivedRules")
    override val rules: DeploymentVersionDeploymentConfig,
    @SerialName("derivedVersions")
    override val versions: List<PromptChainVersion>,
    @SerialName("derivedFallbackVersion")
    override val fallbackVersion: PromptChainVersion?
) : PromptChainWithVersionsAndRules(folderId, rules, versions, fallbackVersion)

@Serializable
data class MaximApiPromptChainResponse(
    val data: PromptChainWithVersionAndRulesAndId? = null,
    val error: Error? = null
)

@Serializable
data class MaximApiPromptChainsResponse(
    val data: List<PromptChainWithVersionAndRulesAndId> = emptyList(),
    val error: Error? = null
)