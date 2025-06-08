package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.logger.utcNow
import ai.getmaxim.sdk.logger.LogWriter
import ai.getmaxim.sdk.models.Tags
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.reflect.full.memberProperties


@Serializable
abstract class BaseConfig(
    open val id: String,
    open val spanId: String? = null,
    open val name: String? = null,
    open val tags: Map<String, String>? = null
)

abstract class BaseContainer(
    protected val entity: Entity,
    config: BaseConfig,
    protected val writer: LogWriter
) {
    protected var containerId: String = config.id
    protected var name: String? = config.name
    protected var spanId: String? = config.spanId
    protected val startTimestamp: Instant = utcNow()
    protected var endTimestamp: Instant? = null
    protected var tags: MutableMap<String, String> = config.tags?.toMutableMap() ?: mutableMapOf()

    val id: String
        get() = containerId

    open fun addTag(key: String, value: String) {
        commit("update", mapOf("tags" to mapOf(key to value)))
    }

    open fun end() {
        endTimestamp = utcNow()
        commit("end", mapOf("endTimestamp" to endTimestamp))
    }

    open fun data(): Map<String, Any?> = mapOf(
        "name" to name,
        "spanId" to spanId,
        "tags" to tags,
        "startTimestamp" to startTimestamp,
        "endTimestamp" to endTimestamp
    )

    protected fun commit(action: String, data: Any? = null) {
        writer.commit(CommitLog(entity, id, action, data ?: this.data()))
    }

    companion object {
        fun addTag(writer: LogWriter, entity: Entity, id: String, key: String, value: String) {
            commit(writer, entity, id, "update", mapOf("tags" to mapOf(key to value)))
        }

        fun end(writer: LogWriter, entity: Entity, id: String, data: Any? = null) {
            val endData = when {
                data == null -> mapOf("endTimestamp" to System.currentTimeMillis())
                data is Map<*, *> && !data.containsKey("endTimestamp") -> {
                    data.toMutableMap().apply {
                        this["endTimestamp"] = System.currentTimeMillis()
                    }
                }

                data !is Map<*, *> && !data::class.memberProperties.any { it.name == "endTimestamp" } -> {
                    mapOf(
                        "originalData" to data,
                        "endTimestamp" to System.currentTimeMillis()
                    )
                }

                else -> data
            }
            commit(writer, entity, id, "end", endData)
        }

        @JvmStatic
        protected fun commit(
            writer: LogWriter,
            entity: Entity,
            id: String,
            action: String,
            data: Any? = null
        ) {
            writer.commit(CommitLog(entity, id, action, data ?: {}))
        }
    }
}


abstract class EventEmittingBaseContainer(
    entity: Entity,
    config: BaseConfig,
    writer: LogWriter
) : BaseContainer(entity, config, writer) {

    fun addEvent(id: String, name: String, tags: Map<String, String>? = null) {
        commit(
            "add-event", mapOf(
                "id" to id,
                "name" to name,
                "timestamp" to utcNow(),
                "tags" to tags
            )
        )
    }

    companion object {
        fun addEvent(writer: LogWriter, entity: Entity, id: String, name: String, tags: Tags? = null) {
            commit(
                writer, entity, id, "add-event", mapOf(
                    "name" to name,
                    "timestamp" to utcNow(),
                    "tags" to tags
                )
            )
        }
    }
}

