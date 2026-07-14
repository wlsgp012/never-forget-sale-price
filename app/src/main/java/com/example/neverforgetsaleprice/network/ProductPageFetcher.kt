package com.example.neverforgetsaleprice.network

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

class ProductPageFetcher(
    private val client: OkHttpClient
) {
    @Throws(IOException::class)
    fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Android) NeverForgetSalePrice/1.0 price-monitor"
    }
}

class HttpStatusException(
    val statusCode: Int,
    message: String
) : IOException(message)
