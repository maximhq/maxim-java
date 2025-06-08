package ai.getmaxim.sdk.cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MaximInMemoryCache : MaximCache {
    private val cache: MutableMap<String, String> = mutableMapOf()

    override suspend fun getAllKeys(): List<String> = withContext(Dispatchers.Default) {
        cache.keys.toList()
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.Default) {
        cache[key]
    }

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.Default) {
        cache[key] = value
    }

    override suspend fun delete(key: String): Unit = withContext(Dispatchers.Default) {
        cache.remove(key)
    }
}