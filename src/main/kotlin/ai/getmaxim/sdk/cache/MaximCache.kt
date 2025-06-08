package ai.getmaxim.sdk.cache

interface MaximCache {
    suspend fun getAllKeys(): List<String>
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun delete(key: String)
}