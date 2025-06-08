package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.LogWriter

data class TraceConfig(
    override val id: String,
    override val name: String? = null,
    val sessionId: String? = null,
    override val tags: Map<String, String>? = null
) : BaseConfig(id, name = name, tags = tags)

data class Feedback(
    val score: Int,
    val comment: String? = null
)

class Trace(config: TraceConfig, writer: LogWriter) : EventEmittingBaseContainer(Entity.TRACE, config, writer) {
    init {
        commit(
            "create", mapOf(
                *data().toList().toTypedArray(),
                "sessionId" to config.sessionId
            )
        )
    }

    fun addGeneration(config: GenerationConfig): Generation {
        val generation = Generation(config, writer)
        commit(
            "add-generation", mapOf(
                "id" to config.id,
                *generation.data().toList().toTypedArray()
            )
        )
        return generation
    }

    fun setFeedback(feedback: Feedback) {
        commit("add-feedback", feedback)
    }

    fun addSpan(config: SpanConfig): Span {
        val span = Span(config, writer)
        commit(
            "add-span", mapOf(
                "id" to span.id,
                *span.data().toList().toTypedArray()
            )
        )
        return span
    }

    fun addRetrieval(config: RetrievalConfig): Retrieval {
        val retrieval = Retrieval(config, writer)
        commit(
            "add-retrieval", mapOf(
                "id" to config.id,
                *retrieval.data().toList().toTypedArray()
            )
        )
        return retrieval
    }

    fun setInput(input: String): Trace {
        commit("update", mapOf("input" to input))
        return this
    }

    fun setOutput(output: String): Trace {
        commit("update", mapOf("output" to output))
        return this
    }

    companion object {
        fun addGeneration(writer: LogWriter, id: String, config: GenerationConfig): Generation {
            val generation = Generation(config, writer)
            commit(
                writer, Entity.TRACE, id, "add-generation", mapOf(
                    "id" to config.id,
                    *generation.data().toList().toTypedArray()
                )
            )
            return generation
        }

        fun setFeedback(writer: LogWriter, id: String, feedback: Feedback) {
            commit(writer, Entity.TRACE, id, "add-feedback", feedback)
        }

        fun addSpan(writer: LogWriter, id: String, config: SpanConfig): Span {
            val span = Span(config, writer)
            commit(
                writer, Entity.TRACE, id, "add-span", mapOf(
                    "id" to span.id,
                    *span.data().toList().toTypedArray()
                )
            )
            return span
        }

        fun addRetrieval(writer: LogWriter, id: String, config: RetrievalConfig): Retrieval {
            val retrieval = Retrieval(config, writer)
            commit(
                writer, Entity.TRACE, id, "add-retrieval", mapOf(
                    "id" to config.id,
                    *retrieval.data().toList().toTypedArray()
                )
            )
            return retrieval
        }

        fun setInput(writer: LogWriter, id: String, input: String) {
            commit(writer, Entity.TRACE, id, "update", mapOf("input" to input))
        }

        fun setOutput(writer: LogWriter, id: String, output: String) {
            commit(writer, Entity.TRACE, id, "update", mapOf("output" to output))
        }

        fun end(writer: LogWriter, id: String, data: Any? = null) {
            end(writer, Entity.TRACE, id, data)
        }

        fun addTag(writer: LogWriter, id: String, key: String, value: String) {
            addTag(writer, Entity.TRACE, id, key, value)
        }

        fun addEvent(writer: LogWriter, id: String, name: String, tags: Map<String, String>? = null) {
            addEvent(writer, Entity.TRACE, id, name, tags)
        }
    }
}

