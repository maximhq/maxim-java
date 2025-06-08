package ai.getmaxim.sdk.logger

import ai.getmaxim.sdk.apis.MaximAPI
import ai.getmaxim.sdk.logger.components.CommitLog
import ai.getmaxim.sdk.utils.Mutex
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext


data class LogWriterConfig(
    val baseUrl: String,
    val apiKey: String,
    val repositoryId: String,
    val autoFlush: Boolean = false,
    val flushInterval: Int = 10,
    val isDebug: Boolean = false
)

class LogWriter(private val config: LogWriterConfig) : CoroutineScope {
    private val logger: Logger = LoggerFactory.getLogger(LogWriter::class.java)
    private val id = generateUniqueId()
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job
    private val queue: Queue<CommitLog> = LinkedList()
    private val mutex = Mutex("log-writer")
    private val isDebug = config.isDebug
    private var flushJob: Job? = null
    private val logsDir = File(System.getProperty("java.io.tmpdir"), "maxim-sdk/$id/maxim-logs")

    init {
        if (config.autoFlush) {
            flushJob = this@LogWriter.launch {
                while (isActive) {
                    flush()
                    delay(config.flushInterval.toLong() * 1000)
                }
            }
        }
    }

    private suspend fun writeToFile(logs: List<CommitLog>): String {
        return withContext(Dispatchers.IO) {
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            val content = logs.joinToString("\n") { it.serialize() }
            val filename = "logs-${Instant.now()}.log"
            val file = File(logsDir, filename)
            file.writeText(content)
            file.absolutePath
        }
    }

    private suspend fun flushLogFiles() {
        if (!logsDir.exists()) return

        withContext(Dispatchers.IO) {
            logsDir.listFiles()?.forEach { file ->
                val logs = file.readText()
                try {
                    MaximAPI.pushLogs(config.baseUrl, config.apiKey, config.repositoryId, logs)
                    file.delete()
                } catch (e: Exception) {
                    logger.error("Error while pushing logs: ${e.message}")
                }
            }
        }
    }

    private suspend fun flushLogs(logs: List<CommitLog>) {
        try {
            flushLogFiles()
            if (isDebug)
                logs.forEach { logger.debug(it.serialize()) }
            val content = logs.joinToString("\n") { it.serialize() }
            MaximAPI.pushLogs(config.baseUrl, config.apiKey, config.repositoryId, content)
        } catch (e: Exception) {
            writeToFile(logs)
            throw e
        }
    }

    fun commit(log: CommitLog) {
        logger.debug("Committing log: ${log.serialize()}")
        queue.add(log)
    }

    private suspend fun flush() {
        mutex.withLock {
            val items = queue.toList()
            queue.clear()
            if (items.isEmpty()) {
                logger.debug("No logs to flush")
                return@withLock
            }
            logger.debug("Flushing logs")
            runCatching {
                flushLogs(items)
            }.onFailure { e ->
                logger.error("Couldn't flush logs: ${e.message}")
            }.onSuccess {
                logger.debug("Flush complete")
            }
        }
    }

    suspend fun cleanup() {
        flushJob?.cancel()
        flush()
    }

    companion object {
        fun generateUniqueId(): String = UUID.randomUUID().toString()
    }
}
