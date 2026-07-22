package dev.alexis.mediagallery.data

import com.google.gson.annotations.SerializedName

/**
 * The backend (Go) returns fields with an initial uppercase letter
 * (ID, Title, Type...), @SerializedName maps these to Kotlin properties
 * using camelCase.
 */
data class Media(
    @SerializedName("ID") val id: Int,
    @SerializedName("Title") val title: String,
    @SerializedName("Type") val type: String, // "image", "video", "audio", "others"
    @SerializedName("MimeType") val mimeType: String,
    @SerializedName("CreatedAt") val createdAt: String,
    @SerializedName("Thumbnail") val thumbnail: String?,
    @SerializedName("Filename") val filename: String?,
    @SerializedName("Description") val description: String?
)

data class MediaListResponse(
    val medias: List<Media>?
)

data class LoginRequest(
    val code: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val code: String,
    val password: String
)

data class UpdateMediaRequest(
    val title: String,
    val description: String?
)

data class AuthResponse(
    val token: String?,
    val error: String?
)

data class GenericResponse(
    val message: String?,
    val error: String?
)

data class MissingThumbnailsResponse(
    val count: Int,
    val medias: List<Media>?
)

data class GenerateThumbnailResponse(
    val message: String,
    val thumbnail: String
)

data class StreamUrlResponse(
    @SerializedName("url") val url: String?
)
