package ai.getmaxim.sdk

import ai.getmaxim.sdk.logger.LoggerConfig
import ai.getmaxim.sdk.logger.components.CompletionRequest
import ai.getmaxim.sdk.logger.components.GenerationConfig
import ai.getmaxim.sdk.logger.components.RetrievalConfig
import ai.getmaxim.sdk.logger.components.SessionConfig
import ai.getmaxim.sdk.logger.components.SpanConfig
import ai.getmaxim.sdk.logger.components.TextCompletionChoice
import ai.getmaxim.sdk.logger.components.TextCompletionResult
import ai.getmaxim.sdk.logger.components.TraceConfig
import ai.getmaxim.sdk.logger.components.Usage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalSerializationApi
class MaximLoggerTests {
    @ExperimentalSerializationApi
    private lateinit var maxim: Maxim
    private lateinit var config: Map<String, Map<String, Any>>
    private lateinit var repoId: String

    private fun readTestConfig(): String {
        return object {}.javaClass.getResource("/testConfig.json")?.readText()
            ?: throw IllegalArgumentException("File not found")
    }

    private fun parseJsonContent(content: String): Map<String, Map<String, Any>> {
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
        return gson.fromJson(content, mapType)
    }

    @BeforeEach
    fun setUp() {
        config = parseJsonContent(readTestConfig())
        val env = "ai"
        val apiKey = config[env]!!["apiKey"]!! as String
        val baseUrl = config[env]!!["baseUrl"]!! as String
        repoId = config[env]!!["repoId"]!! as String
        maxim = Maxim(Config(apiKey = apiKey, baseUrl = baseUrl, debug = true))
    }

    @Test
    fun testShouldBeAbleToCreateATraceAndUpdate() {
        println("user repoid as $repoId")
        val config = LoggerConfig(id = repoId)
        val logger = maxim.logger(config).get()
        val traceId = UUID.randomUUID().toString()
        val traceConfig = TraceConfig(id = traceId)
        val trace = logger.trace(traceConfig)
        trace.setOutput("test output")
        assertNotNull(trace)
        assertEquals(traceId, trace.id)
        trace.end()
        logger.cleanup().get()
        maxim.cleanup()
    }

    @Test
    fun testSessionChanges() {
        val config = LoggerConfig(id = repoId)
        val logger = maxim.logger(config).get()
        val sessionId = UUID.randomUUID().toString()
        val session = logger.session(SessionConfig(id = sessionId, name = "test session"))
        val traceId = UUID.randomUUID().toString()
        val trace = logger.sessionAddTrace(session.id, TraceConfig(id = traceId))
        Thread.sleep(2000)
        trace.end()
        logger.traceSetInput(trace.id, "Java test input")
        logger.traceSetOutput(trace.id, "Java test output")
        logger.traceAddTag(trace.id, "test", "yes")
        logger.traceAddEvent(trace.id, "test event")
        Thread.sleep(40000)
        session.addTag("test", "test tag should appear")
        session.end()
    }

    @Test
    fun testUnendedSession() {
        val config = LoggerConfig(id = repoId)
        val logger = maxim.logger(config).get()
        val sessionId = UUID.randomUUID().toString()
        val session = logger.session(SessionConfig(id = sessionId, name = "test session"))
        Thread.sleep(100000)
        session.addTag("test", "test tag should appear")
        Thread.sleep(100000)
    }

