package ai.getmaxim.sdk

import ai.getmaxim.sdk.models.DatasetEntry
import ai.getmaxim.sdk.models.MessageContent
import ai.getmaxim.sdk.models.QueryBuilder
import ai.getmaxim.sdk.models.Variable
import ai.getmaxim.sdk.models.VariableType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertTrue

import org.junit.jupiter.api.assertThrows
import java.util.ArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalSerializationApi
class MaximTests {

    private lateinit var maxim: Maxim
    private lateinit var config: Map<String, Map<String, Any>>

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

        maxim = Maxim(Config(apiKey = apiKey, baseUrl = baseUrl, debug = true))
    }

    @Test
    fun `test getPrompt with deployment variables`() {
        val prompt = maxim.getPrompt(
            config["ai"]!!["promptId"]!! as String,
            QueryBuilder().and().deploymentVar("Environment", "prod").build()
        ).get()
        assertNotNull(prompt)
        assertEquals(config["ai"]!!["promptId"]!!, prompt.promptId)
        assertEquals(config["ai"]!!["promptVersionId"]!!, prompt.versionId)
        assertEquals("You are a helpful assistant", (prompt.messages[0].content as MessageContent.StringContent).value)
        assertEquals(2, prompt.messages.size)
    }

    @Test
    fun `test getPrompt with deployment variables Environment prod`() {
        val prompt = maxim.getPrompt(
            config["ai"]!!["promptId"]!! as String,
            QueryBuilder().and().deploymentVar("Environment", "prod").build()
        ).get()
        assertNotNull(prompt)
        assertEquals(config["ai"]!!["promptId"]!!, prompt.promptId)
        assertEquals(config["ai"]!!["prodPromptVersionId"]!!, prompt.versionId)
        assertEquals("You are a helpful assistant", (prompt.messages[0].content as MessageContent.StringContent).value)
        assertEquals(2, prompt.messages.size)
    }

    @Test
    fun `test getPrompt with deployment variables Environment prod and TenantId 123`() {
        val prompt = maxim.getPrompt(
            config["ai"]!!["promptId"]!! as String,
            QueryBuilder().and().deploymentVar("Environment", "prod").deploymentVar("TenantId", 123).build()
        ).get()
        assertNotNull(prompt)
        assertEquals(config["ai"]!!["promptId"]!!, prompt.promptId)
        assertEquals(config["ai"]!!["prodAndT123PromptVersionId"]!!, prompt.versionId)
        assertEquals(1, prompt.messages.size)
    }

    @Test
    fun `test fetch all prompts deployed on prod with tag filters`() {
        val prompts = maxim.getPrompts(
            QueryBuilder().and().deploymentVar("Environment", "prod").tag("CustomerId", 1234, false).build()
        ).get()
        println("prompts: ${prompts.map { it.versionId }}")
        prompts.forEach { prompt ->
            assertNotNull(prompt)
            assertTrue(
                (config["ai"]!!["prodPromptsWithOptionalCustomerId1234"]!! as ArrayList<*>).contains(
                    prompt.versionId
                )
            )
        }
    }

    @Test
    fun `test fetch if fallback works fine`() {
        val prompt = maxim.getPrompt(
            config["ai"]!!["promptId"]!! as String,
            QueryBuilder().and().deploymentVar("Environment", "prod").tag("TenantId", 1234, false).build()
        ).get()
        assertEquals(prompt.promptId, config["ai"]!!["promptId"]!! as String)
        assertEquals(prompt.versionId, config["ai"]!!["prodPromptVersionId"]!! as String)
    }

    @Test
    fun `test fetch if forceful fallback works fine`() {
        val prompt = maxim.getPrompt(
            config["ai"]!!["promptId"]!! as String,
            QueryBuilder().and().deploymentVar("Environment", "prod").tag("TenantId", 123, true).build()
        ).get()
        assertEquals(prompt.promptId, config["ai"]!!["promptId"]!! as String)
        assertEquals(prompt.versionId, config["ai"]!!["prodAndT123PromptVersionId"]!! as String)
    }


    @Test
    fun `test getFolderUsingId`() {
        val folder = maxim.getFolderById(config["ai"]!!["testFolderId"]!! as String).get()
        assertNotNull(folder)
        assertEquals("Test Folder", folder.name)
    }

    @Test
    fun `test getFolderUsingTags`() {
        val folders = maxim.getFolders(QueryBuilder().and().tag("test", true).build()).get()
        assertEquals("Test Folder", folders[0].name)
        assertEquals(1, folders.size)
    }

    @Test
    fun `test getPromptsFromAFolder`() {
        val prompts = maxim.getPrompts(
            QueryBuilder().and()
                .folder(config["ai"]!!["testFolderId"]!! as String)
                .deploymentVar("Environment", "stage")
                .deploymentVar("TenantId", 123)
                .build()
        ).get()
        assertEquals(1, prompts.size)
        assertEquals(config["ai"]!!["testFolderEnvStageTenant123PromptVersion"]!!, prompts[0].versionId)
    }

    @Test
    fun `add dataset entries`() {
        // Assuming maxim is an instance of your Maxim class
        maxim.addDatasetEntries(
            "clo7as7v2001axvt02rbx70qg", listOf(
                DatasetEntry(
                    input = Variable(
                        type = VariableType.TEXT,
                        payload = "Hello"
                    )
                )
            )
        ).get()
    }

    @Test
    fun `add dataset entries should throw error on invalid dataset id`() {
        val exception = assertThrows<Exception> {
            maxim.addDatasetEntries(
                "clo7as7v2001axvt02rbx70", listOf(
                    DatasetEntry(
                        input = Variable(
                            type = VariableType.TEXT,
                            payload = "Hello"
                        )
                    )
                )
            )
        }
        assertEquals("Dataset not found", exception.message)
    }

    @AfterEach
    fun tearDown() {
        maxim.cleanup()
    }
}