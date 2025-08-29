package ai.getmaxim.sdk.apis

import ai.getmaxim.sdk.models.*
import org.slf4j.LoggerFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class MaximAPI {
    companion object {
        private val logger = LoggerFactory.getLogger(MaximAPI::class.java)
        private val client = HttpClient(CIO) { // or OkHttp, etc.
            install(HttpTimeout)
        }

        private suspend inline fun <reified T> call(
            url: String,
            method: String,
            apiKey: String,
            headers: Map<String, String>? = null,
            payload: String? = null,
        ): T {
            return MaximJson.decodeFromString(client.request(url) {
                this.method = HttpMethod.parse(method)
                header("x-maxim-api-key", apiKey)
                headers?.forEach { (key, value) ->
                    header(key, value)
                }
                // Only set body for non-GET requests
                if (method.uppercase() != "GET" && payload != null) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }

                timeout {
                    connectTimeoutMillis = 30000
                    requestTimeoutMillis = 60000
                }
            }.bodyAsText())
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
                val resp =
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

        suspend fun getUploadUrl(
            baseUrl: String,
            apiKey: String,
            key: String,
            mimeType: String,
            size: Int
        ): SignedUrlResponse {
            val response: MaximAPIResponse = call(
                "$baseUrl/api/sdk/v1/log-repositories/attachments/upload-url?key=$key&mimeType=$mimeType&size=$size",
                "GET",
                apiKey,
                mapOf("Accept" to "application/json"),
            )
            if (response.error != null) throw Exception(response.error.message)
            return response.data as SignedUrlResponse
        }

        suspend fun uploadToSignedUrl(url: String, data: ByteArray, mimeType: String) {
            val response = client.put(url) {
                setBody(data)
                header(HttpHeaders.ContentType, mimeType)
                header(HttpHeaders.ContentLength, data.size.toString())
            }
            if (response.status != HttpStatusCode.OK) throw Exception(response.bodyAsText())
        }
    }
}