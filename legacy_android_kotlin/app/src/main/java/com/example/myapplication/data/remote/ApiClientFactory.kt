package com.example.myapplication.data.remote

import com.example.myapplication.data.remote.ReportApi
import com.example.myapplication.data.remote.model.ApiMeta
import com.example.myapplication.data.remote.model.ApiMetaAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClientFactory {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ApiMeta::class.java, ApiMetaAdapter())
        .create()

    fun createAuthApi(baseUrl: String): AuthApi {
        return createRetrofit(baseUrl).create(AuthApi::class.java)
    }

    fun createReportApi(baseUrl: String): ReportApi {
        return createRetrofit(baseUrl).create(ReportApi::class.java)
    }

    fun createBlacklistApi(baseUrl: String): BlacklistApi {
        return createRetrofit(baseUrl, logHttp = false).create(BlacklistApi::class.java)
    }

    private fun createRetrofit(baseUrl: String, logHttp: Boolean = true): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .apply {
                // Even BASIC logs can copy arbitrary response reasons/transport exceptions.
                if (logHttp) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                // OkHttp can replay a 503 with Retry-After: 0 even when connection retries are off.
                if (response.code == 503) {
                    response.newBuilder().removeHeader("Retry-After").build()
                } else response
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun gson(): Gson = gson
}
