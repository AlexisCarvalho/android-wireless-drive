package dev.alexis.mediagallery.network

import dev.alexis.mediagallery.data.AuthResponse
import dev.alexis.mediagallery.data.GenerateThumbnailResponse
import dev.alexis.mediagallery.data.GenericResponse
import dev.alexis.mediagallery.data.LoginRequest
import dev.alexis.mediagallery.data.Media
import dev.alexis.mediagallery.data.MediaListResponse
import dev.alexis.mediagallery.data.MissingThumbnailsResponse
import dev.alexis.mediagallery.data.RegisterRequest
import dev.alexis.mediagallery.data.StreamUrlResponse
import dev.alexis.mediagallery.data.UpdateMediaRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiService {

    @POST("/api/users/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("/api/users/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("/api/media/owner")
    suspend fun getMedias(): Response<MediaListResponse>

    @GET("/api/media/{id}")
    suspend fun getMediaDetail(@Path("id") id: Int): Response<Media>

    @GET("/api/media/{id}/stream-url")
    suspend fun getMediaStreamUrl(
        @Path("id") id: Int,
        @Query("type") type: String? = null
    ): Response<StreamUrlResponse>

    // Used for both streaming (type omitted/"stream") and download
    // (type="download"): the URL is already ready (with a short-lived token)
    // from getMediaStreamUrl, so we just stream its bytes.
    @Streaming
    @GET
    suspend fun downloadFromUrl(@Url url: String): Response<ResponseBody>

    @Streaming
    @GET("/api/media/{id}/file")
    suspend fun getMediaFile(@Path("id") id: Int): Response<ResponseBody>

    @PUT("/api/media/{id}")
    suspend fun updateMedia(
        @Path("id") id: Int,
        @Body request: UpdateMediaRequest
    ): Response<GenericResponse>

    @DELETE("/api/media/{id}")
    suspend fun deleteMedia(@Path("id") id: Int): Response<GenericResponse>

    @POST("/api/media/{id}/generate-thumbnail")
    suspend fun generateThumbnail(@Path("id") id: Int): Response<GenerateThumbnailResponse>

    @POST("/api/media/{id}/delete-thumbnail")
    suspend fun deleteThumbnail(@Path("id") id: Int): Response<GenericResponse>

    @GET("/api/media/owner/missing-thumbnails")
    suspend fun getMissingThumbnails(): Response<MissingThumbnailsResponse>

    @Multipart
    @POST("/api/media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody
    ): Response<GenericResponse>
}