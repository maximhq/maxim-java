package ai.getmaxim.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val parentFolderId: String?,
    val tags: Tags
)

@Serializable
data class MaximFolderResponse(
    val data: Folder? = null,
    val error: Error? = null
)

@Serializable
data class MaximFoldersResponse(
    val data: List<Folder> = emptyList(),
    val error: Error? = null
)