package dev.alexis.wirelessdrive.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val currentBaseUrl = ApiConfig.baseUrl.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val rewrittenUrl = original.url.newBuilder()
            .scheme(currentBaseUrl.scheme)
            .host(currentBaseUrl.host)
            .port(currentBaseUrl.port)
            .build()

        val rewrittenRequest = original.newBuilder()
            .url(rewrittenUrl)
            .build()

        return chain.proceed(rewrittenRequest)
    }
}