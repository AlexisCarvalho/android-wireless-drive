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
import dev.alexis.mediagallery.ui.gallery.GalleryViewModel
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
import retrofit2.http.Streaming

interface ApiService {

    @POST("/api/users/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("/api/users/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("/api/media/owner")
    suspend fun getMedias(): Response<MediaListResponse>

    // Metadados de um item só (Title, CreatedAt, Type, MimeType...) -- mesma
    // rota que o video.html chama antes de baixar o arquivo em si.
    @GET("/api/media/{id}")
    suspend fun getMediaDetail(@Path("id") id: Int): Response<Media>

    // Nova rota usada pelo site para devolver uma URL temporária de streaming.
    // Mantemos a rota antiga /file disponível para download.
    @GET("/api/media/{id}/stream-url")
    suspend fun getMediaStreamUrl(@Path("id") id: Int): Response<StreamUrlResponse>

    // @Streaming evita que o OkHttp carregue o arquivo inteiro na memória
    // antes de entregar a resposta -- importante para vídeos grandes.
    // No passo do visualizador em tela cheia usamos isso pra tocar
    // o vídeo sem duplicar o "baixa tudo, depois toca" que o site faz.
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

    // title/description são campos de texto separados no multipart, não JSON --
    // é assim que o upload.html monta o FormData.
    @Multipart
    @POST("/api/media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody
    ): Response<GenericResponse>
}