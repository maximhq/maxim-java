package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.LogWriter

data class ErrorConfig(
    override val id: String,
    val message: String,
    val code: String?,
    val type: String?,
    override val name: String? = null,
    override val spanId: String? = null,
    override val tags: Map<String, String>? = null,
    override val metadata: Map<String, Any>? = null
) : BaseConfig(id, name = name, spanId = spanId, tags = tags)


class Error(config: ErrorConfig, writer: LogWriter) : BaseContainer(Entity.ERROR, config, writer) {
    private var message: String? = config.message
    private var code: String? = config.code
    private var type: String? = config.type

    fun setMessage(message: String) {
        commit("update", mapOf("message" to message))
    }

    fun setCode(code: String) {
        commit("update", mapOf("code" to code))
    }

    fun setType(type: String) {
        commit("update", mapOf("type" to type))
    }

    override fun data(): Map<String, Any?> = super.data().toMutableMap().apply {
        put("message", message)
        put("code", code)
        put("type", type)
    }

    companion object {
        fun setMessage(writer: LogWriter, id: String, message: String) {
            commit(writer, Entity.ERROR, id, "update", mapOf("message" to message))
        }

        fun setCode(writer: LogWriter, id: String, code: String) {
            commit(writer, Entity.ERROR, id, "update", mapOf("code" to code))
        }

        fun setType(writer: LogWriter, id: String, type: String) {
            commit(writer, Entity.ERROR, id, "update", mapOf("type" to type))
        }
    }
}