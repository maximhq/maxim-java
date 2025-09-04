package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.LogWriter
import ai.getmaxim.sdk.models.Tags

data class SpanConfig(
    override val id: String,
    override val name: String? = null,
    override val tags: Map<String, String>? = null
) : BaseConfig(id, name = name, tags = tags)

class Span(config: SpanConfig, writer: LogWriter) : EventEmittingBaseContainer(ENTITY, config, writer) {
    init {
        commit("create")
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

    fun addSpan(config: SpanConfig): Span {
        val span = Span(config, writer)
        commit(
            "add-span", mapOf(
                "id" to config.id,
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

    companion object {
        private val ENTITY = Entity.SPAN

        fun addGeneration(writer: LogWriter, id: String, config: GenerationConfig): Generation {
            val generation = Generation(config, writer)
            commit(
                writer, ENTITY, id, "add-generation", mapOf(
                    "id" to config.id,
                    *generation.data().toList().toTypedArray()
                )
            )
            return generation
        }

        fun addSpan(writer: LogWriter, id: String, config: SpanConfig): Span {
            val span = Span(config, writer)
            commit(
                writer, ENTITY, id, "add-span", mapOf(
                    "id" to config.id,
                    *span.data().toList().toTypedArray()
                )
            )
            return span
        }

        fun addRetrieval(writer: LogWriter, id: String, config: RetrievalConfig): Retrieval {
            val retrieval = Retrieval(config, writer)
            commit(
                writer, ENTITY, id, "add-retrieval", mapOf(
                    "id" to config.id,
                    *retrieval.data().toList().toTypedArray()
                )
            )
            return retrieval
        }

        fun end(writer: LogWriter, id: String, data: Any? = null) {
            end(writer, ENTITY, id, data)
        }

        fun addTag(writer: LogWriter, id: String, key: String, value: String) {
            addTag(writer, ENTITY, id, key, value)
        }

        fun event(writer: LogWriter, id: String, name: String, tags: Tags? = null) {
            addEvent(writer, ENTITY, id, name, tags)
        }
    }
}