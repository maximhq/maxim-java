package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.LogWriter

data class SessionConfig(
    override val id: String,
    override val name: String? = null,
    override val tags: Map<String, String>? = null
) : BaseConfig(id = id, name = name, tags = tags)

class Session(config: SessionConfig, writer: LogWriter) : EventEmittingBaseContainer(ENTITY, config, writer) {

    init {
        commit("create")
    }

    fun setFeedback(feedback: Feedback) {
        commit("add-feedback", feedback)
    }

    fun addTrace(config: TraceConfig): Trace {
        return Trace(
            config.copy(sessionId = this.id),
            this.writer
        )
    }

    companion object {
        private val ENTITY = Entity.SESSION

        fun setFeedback(writer: LogWriter, id: String, feedback: Feedback) {
            commit(writer, ENTITY, id, "add-feedback", feedback)
        }

        fun addTrace(writer: LogWriter, id: String, config: TraceConfig): Trace {
            return Trace(config.copy(sessionId = id), writer)
        }

        fun end(writer: LogWriter, id: String, data: Any? = null) {
            end(writer, ENTITY, id, data)
        }

        fun addTag(writer: LogWriter, id: String, key: String, value: String) {
            addTag(writer, ENTITY, id, key, value)
        }

        fun addEvent(writer: LogWriter, sessionId: String, name: String, tags: Map<String, String>? = null) {
            addEvent(writer, ENTITY, sessionId, name, tags)
        }
    }
}