package ai.getmaxim.sdk.models

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun Instant.toIsoString(): String {
    return DateTimeFormatter
        .ISO_INSTANT
        .withZone(ZoneOffset.UTC)
        .format(this)
}


fun JsonElement.instantOrNull(): Instant? {
    when (this) {
        is JsonPrimitive -> {
            return try {
                // Try parsing as ISO 8601
                Instant.parse(this.content)
            } catch (e: DateTimeParseException) {
                val formatter = DateTimeFormatter.ISO_INSTANT
                try {
                    // Try parsing as a custom format
                    LocalDateTime.parse(this.content, formatter).toInstant(ZoneOffset.UTC)
                } catch (e: DateTimeParseException) {
                    // If both attempts fail, throw an exception
                    return null
                }
            }
        }

        else -> null
    }
    return null
}
