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
    val data: Folder,
    val error: Error? = null
)

@Serializable
data class MaximFoldersResponse(
    val data: List<Folder>,
    val error: Error? = null
)