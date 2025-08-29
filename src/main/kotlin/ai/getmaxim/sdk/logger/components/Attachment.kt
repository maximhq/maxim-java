package ai.getmaxim.sdk.logger.components

import java.time.Instant
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class Attachment constructor(
    open val id: String? = UUID.randomUUID().toString(),
    open val name: String? = null,
    open val size: Int? = null,
    open val mimeType: String? = null,
    open val tags: Map<String, String>? = null,
    open val metadata: Map<String, Any>? = null,
    open val timestamp: Instant? = null
)

data class FileAttachment(
    val path: String,
    override val id: String? = UUID.randomUUID().toString(),
    override val name: String? = null,
    override val size: Int? = null,
    override val mimeType: String? = null,
    override val tags: Map<String, String>? = null,
    override val metadata: Map<String, Any>? = null,
    override val timestamp: Instant? = null
) : Attachment(
    id = id,
    name = name,
    size = size,
    mimeType = mimeType,
    tags = tags,
    metadata = metadata,
    timestamp = timestamp
)

data class FileDataAttachment(
    val data: ByteArray,
    override val id: String? = UUID.randomUUID().toString(),
    override val name: String? = null,
    override val size: Int? = null,
    override val mimeType: String? = null,
    override val tags: Map<String, String>? = null,
    override val metadata: Map<String, Any>? = null,
    override val timestamp: Instant? = null
) : Attachment(
    id = id,
    name = name,
    size = size,
    mimeType = mimeType,
    tags = tags,
    metadata = metadata,
    timestamp = timestamp
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileDataAttachment

        if (size != other.size) return false
        if (!data.contentEquals(other.data)) return false
        if (id != other.id) return false
        if (name != other.name) return false
        if (mimeType != other.mimeType) return false
        if (tags != other.tags) return false
        if (metadata != other.metadata) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = size ?: 0
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (id?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (tags?.hashCode() ?: 0)
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        return result
    }
}

data class UrlAttachment(
    val url: String,
    override val id: String? = UUID.randomUUID().toString(),
    override val name: String? = null,
    override val size: Int? = null,
    override val mimeType: String? = null,
    override val tags: Map<String, String>? = null,
    override val metadata: Map<String, Any>? = null,
    override val timestamp: Instant? = null
) : Attachment(
    id = id,
    name = name,
    size = size,
    mimeType = mimeType,
    tags = tags,
    metadata = metadata,
    timestamp = timestamp
)

