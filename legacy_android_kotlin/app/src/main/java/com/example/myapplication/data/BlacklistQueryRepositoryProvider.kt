package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.security.SecuritySignedRequestFactory

object BlacklistQueryRepositoryProvider {
    fun create(baseUrl: String = BuildConfig.API_BASE_URL): BlacklistQueryRepository {
        val blacklistApi = ApiClientFactory.createBlacklistApi(baseUrl)
        return BlacklistQueryRepositoryImpl(
            blacklistApi = blacklistApi,
            signedRequestFactory = SecuritySignedRequestFactory(),
            gson = ApiClientFactory.gson(),
        )
    }
}
