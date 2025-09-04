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

@kotlinx.serialization.ExperimentalSerializationApi
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
        runBlocking { writer.cleanup() }
        return CompletableFuture.completedFuture(null)
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

    fun sessionAddMetadata(sessionId: String, key: String, value: Any) {
        Session.addMetadata(writer, sessionId, key, value)
    }

    fun sessionAddTrace(sessionId: String, config: TraceConfig): Trace {
        return Session.addTrace(writer, sessionId, config)
    }

    // Trace methods
    fun traceAddGeneration(traceId: String, config: GenerationConfig): Generation {
        return Trace.addGeneration(writer, traceId, config)
    }

    fun traceAddRetrieval(traceId: String, config: RetrievalConfig): Retrieval {
        return Trace.addRetrieval(writer, traceId, config)
    }

    fun traceAddAttachment(traceId: String, attachment: Attachment) {
        return Trace.addAttachment(writer, traceId, attachment)
    }

    fun traceAddToolCall(traceId: String, config: ToolCallConfig): ToolCall {
        return Trace.addToolCall(writer, traceId, config)
    }

    fun traceAddError(traceId: String, config: ErrorConfig): Error {
        return Trace.addError(writer, traceId, config)
    }

    fun traceSetOutput(traceId: String, output: String) {
        Trace.setOutput(writer, traceId, output)
    }

    fun traceSetInput(traceId: String, input: String) {
        Trace.setInput(writer, traceId, input)
    }

    fun traceAddSpan(traceId: String, config: SpanConfig): Span {
        return Trace.addSpan(writer, traceId, config)
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

    fun generateAddAttachment(generationId: String, attachment: Attachment) {
        Generation.addAttachment(writer, generationId, attachment)
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
    fun spanAddGeneration(spanId: String, config: GenerationConfig): Generation {
        return Span.addGeneration(writer, spanId, config)
    }

    fun spanRetrieval(spanId: String, config: RetrievalConfig): Retrieval {
        return Span.addRetrieval(writer, spanId, config)
    }

    fun spanAddSubSpan(spanId: String, config: SpanConfig): Span {
        return Span.addSpan(writer, spanId, config)
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

    fun retrievalSetAttachment(retrievalId: String, attachment: Attachment) {
        Retrieval.addAttachment(writer, retrievalId, attachment)
    }

    fun retrievalSetOutput(retrievalId: String, output: String) {
        Retrieval.setOutput(writer, retrievalId, output)
    }
}