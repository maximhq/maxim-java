package ai.getmaxim.sdk.logger.components

import ai.getmaxim.sdk.models.MaximJson
import kotlinx.serialization.encodeToString
import java.util.*

enum class Entity {
    SESSION,
    TRACE,
    SPAN,
    GENERATION,
    FEEDBACK,
    RETRIEVAL,
    TOOL_CALL,
    ERROR;

    override fun toString(): String {
        return name.lowercase(Locale.getDefault())
    }
}

class CommitLog(
    val entity: Entity,
    val entityId: String,
    val action: String,
    val data: Any?,
) {
    fun serialize(): String {
        val jsonData = MaximJson.encodeToString(data ?: emptyMap<String, Any>())
        return "${entity}{id=$entityId,action=$action,data=$jsonData}"
    }
}