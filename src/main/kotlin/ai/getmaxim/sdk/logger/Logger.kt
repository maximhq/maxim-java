package ai.getmaxim.sdk.logger

import ai.getmaxim.sdk.logger.components.*
import ai.getmaxim.sdk.models.Tags
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

data class LoggerConfig(
    val id: String,
    val autoFlush: Boolean = true,
    val flushIntervalSeconds: Int = 10,
)

class Logger(
    config: LoggerConfig,
    apiKey: String,
    baseUrl: String,
    private val isDebug: Boolean = false,
) {
    private val _id: String = config.id
    private val writer: LogWriter

    init {
        require(config.id.isNotEmpty()) { "Logger must be initialized with id of the logger" }
        writer = LogWriter(
            LogWriterConfig(
                isDebug = isDebug,
                autoFlush = config.autoFlush,
                flushInterval = config.flushIntervalSeconds,
                baseUrl = baseUrl,
                apiKey = apiKey,
                repositoryId = config.id
            )
        )
    }

    val id: String
        get() = _id

    fun session(config: SessionConfig): Session {
        return Session(config, writer)
    }

    fun trace(config: TraceConfig): Trace {
        return Trace(config, writer)
    }

    fun cleanup(): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            runBlocking { writer.cleanup() }
            null
        }
    }

    // Session methods
    fun sessionAddTag(sessionId: String, key: String, value: String) {
        Session.addTag(writer, sessionId, key, value)
    }

    fun sessionEnd(sessionId: String, data: Any? = null) {
        Session.end(writer, sessionId, data)
    }

    fun sessionAddEvent(sessionId: String, event: String, tags: Map<String, String>? = null) {
        Session.addEvent(writer, sessionId, event, tags)
    }

    fun sessionAddFeedback(sessionId: String, feedback: Feedback) {
        Session.setFeedback(writer, sessionId, feedback)
    }

    fun sessionAddTrace(sessionId: String, config: TraceConfig): Trace {
        return Session.addTrace(writer, sessionId, config)
    }

    // Trace methods
    fun traceAddGeneration(traceId: String, config: GenerationConfig) {
        Trace.addGeneration(writer, traceId, config)
    }

    fun traceAddRetrieval(traceId: String, config: RetrievalConfig) {
        Trace.addRetrieval(writer, traceId, config)
    }

    fun traceSetOutput(traceId: String, output: String) {
        Trace.setOutput(writer, traceId, output)
    }

    fun traceSetInput(traceId: String, input: String) {
        Trace.setInput(writer, traceId, input)
    }

    fun traceAddSpan(traceId: String, config: SpanConfig) {
        Trace.addSpan(writer, traceId, config)
    }

    fun traceAddTag(traceId: String, key: String, value: String) {
        Trace.addTag(writer, traceId, key, value)
    }

    fun traceAddEvent(traceId: String, event: String, tags: Map<String, String>? = null) {
        Trace.addEvent(writer, traceId, event, tags)
    }

    fun traceSetFeedback(traceId: String, feedback: Feedback) {
        Trace.setFeedback(writer, traceId, feedback)
    }

    fun traceEnd(traceId: String, data: Any? = null) {
        Trace.end(writer, traceId, data)
    }

    // Generation methods
    fun generationSetModel(generationId: String, model: String) {
        Generation.setModel(writer, generationId, model)
    }

    fun generationAddMessage(generationId: String, message: List<CompletionRequest>) {
        Generation.addMessage(writer, generationId, message)
    }

    fun generationSetModelParameters(generationId: String, modelParameters: Map<String, Any>) {
        Generation.setModelParameters(writer, generationId, modelParameters)
    }

    fun generationSetResult(generationId: String, result: TextCompletionResult) {
        Generation.setResult(writer, generationId, result)
    }

    fun generationSetResult(generationId: String, result: ChatCompletionResult) {
        Generation.setResult(writer, generationId, result)
    }

    fun generationSetError(generationId: String, error: GenerationError) {
        Generation.error(writer, generationId, error)
    }

    fun generationEnd(generationId: String, data: Any? = null) {
        Generation.end(writer, generationId, data)
    }

    // Span methods
    fun spanAddGeneration(spanId: String, config: GenerationConfig) {
        Span.addGeneration(writer, spanId, config)
    }

    fun spanRetrieval(spanId: String, config: RetrievalConfig) {
        Span.addRetrieval(writer, spanId, config)
    }

    fun spanAddSubSpan(spanId: String, config: SpanConfig) {
        Span.addSpan(writer, spanId, config)
    }

    fun spanAddTag(spanId: String, key: String, value: String) {
        Span.addTag(writer, spanId, key, value)
    }

    fun spanAddEvent(spanId: String, event: String, tags: Tags? = null) {
        Span.event(writer, spanId, event, tags)
    }

    fun spanEnd(spanId: String, data: Any? = null) {
        Span.end(writer, spanId, data)
    }

    // Retrieval methods
    fun retrievalEnd(retrievalId: String, data: Any? = null) {
        Retrieval.end(writer, retrievalId, data)
    }

    fun retrievalSetInput(retrievalId: String, input: String) {
        Retrieval.setInput(writer, retrievalId, input)
    }

    fun retrievalSetOutput(retrievalId: String, output: List<String>) {
        Retrieval.setOutput(writer, retrievalId, output)
    }

    fun retrievalSetOutput(retrievalId: String, output: String) {
        Retrieval.setOutput(writer, retrievalId, output)
    }
}