    @Test
    fun testAddingGeneration(){
        val config = LoggerConfig(id = repoId)
        val logger = maxim.logger(config).get()
        val traceId = UUID.randomUUID().toString()
        val trace = logger.trace(TraceConfig(id = traceId))
        val generationId = UUID.randomUUID().toString()
        logger.traceAddGeneration(
            trace.id, GenerationConfig(
                id = generationId,
                name = "gen1",
                provider = "openai",
                model = "gpt-3.5-turbo-16k",
                modelParameters = mapOf("temperature" to 3),
                messages = listOf(CompletionRequest(role = "user", content = "Hello, how can I help you today ttttt?"))
            )
        )
        Thread.sleep(30000)
        logger.generationSetResult(
            generationId = generationId,
            result = TextCompletionResult(
                id = UUID.randomUUID().toString(),
                `object` = "text_completion",
                created = Instant.now().epochSecond,
                model = "gpt-35-turbo",
                choices = listOf(
                    TextCompletionChoice(
                        index = 0,
                        text = """{"title": "Sending a Greeting in PowerShell", "answer": "To send a greeting in PowerShell, you can create a cmdlet that accepts a name parameter and writes out a greeting to the user. Here's an example of how you can do it:\n\n```powershell\nusing System.Management.Automation;\n\nnamespace SendGreeting\n{\n    [Cmdlet(VerbsCommunications.Send, \"Greeting\")]\n    public class SendGreetingCommand : Cmdlet\n    {\n        [Parameter(Mandatory = true)]\n        public string Name { get; set; }\n\n        protected override void ProcessRecord()\n        {\n            WriteObject(\"Hello \" + Name + \"!\");\n        }\n    }\n}\n```\n\nYou can then use this cmdlet by calling `Send-Greeting -Name suresh` to send a greeting with the name 'suresh'. The cmdlet will write out 'Hello suresh!' as the output.", "source_uuids_scores": [{"uuid": "c3491cef-0485-3a09-b0cd-41fdf78b160c", "score": 1}] }""",
                        finish_reason = "stop"
                    )
                ),
                usage = Usage(completion_tokens = 247, prompt_tokens = 1473, total_tokens = 1729)
            )
        )
        trace.end()
        logger.cleanup()
    }

