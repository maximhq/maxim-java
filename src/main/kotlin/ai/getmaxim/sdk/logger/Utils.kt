package ai.getmaxim.sdk.logger

import java.time.Instant
import java.util.*

fun utcNow(): Instant {
    return Instant.now()
}

fun uniqueId(): String {
    return UUID.randomUUID().toString()
}