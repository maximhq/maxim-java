package ai.getmaxim.sdk

import ai.getmaxim.sdk.apis.MaximAPI
import ai.getmaxim.sdk.cache.MaximCache
import ai.getmaxim.sdk.cache.MaximInMemoryCache
import ai.getmaxim.sdk.logger.Logger
import ai.getmaxim.sdk.logger.LoggerConfig
import ai.getmaxim.sdk.models.*
import ai.getmaxim.sdk.models.RuleType
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.scheduleAtFixedRate

enum class EntityType {
    PROMPT, PROMPT_CHAIN, FOLDER
}

data class Config(
    val baseUrl: String? = null,
    val apiKey: String,
    val cache: MaximCache? = null,
    val debug: Boolean = false
)

@ExperimentalSerializationApi
class Maxim(private val config: Config) {
    private val logger = LoggerFactory.getLogger(Maxim::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val baseUrl: String = config.baseUrl ?: "https://app.getmaxim.ai"
    private val apiKey: String = config.apiKey
    private val isDebug: Boolean = config.debug
    private val cache: MaximCache = config.cache ?: MaximInMemoryCache()
    private var sync: Deferred<Unit>? = null
    private val loggers: MutableMap<String, Logger> = mutableMapOf()
    private var timer: Timer

    init {
        timer = Timer()
        timer.scheduleAtFixedRate(0, 60000) {
            scope.launch {
                sync?.await() // Wait for previous sync to complete
                sync = async { syncEntities() }
            }
        }
    }

    private suspend fun syncEntities() {
        logger.debug("Syncing prompts, chains and folders")
        try {
            coroutineScope {
                launch { syncPrompts() }
                launch { syncFolders() }
                launch { syncPromptChains() }
            }
        } catch (e: Exception) {
            logger.warn("Error while syncing: ${e.message}")
        }
    }

    private suspend fun syncPrompts() {
        val prompts = MaximAPI.getPrompts(baseUrl, apiKey)
        logger.debug("Syncing ${prompts.size} prompts")
        prompts.forEach { prompt ->
            try {
                cache.set(getCacheKey(EntityType.PROMPT, prompt.promptId), MaximJson.encodeToString(prompt))
            } catch (e: Exception) {
                logger.error("Error while caching prompts: ${e.message}")
            }
        }
    }

    private suspend fun syncPromptChains() {
        val promptChains = MaximAPI.getPromptChains(baseUrl, apiKey)
        logger.debug("Syncing ${promptChains.size} prompt chains")
        promptChains.forEach { promptChain ->
            try {
                cache.set(
                    getCacheKey(EntityType.PROMPT_CHAIN, promptChain.promptChainId),
                    MaximJson.encodeToString(promptChain)
                )
            } catch (e: Exception) {
                logger.error("Error while syncing ${promptChain.promptChainId} ${e.message}")
            }
        }
    }

    private suspend fun syncFolders() {
        val folders = MaximAPI.getFolders(baseUrl, apiKey)
        logger.debug("Syncing ${folders.size} folders")
        folders.forEach { folder ->
            try {
                cache.set(getCacheKey(EntityType.FOLDER, folder.id), MaximJson.encodeToString(folder))
            } catch (e: Exception) {
                logger.error("Error while syncing cache ${e.message}")
            }
        }
    }

    private suspend fun getPromptFromCache(key: String): PromptVersionsAndRules? {
        val data = cache.get(key) ?: return null
        return MaximJson.decodeFromString<PromptVersionsAndRules>(data)
    }

    private suspend fun getAllPromptsFromCache(): List<PromptVersionsAndRules> {
        val keys = cache.getAllKeys()
        return keys.filter { it.startsWith("prompt:") }
            .mapNotNull { cache.get(it) }
            .map { MaximJson.decodeFromString<PromptVersionsAndRules>(it) }
    }

    private suspend fun getPromptChainFromCache(key: String): PromptChainWithVersionsAndRules? {
        val data = cache.get(key) ?: return null
        return MaximJson.decodeFromString<PromptChainWithVersionsAndRules>(data)
    }

    private suspend fun getAllPromptChainsFromCache(): List<PromptChainWithVersionsAndRules> {
        val keys = cache.getAllKeys()
        return keys.filter { it.startsWith("promptChain:") }
            .mapNotNull { cache.get(it) }
            .map { MaximJson.decodeFromString<PromptChainWithVersionsAndRules>(it) }
    }

    private suspend fun getFolderFromCache(key: String): Folder? {
        val data = cache.get(key) ?: return null
        return MaximJson.decodeFromString<Folder>(data)
    }

    private suspend fun getAllFoldersFromCache(): List<Folder> {
        val keys = cache.getAllKeys()
        return keys.filter { it.startsWith("folder:") }
            .mapNotNull { cache.get(it) }
            .map { MaximJson.decodeFromString<Folder>(it) }
    }

    private fun getCacheKey(entity: EntityType, id: String): String {
        return when (entity) {
            EntityType.PROMPT -> "prompt:$id"
            EntityType.PROMPT_CHAIN -> "promptChain:$id"
            EntityType.FOLDER -> "folder:$id"
        }
    }

    private fun getPromptVersionForRule(promptVersionAndRules: PromptVersionsAndRules, rule: QueryRule?): Prompt? {
        if (rule != null) {
            val incomingQuery = IncomingQuery(
                query = rule.query,
                operator = rule.operator.toString(),
                exactMatch = rule.exactMatch
            )
            val objects = mutableListOf<QueryObject>()

            promptVersionAndRules.rules.forEach { (versionId, versionRules) ->
                versionRules.forEach { versionRule ->
                    if (versionRule.rules.query != null) {
                        rule.scopes.keys.forEach { key ->
                            when (key) {
                                "folder" -> {}
                                else -> throw IllegalArgumentException("Invalid scope added")
                            }
                        }

                        val version = promptVersionAndRules.versions.find { it.id == versionId }
                        val query: RuleGroupType = versionRule.rules.query
                        version?.let { v ->
                            v.config.tags?.let { tags ->
                                val parsedIncomingQuery = parseIncomingQuery(incomingQuery.query)
                                tags.filter { (key, _) -> parsedIncomingQuery.any { it.field == key } }
                                    .forEach { (key, value) ->
                                        query.rules.add(
                                            RuleType(field = key, operator = "=", value = value)

                                        )
                                    }
                            }
                            objects.add(QueryObject(id = versionId, query = query))
                        }
                    }
                }
            }

            val deployedVersionObject = findBestMatch(objects, incomingQuery)
            return deployedVersionObject?.let { obj ->
                promptVersionAndRules.versions.find { it.id == obj.id }?.let { deployedVersion ->
                    Prompt(
                        promptId = deployedVersion.promptId,
                        versionId = deployedVersion.id,
                        version = deployedVersion.version,
                        messages = deployedVersion.config.messages,
                        modelParameters = deployedVersion.config.modelParameters,
                        model = deployedVersion.config.model,
                        tags = deployedVersion.config.tags
                    )
                }
            }
        } else {
            for ((versionId, versionRules) in promptVersionAndRules.rules) {
                if (versionRules.any { it.rules.query == null || it.rules.query.rules.isEmpty() }) {
                    return promptVersionAndRules.versions.find { it.id == versionId }?.let { deployedVersion ->
                        Prompt(
                            promptId = deployedVersion.promptId,
                            versionId = deployedVersion.id,
                            version = deployedVersion.version,
                            messages = deployedVersion.config.messages,
                            modelParameters = deployedVersion.config.modelParameters,
                            model = deployedVersion.config.model,
                            tags = deployedVersion.config.tags
                        )
                    }
                }
            }
        }

        return promptVersionAndRules.fallbackVersion?.let { fallback ->
            Prompt(
                promptId = fallback.promptId,
                versionId = fallback.id,
                version = fallback.version,
                messages = fallback.config.messages,
                modelParameters = fallback.config.modelParameters,
                model = fallback.config.model,
                tags = fallback.config.tags
            )
        }
    }

    private fun getPromptChainVersionForRule(
        promptChainVersionAndRules: PromptChainWithVersionsAndRules,
        rule: QueryRule?
    ): PromptChain? {
        if (rule != null) {
            val incomingQuery = IncomingQuery(
                query = rule.query,
                operator = rule.operator.toString(),
                exactMatch = rule.exactMatch
            )
            val objects = mutableListOf<QueryObject>()

            promptChainVersionAndRules.rules.forEach { (versionId, versionRules) ->
                versionRules.forEach { versionRule ->
                    if (versionRule.rules.query != null) {
                        rule.scopes.keys.forEach { key ->
                            when (key) {
                                "folder" -> {}
                                else -> throw IllegalArgumentException("Invalid scope added")
                            }
                        }

                        val version = promptChainVersionAndRules.versions.find { it.id == versionId }
                        val query: RuleGroupType = versionRule.rules.query
                        version?.let {
                            objects.add(QueryObject(id = versionId, query = query))
                        }
                    }
                }
            }

            val deployedVersionObject = findBestMatch(objects, incomingQuery)
            return deployedVersionObject?.let { obj ->
                promptChainVersionAndRules.versions.find { it.id == obj.id }?.let { deployedVersion ->
                    PromptChain(
                        promptChainId = deployedVersion.promptChainId,
                        versionId = deployedVersion.id,
                        version = deployedVersion.version,
                        nodes = deployedVersion.config?.nodes
                    )
                }
            }
        } else {
            for ((versionId, versionRules) in promptChainVersionAndRules.rules) {
                if (versionRules.any { it.rules.query == null || it.rules.query.rules.isEmpty() }) {
                    return promptChainVersionAndRules.versions.find { it.id == versionId }?.let { deployedVersion ->
                        PromptChain(
                            promptChainId = deployedVersion.promptChainId,
                            versionId = deployedVersion.id,
                            version = deployedVersion.version,
                            nodes = deployedVersion.config?.nodes
                        )
                    }
                }
            }
        }

        return promptChainVersionAndRules.fallbackVersion?.let { fallback ->
            PromptChain(
                promptChainId = fallback.promptChainId,
                versionId = fallback.id,
                version = fallback.version,
                nodes = fallback.config?.nodes ?: emptyList()
            )
        }
    }

    private fun getFoldersForRule(folders: List<Folder>, rule: QueryRule): List<Folder> {
        val incomingQuery = IncomingQuery(
            query = rule.query,
            operator = rule.operator.toString(),
            exactMatch = rule.exactMatch
        )
        val objects = mutableListOf<QueryObject>()

        folders.forEach { folder ->
            val query = RuleGroupType(combinator = "AND", not = false, rules = mutableListOf())
            folder.tags.let { tags ->
                val parsedIncomingQuery = parseIncomingQuery(incomingQuery.query)
                tags.filter { (key, _) -> parsedIncomingQuery.any { it.field == key } }
                    .forEach { (key, value) ->
                        query.rules.add(
                            RuleType(key, value, "=")
                        )
                    }
                if (query.rules.isNotEmpty()) {
                    objects.add(QueryObject(id = folder.id, query = query))
                }
            }
        }

        val folderObjects = findAllMatches(objects, incomingQuery)
        val ids = folderObjects.map { it.id }
        return folders.filter { it.id in ids }
    }

    fun getPrompt(id: String, rule: QueryRule): CompletableFuture<Prompt> {
        return CompletableFuture.supplyAsync {
            runBlocking { sync?.await() }

            val key = getCacheKey(EntityType.PROMPT, id)
            var versionAndRules = runBlocking { getPromptFromCache(key) }
            println("version and rules ${MaximJson.encodeToString(versionAndRules)}")
            if (versionAndRules == null) {
                versionAndRules = runBlocking { MaximAPI.getPrompt(baseUrl, apiKey, id) }
                if (versionAndRules.versions.isEmpty()) {
                    throw Exception("No active deployments found for Prompt $id")
                }
                runBlocking { cache.set(key, MaximJson.encodeToString(versionAndRules)) }
            }
            getPromptVersionForRule(versionAndRules, rule)
                ?: throw Exception("No active deployments found for Prompt $id")
        }
    }

    fun getPrompts(rule: QueryRule): CompletableFuture<List<Prompt>> {
        return CompletableFuture.supplyAsync {
            runBlocking { sync?.await() }
            var versionAndRules = runBlocking { getAllPromptsFromCache() }
            if (versionAndRules.isEmpty()) {
                runBlocking { syncEntities() }
                versionAndRules = runBlocking { getAllPromptsFromCache() }
            }
            if (versionAndRules.isEmpty()) {
                throw Exception("No active deployments found for any prompt")
            }
            val prompts = versionAndRules
                .filter { v ->
                    if (rule.scopes.isEmpty()) true
                    else rule.scopes["folder"]?.let { v.folderId == it } != false
                }
                .mapNotNull { v -> getPromptVersionForRule(v, rule) }
            if (prompts.isEmpty()) {
                throw Exception("No active deployments found for any prompt")
            }
            prompts
        }
    }

    fun getPromptChain(id: String, rule: QueryRule): CompletableFuture<PromptChain> {
        return CompletableFuture.supplyAsync {
            runBlocking { sync?.await() }
            val key = getCacheKey(EntityType.PROMPT_CHAIN, id)
            var versionAndRules = runBlocking { getPromptChainFromCache(key) }
            if (versionAndRules == null) {
                versionAndRules = runBlocking { MaximAPI.getPromptChain(baseUrl, apiKey, id) }
                if (versionAndRules.versions.isEmpty()) {
                    throw Exception("No active deployments found for Prompt Chain $id")
                }
                runBlocking { cache.set(key, MaximJson.encodeToString(versionAndRules)) }
            }
            getPromptChainVersionForRule(versionAndRules, rule)
                ?: throw Exception("No active deployments found for Prompt Chain $id")
        }
    }

    fun getPromptChains(rule: QueryRule): CompletableFuture<List<PromptChain>> {
        return CompletableFuture.supplyAsync {
            runBlocking { sync?.await() }
            var versionAndRules = runBlocking { getAllPromptChainsFromCache() }
            if (versionAndRules.isEmpty()) {
                runBlocking { syncEntities() }
                versionAndRules = runBlocking { getAllPromptChainsFromCache() }
            }
            if (versionAndRules.isEmpty()) {
                throw Exception("No active deployments found for any prompt chain")
            }
            val promptChains = versionAndRules
                .filter { v ->
                    if (rule.scopes.isEmpty()) true
                    else rule.scopes["folder"]?.let { v.folderId == it } != false
                }
                .mapNotNull { v -> getPromptChainVersionForRule(v, rule) }
            if (promptChains.isEmpty()) {
                throw Exception("No active deployments found for any prompt chain")
            }
            promptChains
        }
    }

    fun getFolderById(id: String): CompletableFuture<Folder> {
        return CompletableFuture.supplyAsync {
            runBlocking { sync?.await() }
            val key = getCacheKey(EntityType.FOLDER, id)
            var folder = runBlocking { getFolderFromCache(key) }
            if (folder == null) {
                folder = runBlocking { MaximAPI.getFolder(baseUrl, apiKey, id) }
                if (folder == null) {
                    throw Exception("No folder found with id $id")
                }
                runBlocking { cache.set(key, MaximJson.encodeToString(folder)) }
            }
            folder
        }
    }

    fun getFolders(rule: QueryRule): CompletableFuture<List<Folder>> {
        return CompletableFuture.supplyAsync {
            runBlocking { sync?.await() }
            var folders = runBlocking { getAllFoldersFromCache() }
            if (folders.isEmpty()) {
                runBlocking { syncEntities() }
                folders = runBlocking { getAllFoldersFromCache() }
            }
            if (folders.isEmpty()) {
                throw Exception("No folders found")
            }
            getFoldersForRule(folders, rule)
        }
    }

    fun addDatasetEntries(datasetId: String, entries: List<DatasetEntry>): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            runBlocking { MaximAPI.addDatasetEntries(baseUrl, apiKey, datasetId, entries) }
        }
    }

    fun logger(config: LoggerConfig): CompletableFuture<Logger> {
        return CompletableFuture.supplyAsync {
            runBlocking { sync?.await() }
            val exists = runBlocking { MaximAPI.doesLogRepositoryExist(baseUrl, apiKey, config.id) }
            if (!exists && config.id.isNotEmpty()) {
                throw Exception("Log repository not found.")
            }
            loggers.getOrPut(config.id) {
                Logger(
                    config = config,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    isDebug = isDebug
                )
            }
        }
    }

    fun cleanup(): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync {
            timer.cancel()
            loggers.values.forEach { runBlocking { it.cleanup() } }
            null
        }
    }
}
