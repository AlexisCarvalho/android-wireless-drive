package dev.alexis.wirelessdrive.network

import dev.alexis.wirelessdrive.data.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    @Volatile
    private var api: ApiService? = null

    fun getInstance(tokenManager: TokenManager): ApiService {
        return api ?: synchronized(this) {
            api ?: buildRetrofit(tokenManager).also { api = it }
        }
    }

    private fun buildRetrofit(tokenManager: TokenManager): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor())
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val placeholderBaseUrl =
            if (ApiConfig.baseUrl.endsWith("/")) ApiConfig.baseUrl else "${ApiConfig.baseUrl}/"

        return Retrofit.Builder()
            .baseUrl(placeholderBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}