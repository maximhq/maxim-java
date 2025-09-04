package ai.getmaxim.sdk.logger

import ai.getmaxim.sdk.Maxim
import ai.getmaxim.sdk.apis.MaximAPI
import ai.getmaxim.sdk.logger.components.*
import ai.getmaxim.sdk.utils.Mutex
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
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

@kotlinx.serialization.ExperimentalSerializationApi
class LogWriter(private val config: LogWriterConfig) : CoroutineScope {
    private val logger = LoggerFactory.getLogger(Maxim::class.java).also {
        if (config.isDebug)
            it.atLevel(Level.DEBUG)
        else
            it.atLevel(Level.INFO)
    }
    private val id = generateUniqueId()
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job
    private val queue: Queue<CommitLog> = LinkedList()
    private val uploadAttachmentQueue: Queue<CommitLog> = LinkedList()
    private val uploadAttachmentsMutex = Mutex("log-writer-attachments")
    private val mutex = Mutex("log-writer")
    private var flushJob: Job? = null
    private val logsDir = File(System.getProperty("java.io.tmpdir"), "maxim-sdk/$id/maxim-logs")

    init {
        if (config.autoFlush) {
            flushJob = this@LogWriter.launch {
                while (isActive) {
                    run { flush() }
                    run { flushAttachments() }
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
            logs.forEach { logger.debug(it.serialize()) }
            val content = logs.joinToString("\n") { it.serialize() }
            MaximAPI.pushLogs(config.baseUrl, config.apiKey, config.repositoryId, content)
        } catch (e: Exception) {
            writeToFile(logs)
            throw e
        }
    }

    private suspend fun flushLogAttachments(logs: List<CommitLog>) {
        logs.asFlow()
            .map { it ->
                logger.debug("Uploading attachment: ${it.serialize()}")
                logger.debug("Attachment data: ${it.data}")
                val attachment: Attachment = it.data as Attachment? ?: return@map
                val key =
                    "${config.repositoryId}/${it.entity.toString()}/${it.entityId}/files/original/${attachment.id}"
                when (attachment) {
                    is FileDataAttachment -> {
                        val resp = MaximAPI.getUploadUrl(
                            config.baseUrl,
                            config.apiKey,
                            key,
                            attachment.mimeType ?: "application/octet-stream",
                            attachment.data.size
                        )
                        MaximAPI.uploadToSignedUrl(
                            resp.url,
                            attachment.data,
                            attachment.mimeType ?: "application/octet-stream"
                        )
                        commit(
                            CommitLog(
                                entity = it.entity,
                                entityId = it.entityId,
                                data = it.data,
                                action = "add-attachment"
                            )
                        )
                    }

                    is FileAttachment -> {
                        // reading file from the path
                        val bytes = File("image.png").readBytes()
                        val resp = MaximAPI.getUploadUrl(
                            config.baseUrl,
                            config.apiKey,
                            key,
                            attachment.mimeType ?: "application/octet-stream",
                            bytes.size
                        )
                        MaximAPI.uploadToSignedUrl(
                            resp.url,
                            bytes,
                            attachment.mimeType ?: "application/octet-stream"
                        )
                        commit(
                            CommitLog(
                                entity = it.entity,
                                entityId = it.entityId,
                                data = it.data,
                                action = "add-attachment"
                            )
                        )
                    }

                    is UrlAttachment -> {
                        // Then we just attach URL as is
                        commit(
                            CommitLog(
                                entity = it.entity,
                                entityId = it.entityId,
                                data = it.data,
                                action = "add-attachment"
                            )
                        )
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .buffer(3)
            .collect { }
        // Currently we are keeping to 3 parallel uploads
        // Later we can make them configurable
    }

    fun commit(log: CommitLog) {
        if (log.action == "upload-attachment") {
            logger.debug("Adding attachment to upload queue: ${log.serialize()}")
            uploadAttachmentQueue.add(log)
            return
        }
        queue.add(log)
    }

    private suspend fun flushAttachments() {
        logger.debug("Flushing attachments")
        uploadAttachmentsMutex.withLock {
            val items = uploadAttachmentQueue.toList()
            uploadAttachmentQueue.clear()
            if (items.isEmpty()) {
                logger.debug("No attachments to flush")
                return@withLock
            }
            logger.debug("Flushing attachments ${items.size}")
            runCatching {
                flushLogAttachments(items)
            }.onFailure { e ->
                logger.error("Couldn't flush file attachments: ${e.message}")
            }.onSuccess {
                logger.debug("Attachment file flush complete")
            }
        }
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
        try {
            logger.debug("Cleaning up")
            flushAttachments()
            flush()
            flushJob?.cancelAndJoin()
            job.cancelAndJoin()
        } catch (e: Exception) {
            logger.error("Error while cleaning up: ${e.message}")
        }
    }

    companion object {
        fun generateUniqueId(): String = UUID.randomUUID().toString()
    }
}