    @Test
    fun testAddingLogsOutOfOrder() {
        val config = LoggerConfig(id = repoId)
        val logger = maxim.logger(config).get()
        val sessionId = UUID.randomUUID().toString()
        val session = logger.session(SessionConfig(id = sessionId))
        val traceId = UUID.randomUUID().toString()
        val trace = logger.sessionAddTrace(session.id, TraceConfig(id = traceId))
        Thread.sleep(2000)
        trace.end()
        logger.traceAddTag(trace.id, "test", "yes")
        logger.traceAddEvent(trace.id, "test event1")
        Thread.sleep(40000)
        val generationId = UUID.randomUUID().toString()
        logger.traceAddGeneration(
            trace.id, GenerationConfig(
                id = generationId,
                name = "gen1",
                provider = "openai",
                model = "gpt-3.5-turbo-16k",
                modelParameters = mapOf("temperature" to 3),
                messages = listOf(CompletionRequest(role = "user", content = "Hello, how can I help you today ttttt?"))
            )
        )
        Thread.sleep(30000)
        logger.generationSetResult(
            generationId = generationId,
            result = TextCompletionResult(
                id = UUID.randomUUID().toString(),
                `object` = "text_completion",
                created = Instant.now().epochSecond,
                model = "gpt-35-turbo",
                choices = listOf(
                    TextCompletionChoice(
                        index = 0,
                        text = """{"title": "Sending a Greeting in PowerShell", "answer": "To send a greeting in PowerShell, you can create a cmdlet that accepts a name parameter and writes out a greeting to the user. Here's an example of how you can do it:\n\n```powershell\nusing System.Management.Automation;\n\nnamespace SendGreeting\n{\n    [Cmdlet(VerbsCommunications.Send, \"Greeting\")]\n    public class SendGreetingCommand : Cmdlet\n    {\n        [Parameter(Mandatory = true)]\n        public string Name { get; set; }\n\n        protected override void ProcessRecord()\n        {\n            WriteObject(\"Hello \" + Name + \"!\");\n        }\n    }\n}\n```\n\nYou can then use this cmdlet by calling `Send-Greeting -Name suresh` to send a greeting with the name 'suresh'. The cmdlet will write out 'Hello suresh!' as the output.", "source_uuids_scores": [{"uuid": "c3491cef-0485-3a09-b0cd-41fdf78b160c", "score": 1}] }""",
                        finish_reason = "stop"
                    )
                ),
                usage = Usage(completion_tokens = 247, prompt_tokens = 1473, total_tokens = 1729)
            )
        )
        Thread.sleep(20000)
        val span1Id = UUID.randomUUID().toString()
        logger.traceAddSpan(trace.id, SpanConfig(id = span1Id, name = "Test Span"))
        val generation2Id = UUID.randomUUID().toString()
        logger.spanAddGeneration(
            span1Id, GenerationConfig(
                id = generation2Id,
                name = "gen2",
                provider = "openai",
                model = "gpt-3.5-turbo-16k",
                modelParameters = mapOf("temperature" to 3),
                messages = listOf(CompletionRequest(role = "user", content = "Hello, how can I help you today?"))
            )
        )
        Thread.sleep(4000)
        logger.generationSetResult(
            generation2Id, result = TextCompletionResult(
                id = UUID.randomUUID().toString(),
                `object` = "text_completion",
                created = Instant.now().epochSecond,
                model = "gpt-35-turbo",
                choices = listOf(
                    TextCompletionChoice(
                        index = 0,
                        text = """{"Intent": "General Talk"}""",
                        finish_reason = "stop"
                    )
                ),
                usage = Usage(completion_tokens = 7, prompt_tokens = 653, total_tokens = 660)
            )
        )
        Thread.sleep(10000)
        logger.spanAddTag(span1Id, "test", "test-span")
        logger.spanAddEvent(span1Id, "test-event")
        val retrievalId = UUID.randomUUID().toString()
        logger.spanRetrieval(span1Id, RetrievalConfig(id = retrievalId, name = "Test Retrieval"))
        logger.retrievalSetInput(retrievalId, "asdasdas")
        logger.retrievalSetOutput(retrievalId, listOf("doc 1","doc 2"))
        logger.retrievalEnd(retrievalId)
        Thread.sleep(2000)
        logger.spanEnd(span1Id)
    }
//
//    @Test
//    fun testShouldBeAbleToCreateASessionAndTraceUsingLogger() {
//        val config = LoggerConfig(id = repoId)
//        val logger = maxim.logger(config).get()
//        val sessionId = UUID.randomUUID().toString()
//        val session = logger.session(SessionConfig(id = sessionId))
//        val traceId = UUID.randomUUID().toString()
//        val trace = logger.sessionAddTrace(session.id, TraceConfig(id = traceId))
//        assertNotNull(trace)
//        assertEquals(traceId, trace.id)
//        logger.traceAddTag(trace.id, "test", "yes")
//        logger.traceAddEvent(trace.id, UUID.randomUUID().toString(), "test event")
//        val generationId = UUID.randomUUID().toString()
//        logger.traceAddGeneration(
//            trace.id, GenerationConfig(
//                id = generationId,
//                name = "gen1",
//                provider = "openai",
//                model = "gpt-3.5-turbo-16k",
//                modelParameters = mapOf("temperature" to 3),
//                messages = listOf(mapOf("role" to "user", "content" to "Hello, how can I help you today ttttt?"))
//            )
//        )
//        Thread.sleep(2000)
//        logger.generationSetResult(
//            generationId, mapOf(
//                "id" to "10145d10-b2d0-42f6-b69a-9a8311f312b6",
//                "object" to "text_completion",
//                "created" to 1720353381,
//                "model" to "gpt-35-turbo",
//                "choices" to listOf(
//                    mapOf(
//                        "index" to 0,
//                        "text" to """{"title": "Sending a Greeting in PowerShell", "answer": "To send a greeting in PowerShell, you can create a cmdlet that accepts a name parameter and writes out a greeting to the user. Here's an example of how you can do it:\n\n```powershell\nusing System.Management.Automation;\n\nnamespace SendGreeting\n{\n    [Cmdlet(VerbsCommunications.Send, \"Greeting\")]\n    public class SendGreetingCommand : Cmdlet\n    {\n        [Parameter(Mandatory = true)]\n        public string Name { get; set; }\n\n        protected override void ProcessRecord()\n        {\n            WriteObject(\"Hello \" + Name + \"!\");\n        }\n    }\n}\n```\n\nYou can then use this cmdlet by calling `Send-Greeting -Name suresh` to send a greeting with the name 'suresh'. The cmdlet will write out 'Hello suresh!' as the output.", "source_uuids_scores": [{"uuid": "c3491cef-0485-3a09-b0cd-41fdf78b160c", "score": 1}] }""",
//                        "finish_reason" to "stop"
//                    )
//                ),
//                "usage" to mapOf(
//                    "completion_tokens" to 247,
//                    "prompt_tokens" to 1473,
//                    "total_tokens" to 1720
//                )
//            )
//        )
//        val span1Id = UUID.randomUUID().toString()
//        logger.traceAddSpan(trace.id, SpanConfig(id = span1Id, name = "Test Span"))
//        val generation2Id = UUID.randomUUID().toString()
//        logger.spanAddGeneration(
//            span1Id, GenerationConfig(
//                id = generation2Id,
//                name = "gen2",
//                provider = "openai",
//                model = "gpt-4o",
//                modelParameters = mapOf("temperature" to 3),
//                messages = listOf(mapOf("role" to "user", "content" to "Hello, how can I help you today?"))
//            )
//        )
//        Thread.sleep(1000)
//        logger.generationSetResult(
//            generation2Id, mapOf(
//                "id" to "c9395a2d-8fbf-4e96-8ae9-be4820348f46",
//                "object" to "text_completion",
//                "created" to 1720359641,
//                "model" to "gpt-4o",
//                "choices" to listOf(
//                    mapOf(
//                        "index" to 0,
//                        "text" to """\n1. **Consistency**: Ensure your API design is consistent within itself and with industry standards. This includes using uniform resource naming conventions, consistent data formats, and predictable error handling mechanisms.\n2. **Simplicity**: Design APIs to be as simple as possible, but no simpler. This means providing only necessary functionalities and avoiding over-complex structures that might confuse the users.\n3. **Documentation**: Provide clear, thorough, and accessible documentation. Good documentation is crucial for API usability and maintenance. It helps users understand how to effectively interact with your API and what they can expect in terms of responses.\n4. **Versioning**: Plan for future changes by using versioning of your API. This helps prevent breaking changes to the API and keeps it robust over time.\n5. **Security**: Implement robust security measures to protect your API and its data. This includes using authentication mechanisms like OAuth, ensuring data is encrypted in transit, and considering security implications in all aspects of API design.\n""",
//                        "logprobs" to null,
//                        "finish_reason" to "stop"
//                    )
//                ),
//                "usage" to mapOf(
//                    "prompt_tokens" to 100,
//                    "completion_tokens" to 0,
//                    "total_tokens" to 113
//                )
//            )
//        )
//        logger.spanAddTag(span1Id, "test", "test-span")
//        logger.spanAddEvent(span1Id, "test-event")
//        val retrievalId = UUID.randomUUID().toString()
//        logger.spanRetrieval(span1Id, RetrievalConfig(id = retrievalId, name = "Test Retrieval"))
//        logger.retrievalSetInput(retrievalId, "asdasdas")
//        logger.retrievalSetOutput(retrievalId, listOf())
//        logger.retrievalEnd(retrievalId)
//        Thread.sleep(2000)
//        val nestedSpan1Id = UUID.randomUUID().toString()
//        logger.spanAddSubSpan(span1Id, SpanConfig(id = nestedSpan1Id, name = "Nested Span 1"))
//        logger.spanAddEvent(nestedSpan1Id, "test-event22")
//        Thread.sleep(1000)
//        val nestedSpan2Id = UUID.randomUUID().toString()
//        logger.spanAddSubSpan(nestedSpan1Id, SpanConfig(id = nestedSpan2Id, name = "Nested Span 2"))
//        logger.spanAddEvent(nestedSpan2Id, "test-event 33")
//        Thread.sleep(4000)
//        logger.spanEnd(nestedSpan2Id)
//        logger.spanEnd(nestedSpan1Id)
//        logger.spanEnd(span1Id)
//        logger.traceAddEvent(trace.id, "test-event")
//        logger.traceEnd(trace.id)
//        logger.sessionEnd(sessionId = session.id)
//        logger.traceSetFeedback(trace.id, Feedback(score = 5, comment = "Great job!"))
//        println("cleaning up")
//        logger.cleanup()
//        println("cleaning up done")
//        assertEquals(1, 1)
//    }

    @AfterEach
    fun tearDown() {
        maxim.cleanup().get()
    }
}