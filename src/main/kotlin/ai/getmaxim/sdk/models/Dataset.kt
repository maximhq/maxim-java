package ai.getmaxim.sdk.models

import kotlinx.serialization.Serializable

@Serializable(with= VariableSerializer::class)
enum class VariableType(val value: String) {
    TEXT("text"),
    JSON("json")
}

@Serializable
data class Variable(
    val type: VariableType,
    val payload: String
)

@Serializable
data class DatasetEntry(
    val input: Variable? = null,
    val context: Variable? = null,
    val expectedOutput: Variable? = null
)

@Serializable
data class AddDatasetEntriesPayload(val datasetId:String, val entries:List<DatasetEntry>)