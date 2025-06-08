package ai.getmaxim.sdk.utils

import java.security.SecureRandom
import java.net.InetAddress

fun generateUniqueId(): String {
    val timestamp = System.currentTimeMillis().toString(36)
    val hostname = InetAddress.getLocalHost().hostName
    val random = SecureRandom()
    val randomBytes = ByteArray(4)
    random.nextBytes(randomBytes)
    val randomHex = randomBytes.joinToString("") { "%02x".format(it) }
    return "$timestamp-$hostname-$randomHex"
}