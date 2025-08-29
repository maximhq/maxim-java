package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.utcNow
import ai.getmaxim.sdk.logger.LogWriter


data class RetrievalConfig(
    override val id: String,
    override val name: String? = null,
    override val spanId: String? = null,
    override val tags: Map<String, String>? = null
) : BaseConfig(id, name = name, spanId = spanId, tags = tags)

class Retrieval(config: RetrievalConfig, writer: LogWriter) :
    EventEmittingBaseContainer(Entity.RETRIEVAL, config, writer) {

    fun setInput(query: String) {
        commit("update", mapOf("input" to query))
    }

    fun addAttachment(attachment: Attachment) {
        commit("upload-attachment", attachment)
    }

    fun setOutput(docs: Any) {
        val finalDocs = when (docs) {
            is String -> listOf(docs)
            is List<*> -> docs
            else -> throw IllegalArgumentException("docs must be a String or a List of Strings")
        }
        commit("end", mapOf("docs" to finalDocs, "endTimestamp" to utcNow()))
    }

    companion object {
        fun setInput(writer: LogWriter, id: String, query: String) {
            commit(writer, Entity.RETRIEVAL, id, "update", mapOf("input" to query))
        }

        fun setOutput(writer: LogWriter, id: String, docs: Any) {
            val finalDocs = when (docs) {
                is String -> listOf(docs)
                is List<*> -> docs
                else -> throw IllegalArgumentException("docs must be a String or a List of Strings")
            }
            commit(writer, Entity.RETRIEVAL, id, "end", mapOf("docs" to finalDocs, "endTimestamp" to utcNow()))
        }

        fun addAttachment(writer: LogWriter, id: String, attachment: Attachment) {
            commit(writer, Entity.RETRIEVAL, id, "upload-attachment", attachment)
        }

        fun end(writer: LogWriter, id: String, data: Any? = null) {
            end(writer, Entity.RETRIEVAL, id, data)
        }

        fun addTag(writer: LogWriter, id: String, key: String, value: String) {
            addTag(writer, Entity.RETRIEVAL, id, key, value)
        }
    }
}