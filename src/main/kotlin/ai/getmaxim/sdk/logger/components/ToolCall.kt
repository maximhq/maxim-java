package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.LogWriter

data class ToolCallConfig(
    override val id: String,
    override val name: String? = null,
    val description: String,
    override val spanId: String? = null,
    val args: String? = null,
    override val tags: Map<String, String>? = null
) : BaseConfig(id, name = name, spanId = spanId, tags = tags)

data class ToolCallError(
    val message: String,
    val code: String? = null,
    val type: String? = null
)

class ToolCall(config: ToolCallConfig, writer: LogWriter) : BaseContainer(Entity.TOOL_CALL, config, writer) {
    private var description: String = config.description
    private var args: String? = config.args

    fun setDescription(description: String) {
        commit("update", mapOf("description" to description))
    }

    fun setArgs(args: String) {
        commit("update", mapOf("args" to args))
    }

    fun result(result: String) {
        commit("result", mapOf("result" to result))
        end()
    }

    fun error(error: ToolCallError) {
        commit("error", mapOf("error" to error))
        end()
    }

    override fun data(): Map<String, Any?> = super.data().toMutableMap().apply {
        put("description", description)
        put("args", args)
    }

    companion object {
        fun setDescription(writer: LogWriter, id: String, description: String) {
            commit(writer, Entity.TOOL_CALL, id, "update", mapOf("description" to description))
        }

        fun setArgs(writer: LogWriter, id: String, args: String) {
            commit(writer, Entity.TOOL_CALL, id, "update", mapOf("args" to args))
        }

        fun addTag(writer: LogWriter, id: String, event: String, data: Map<String, Any>? = null) {
            commit(writer, Entity.TOOL_CALL, id, event, data)
        }

        fun setResult(writer: LogWriter, id: String, result: String) {
            commit(writer, Entity.TOOL_CALL, id, "result", result)
        }

        fun setError(writer: LogWriter, id: String, error: ToolCallError) {
            commit(writer, Entity.TOOL_CALL, id, "error", error)
        }
    }
}