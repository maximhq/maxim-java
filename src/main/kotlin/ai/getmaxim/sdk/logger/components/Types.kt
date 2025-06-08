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
    RETRIEVAL;

    override fun toString(): String {
        return name.lowercase(Locale.getDefault())
    }
}

class CommitLog(
    private val entity: Entity,
    private val entityId: String,
    private val action: String,
    private val data: Any?,
) {
    fun serialize(): String {
        val jsonData = MaximJson.encodeToString(data ?: emptyMap<String, Any>())
        return "${entity}{id=$entityId,action=$action,data=$jsonData}"
    }
}