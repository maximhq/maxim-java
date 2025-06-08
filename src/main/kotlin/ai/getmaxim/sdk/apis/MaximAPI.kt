package ai.getmaxim.sdk.apis

import ai.getmaxim.sdk.models.*
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.net.URI

class MaximAPI {
    companion object {
        private val logger = LoggerFactory.getLogger(MaximAPI::class.java)
        private suspend inline fun <reified T> call(
            url: String,
            method: String,
            apiKey: String,
            headers: Map<String, String>? = null,
            body: String? = null,
        ): T {
            return withContext(Dispatchers.IO) {
                val parsedUrl = URI.create(url).toURL()
                val isLocalhost = parsedUrl.host == "localhost"
                val connection = if (isLocalhost) {
                    (parsedUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 30000
                        readTimeout = 60000
                    }
                } else {
                    (parsedUrl.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                        connectTimeout = 30000
                        readTimeout = 60000
                    }
                }
                connection.apply {
                    requestMethod = method
                    setRequestProperty("x-maxim-api-key", apiKey)
                    headers?.forEach { (key, value) -> setRequestProperty(key, value) }
                    doInput = true
                    if (body != null) {
                        doOutput = true
                        outputStream.write(body.toByteArray())
                    }
                }
                try {
                    val responseCode = connection.responseCode
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    logger.debug("API $url - $responseCode")
                    if (responseCode in 200..299) {
                        MaximJson.decodeFromString<T>(responseBody)
                    } else {
                        throw Exception("HTTP Error: $responseCode")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }

        suspend fun getPrompt(baseUrl: String, apiKey: String, id: String): PromptVersionsAndRules {
            val requestUrl = "$baseUrl/api/sdk/v4/prompts?promptId=$id"
            val response: MaximApiPromptResponse = call(requestUrl, "GET", apiKey)
            return response.data
        }

        suspend fun getPrompts(
            baseUrl: String,
            apiKey: String,
        ): List<PromptWithVersionsAndRules> {
            val response: MaximApiPromptsResponse = call("$baseUrl/api/sdk/v4/prompts", "GET", apiKey)
            return response.data
        }

        suspend fun getPromptChain(baseUrl: String, apiKey: String, id: String): PromptChainWithVersionsAndRules {
            val requestUrl = "$baseUrl/api/sdk/v4/prompt-chains?promptChainId=$id"
            val response: MaximApiPromptChainResponse = call(requestUrl, "GET", apiKey)
            return response.data
        }

        suspend fun getPromptChains(
            baseUrl: String,
            apiKey: String,
        ): List<PromptChainWithVersionAndRulesAndId> {
            val response: MaximApiPromptChainsResponse = call("$baseUrl/api/sdk/v4/prompt-chains", "GET", apiKey)
            return response.data
        }

        suspend fun getFolder(baseUrl: String, apiKey: String, id: String): Folder? {
            val response: MaximFolderResponse = call("$baseUrl/api/sdk/v3/folders?folderId=$id", "GET", apiKey)
            if (response.error != null) {
                return null
            }
            return response.data
        }

        suspend fun getFolders(baseUrl: String, apiKey: String): List<Folder> {
            val response: MaximFoldersResponse = call("$baseUrl/api/sdk/v3/folders", "GET", apiKey)
            return response.data
        }

        suspend fun addDatasetEntries(
            baseUrl: String,
            apiKey: String,
            datasetId: String,
            datasetEntries: List<DatasetEntry>,
        ) {
            val body = MaximJson.encodeToString(AddDatasetEntriesPayload(datasetId, datasetEntries))
            val response: MaximAPIResponse = call(
                "$baseUrl/api/sdk/v3/datasets/entries",
                "POST",
                apiKey,
                mapOf("Content-Type" to "application/json"),
                body
            )
            if (response.error != null) throw Exception(response.error.message)
        }

        suspend fun doesLogRepositoryExist(baseUrl: String, apiKey: String, loggerId: String): Boolean {
            return try {
                call<MaximAPIResponse>("$baseUrl/api/sdk/v3/log-repositories?loggerId=$loggerId", "GET", apiKey)
                true
            } catch (e: Exception) {
                false
            }
        }

        suspend fun pushLogs(baseUrl: String, apiKey: String, repositoryId: String, logs: String) {
            val response: MaximAPIResponse = call(
                "$baseUrl/api/sdk/v3/log?id=$repositoryId",
                "POST",
                apiKey,
                mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                logs
            )
            if (response.error != null) throw Exception(response.error.message)
        }
    }
}