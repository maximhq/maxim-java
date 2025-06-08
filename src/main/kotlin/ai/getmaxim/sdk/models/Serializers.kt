@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)

package ai.getmaxim.sdk.models

import ai.getmaxim.sdk.logger.components.ChatCompletionChoice
import ai.getmaxim.sdk.logger.components.ChatCompletionMessage
import ai.getmaxim.sdk.logger.components.ChatCompletionResult
import ai.getmaxim.sdk.logger.components.CompletionRequest
import ai.getmaxim.sdk.logger.components.TextCompletionChoice
import ai.getmaxim.sdk.logger.components.TextCompletionResult
import ai.getmaxim.sdk.logger.components.ToolCall
import ai.getmaxim.sdk.logger.components.ToolCallFunction
import ai.getmaxim.sdk.logger.components.Usage
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.time.Instant
import java.util.Date

object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeLong(value.time)
    }

    override fun deserialize(decoder: Decoder): Date {
        return Date(decoder.decodeLong())
    }
}

object RuleSerializer : JsonContentPolymorphicSerializer<Rule>(Rule::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "rules" in element.jsonObject -> RuleGroupType.serializer()
        else -> RuleType.serializer()
    }
}

object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder =
            encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonElement = serializeToJsonElement(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    private fun serializeToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }
                .mapValues { serializeToJsonElement(it.value) })

            is CompletionRequest -> JsonObject(
                content = mapOf(
                    "role" to JsonPrimitive(value.role),
                    "content" to serializeToJsonElement(value.content)
                )
            )

            is TextCompletionResult -> JsonObject(
                content = mapOf(
                    "id" to JsonPrimitive(value.id),
                    "object" to JsonPrimitive(value.`object`),
                    "created" to JsonPrimitive(value.created),
                    "model" to JsonPrimitive(value.model),
                    "choices" to serializeToJsonElement(value.choices),
                    "usage" to serializeToJsonElement(value.usage),
                    "error" to serializeToJsonElement(value.error),
                )
            )

            is ChatCompletionResult -> JsonObject(
                content = mapOf(
                    "id" to JsonPrimitive(value.id),
                    "object" to JsonPrimitive(value.`object`),
                    "created" to JsonPrimitive(value.created),
                    "model" to JsonPrimitive(value.model),
                    "choices" to serializeToJsonElement(value.choices),
                    "usage" to serializeToJsonElement(value.usage),
                    "error" to serializeToJsonElement(value.error),
                )
            )

            is TextCompletionChoice -> JsonObject(
                content = mapOf(
                    "index" to JsonPrimitive(value.index),
                    "text" to JsonPrimitive(value.text),
                    "logprobs" to serializeToJsonElement(value.logprobs),
                    "finish_reason" to JsonPrimitive(value.finish_reason)
                )
            )

            is ToolCall -> JsonObject(
                content = mapOf(
                    "id" to JsonPrimitive(value.id),
                    "type" to JsonPrimitive(value.type),
                    "function" to serializeToJsonElement(value.function),
                )
            )

            is ToolCallFunction -> JsonObject(
                content = mapOf(
                    "name" to JsonPrimitive(value.name),
                    "arguments" to JsonPrimitive(value.arguments)
                )
            )

            is ChatCompletionMessage -> JsonObject(
                content = mapOf(
                    "role" to JsonPrimitive(value.role),
                    "content" to JsonPrimitive(value.content),
                    "function_call" to serializeToJsonElement(value.function_call),
                    "tool_calls" to serializeToJsonElement(value.tool_calls),
                )
            )

            is ChatCompletionChoice -> JsonObject(
                content = mapOf(
                    "index" to JsonPrimitive(value.index),
                    "messages" to serializeToJsonElement(value.message),
                    "logprobs" to serializeToJsonElement(value.logprobs),
                    "finish_reason" to JsonPrimitive(value.finish_reason)
                )
            )

            is Usage -> JsonObject(
                content = mapOf(
                    "prompt_tokens" to JsonPrimitive(value.prompt_tokens),
                    "completion_tokens" to JsonPrimitive(value.completion_tokens),
                    "total_tokens" to JsonPrimitive(value.total_tokens),
                )
            )

            is List<*> -> JsonArray(value.map { serializeToJsonElement(it) })
            is Instant -> JsonPrimitive(value.toIsoString())
            else -> Json.encodeToJsonElement(value)
        }
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder =
            decoder as? JsonDecoder ?: throw SerializationException("This class can be loaded only by Json")
        return deserialize(jsonDecoder.decodeJsonElement())!!
    }

    private fun deserialize(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> when {
                element.instantOrNull() != null -> element.instantOrNull()
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                element.booleanOrNull != null -> element.boolean
                else -> element.toString()
            }

            is JsonObject -> element.mapValues { deserialize(it.value) }
            is JsonArray -> element.map { deserialize(it) }
            JsonNull -> null
        }
    }
}

