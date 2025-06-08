package ai.getmaxim.sdk.utils

class Queue<T> {
    private val storage: MutableList<T> = mutableListOf()

    fun enqueue(item: T) {
        storage.add(item)
    }

    fun dequeue(): T? {
        return if (storage.isNotEmpty()) storage.removeAt(0) else null
    }

    fun dequeueAll(): List<T> {
        val items = storage.toList()
        storage.clear()
        return items
    }
}