package ai.getmaxim.sdk.utils
import kotlinx.coroutines.sync.Mutex as KotlinxMutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job

class Mutex(private val name: String) {
    private val mutex = KotlinxMutex()
    private val owner = AtomicReference<Job?>(null)

    suspend fun acquire() {
        mutex.lock()
        owner.set(coroutineContext[Job])
    }

    suspend fun release() {
        val currentJob = coroutineContext[Job]
        if (owner.get() != currentJob) {
            throw IllegalStateException("Mutex $name can only be released by the coroutine that acquired it")
        }
        owner.set(null)
        mutex.unlock()
    }

    suspend fun <T> withLock(block: suspend () -> T): T {
        return mutex.withLock {
            owner.set(coroutineContext[Job])
            try {
                block()
            } finally {
                owner.set(null)
            }
        }
    }

    fun isLocked(): Boolean = mutex.isLocked

    fun getOwner(): Job? = owner.get()
}