object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageContent")

    override fun serialize(encoder: Encoder, value: MessageContent) {
        when (value) {
            is MessageContent.StringContent -> encoder.encodeString(value.value)
            is MessageContent.RequestContent -> encoder.encodeSerializableValue(
                CompletionRequestContent.serializer(),
                value.value
            )
        }
    }

    override fun deserialize(decoder: Decoder): MessageContent {
        val jsonDecoder =
            decoder as? JsonDecoder ?: throw SerializationException("This class can be loaded only by JSON")
        val json = jsonDecoder.decodeJsonElement()

        return when {
            json is JsonPrimitive && json.isString -> MessageContent.StringContent(json.content)
            else -> MessageContent.RequestContent(
                jsonDecoder.json.decodeFromJsonElement(
                    CompletionRequestContent.serializer(),
                    json
                )
            )
        }
    }
}

object VariableSerializer : KSerializer<VariableType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("VariableType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VariableType) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): VariableType {
        val value = decoder.decodeString()
        return VariableType.valueOf(value)
    }
}

object CompletionRequestSerializer : KSerializer<CompletionRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CompletionRequest") {
        element<String>("role")
        element<JsonElement>("content")
    }

    override fun serialize(encoder: Encoder, value: CompletionRequest) {
        val compositeEncoder = encoder.beginStructure(descriptor)
        compositeEncoder.encodeStringElement(descriptor, 0, value.role)
        val jsonElement = when (val content = value.content) {
            is String -> JsonPrimitive(content)
            is CompletionRequestContent -> Json.encodeToJsonElement(content)
            else -> throw SerializationException("Unsupported content type: ${content::class}")
        }
        compositeEncoder.encodeSerializableElement(descriptor, 1, JsonElement.serializer(), jsonElement)
        compositeEncoder.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): CompletionRequest {
        val compositeDecoder = decoder.beginStructure(descriptor)
        var role: String? = null
        var content: Any? = null
        while (true) {
            when (val index = compositeDecoder.decodeElementIndex(descriptor)) {
                0 -> role = compositeDecoder.decodeStringElement(descriptor, 0)
                1 -> {
                    val element = compositeDecoder.decodeSerializableElement(descriptor, 1, JsonElement.serializer())
                    content = when {
                        element is JsonPrimitive && element.isString -> element.content
                        element is JsonObject -> {
                            when {
                                "text" in element -> Json.decodeFromJsonElement<CompletionRequestContent.Text>(element)
                                "url" in element -> Json.decodeFromJsonElement<CompletionRequestContent.ImageUrl>(
                                    element
                                )

                                else -> throw SerializationException("Unknown CompletionRequestContent type")
                            }
                        }

                        else -> throw SerializationException("Invalid content format")
                    }
                }

                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        compositeDecoder.endStructure(descriptor)
        return CompletionRequest(
            role ?: throw SerializationException("Role is missing"),
            content ?: throw SerializationException("Content is missing")
        )
    }
}


val MaximSerializerModule = SerializersModule {
    polymorphic(Rule::class) {
        subclass(RuleType::class, RuleType.serializer())
        subclass(RuleGroupType::class, RuleGroupType.serializer())
    }
    contextual(CompletionRequest::class, CompletionRequestSerializer)
    contextual(Any::class, AnySerializer)
    contextual(MessageContent::class, MessageContentSerializer)
    contextual(Date::class, DateSerializer)
}


val MaximJson: Json = Json {
    serializersModule = MaximSerializerModule
    classDiscriminator = "type"
    isLenient = true
    ignoreUnknownKeys = true
}