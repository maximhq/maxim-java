package ai.getmaxim.sdk.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

typealias Tags = Map<String, @Serializable(with = AnySerializer::class) Any>

@Serializable
sealed class CompletionRequestContent {
    @Serializable
    data class Text(val text: String) : CompletionRequestContent()

    @Serializable
    data class ImageUrl(val url: String, val detail: String? = null) : CompletionRequestContent()
}

@Serializable(with = MessageContentSerializer::class)
sealed class MessageContent {
    @Serializable
    data class StringContent(val value: String) : MessageContent()

    @Serializable
    data class RequestContent(val value: CompletionRequestContent) : MessageContent()
}

@Serializable
data class Message(
    val role: String,
    @Contextual
    val content: MessageContent
)

@Serializable
data class Prompt(
    val promptId: String,
    val version: Int,
    val versionId: String,
    val messages: List<Message>,
    val modelParameters: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val model: String,
    val tags: Tags? = null
)

@Serializable
data class PromptVersionAuthor(
    val id: String
)


@Serializable
data class PromptVersionConfig(
    val id: String? = null,
    val modelId: String? = null,
    val messages: List<Message>,
    val modelParameters: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val model: String,
    val provider: String,
    val author: PromptVersionAuthor? = null,
    val tags: Tags? = null
)

@Serializable
data class PromptVersion(
    val id: String,
    val version: Int,
    val promptId: String,
    val description: String? = null,
    val config: PromptVersionConfig,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class PromptVersionsAndRules(
    val folderId: String? = null,
    val rules: DeploymentVersionDeploymentConfig,
    val versions: List<PromptVersion>,
    val fallbackVersion: PromptVersion? = null
)

@Serializable
data class MaximApiPromptResponse(
    val data: PromptVersionsAndRules,
    val error: Error? = null
)

@Serializable
data class PromptWithVersionsAndRules(
    val promptId: String,
    val folderId: String? = null,
    val rules: DeploymentVersionDeploymentConfig,
    val versions: List<PromptVersion>,
    val fallbackVersion: PromptVersion? = null
)

@Serializable
data class MaximApiPromptsResponse(
    val data: List<PromptWithVersionsAndRules>,
    val error: Error? = null
)