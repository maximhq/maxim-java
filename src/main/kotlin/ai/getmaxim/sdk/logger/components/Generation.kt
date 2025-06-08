package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.uniqueId
import ai.getmaxim.sdk.logger.utcNow
import ai.getmaxim.sdk.logger.LogWriter
import ai.getmaxim.sdk.models.AnySerializer
import kotlinx.serialization.Serializable

@Serializable
data class GenerationError(
    val message: String,
    val code: String? = null,
    val type: String? = null,
)

@Serializable
data class ChatCompletionResult(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<ChatCompletionChoice>,
    val usage: Usage,
    val error: GenerationError? = null,
)

@Serializable
data class TextCompletionResult(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<TextCompletionChoice>,
    val usage: Usage,
    val error: GenerationError? = null,
)

@Serializable
data class ToolCallFunction(
    val arguments: String,
    val name: String,
)

@Serializable
data class ToolCall(
    val id: String,
    val function: ToolCallFunction,
    val type: String,
)

@Serializable
data class ChatCompletionMessage(
    val role: String,
    val content: String?,
    val function_call: ToolCallFunction? = null,
    val tool_calls: List<ToolCall>? = null,
)

@Serializable
data class ChatCompletionChoice(
    val index: Int,
    val message: List<ChatCompletionMessage>,
    @Serializable(with = AnySerializer::class)
    val logprobs: Any? = null,
    val finish_reason: String,
)

@Serializable
data class TextCompletionChoice(
    val index: Int,
    val text: String,
    @Serializable(with = AnySerializer::class)
    val logprobs: Any? = null,
    val finish_reason: String,
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
)

@Serializable
sealed class CompletionRequestContent {
    @Serializable
    data class Text(val text: String) : CompletionRequestContent()
    @Serializable
    data class ImageUrl(val url: String, val detail: String? = null) : CompletionRequestContent()
}

@Serializable
data class CompletionRequest(
    val role: String,
    @Serializable(with = AnySerializer::class)
    val content: Any,
)

data class GenerationConfig(
    override val id: String,
    override val name: String? = null,
    override val spanId: String? = null,
    val provider: String,
    val model: String,
    val maximPromptId: String? = null,
    val messages: List<CompletionRequest>,
    val modelParameters: Map<String, @Serializable(with = AnySerializer::class) Any>,
    override val tags: Map<String, String>? = null,
) : BaseConfig(id, name = name, spanId = spanId, tags = tags)

class Generation(config: GenerationConfig, writer: LogWriter) : BaseContainer(Entity.GENERATION, config, writer) {
    private var model: String? = config.model
    private var provider: String? = config.provider
    private var maximPromptId: String? = config.maximPromptId
    private var messages: MutableList<CompletionRequest>? = config.messages.toMutableList()
    private var modelParameters: MutableMap<String, Any>? =
        config.modelParameters.toMutableMap()

    fun setModel(model: String) {
        this.model = model
        commit("update", mapOf("model" to model))
    }

    fun addMessage(message: List<CompletionRequest>) {
        messages = (messages ?: mutableListOf()).apply { addAll(message) }
        commit("update", mapOf("messages" to message))
    }

    fun setModelParameters(modelParameters: Map<String, Any>) {
        commit("update", mapOf("modelParameters" to modelParameters))
    }

    fun setResult(result: TextCompletionResult) {
        commit("result", mapOf("result" to result))
        end()
    }

    fun setResult(result: ChatCompletionResult) {
        commit("result", mapOf("result" to result))
        end()
    }

    fun error(error: GenerationError) {
        commit("result", mapOf("result" to mapOf("error" to error, "id" to uniqueId())))
        end()
    }

    override fun data(): Map<String, Any?> = super.data().toMutableMap().apply {
        put("provider", provider)
        put("model", model)
        put("maximPromptId", maximPromptId)
        put("messages", messages)
        put("modelParameters", modelParameters)
    }

    companion object {
        fun setModel(writer: LogWriter, id: String, model: String) {
            commit(writer, Entity.GENERATION, id, "update", mapOf("model" to model))
        }

        fun addMessage(writer: LogWriter, id: String, message: Any) {
            commit(writer, Entity.GENERATION, id, "update", mapOf("messages" to listOf(message)))
        }

        fun setModelParameters(writer: LogWriter, id: String, modelParameters: Map<String, Any>) {
            commit(writer, Entity.GENERATION, id, "update", mapOf("modelParameters" to modelParameters))
        }

        fun setResult(writer: LogWriter, id: String, result: TextCompletionResult) {
            commit(writer, Entity.GENERATION, id, "result", mapOf("result" to result))
            end(writer, Entity.GENERATION, id, mapOf("endTimestamp" to utcNow()))
        }

        fun setResult(writer: LogWriter, id: String, result: ChatCompletionResult) {
            commit(writer, Entity.GENERATION, id, "result", mapOf("result" to result))
            end(writer, Entity.GENERATION, id, mapOf("endTimestamp" to utcNow()))
        }

        fun error(writer: LogWriter, id: String, error: GenerationError) {
            commit(
                writer,
                Entity.GENERATION,
                id,
                "result",
                mapOf("result" to mapOf("error" to error, "id" to uniqueId()))
            )
            end(writer, Entity.GENERATION, id, mapOf("endTimestamp" to utcNow()))
        }

        fun end(writer: LogWriter, id: String, data: Any? = null) {
            end(writer, Entity.GENERATION, id, data)
        }

        fun addTag(writer: LogWriter, id: String, event: String, data: Map<String, Any>? = null) {
            commit(writer, Entity.GENERATION, id, event, data)
        }
    }
}
