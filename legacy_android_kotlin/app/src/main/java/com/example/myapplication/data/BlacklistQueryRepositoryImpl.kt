package com.example.myapplication.data

import com.example.myapplication.data.remote.BlacklistApi
import com.example.myapplication.data.remote.model.ApiErrorEnvelope
import com.example.myapplication.data.remote.model.CheckBlacklistRequest
import com.example.myapplication.security.SignedRequestFactory
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class BlacklistQueryRepositoryImpl(
    private val blacklistApi: BlacklistApi,
    private val signedRequestFactory: SignedRequestFactory,
    private val gson: Gson,
) : BlacklistQueryRepository {
    override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult {
        val normalizedTargetHash = targetHash.trim().lowercase()

        return try {
            val signedPayload = signedRequestFactory.createBlacklistCheckRequest(normalizedTargetHash)
            val response = blacklistApi.checkBlacklist(
                CheckBlacklistRequest(
                    targetHash = normalizedTargetHash,
                    publicKey = signedPayload.publicKey,
                    signature = signedPayload.signature,
                ),
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    BlacklistQueryResult.Success(
                        targetHash = body.data.targetHash,
                        features = body.data.features,
                    )
                } else {
                    BlacklistQueryResult.Failure(
                        code = "unexpected_empty_body",
                        message = "The server returned an empty response.",
                    )
                }
            } else {
                parseFailure(response.errorBody()?.charStream()?.readText()).toQueryFailure()
            }
        } catch (_: IOException) {
            BlacklistQueryResult.Failure(
                code = "network_unavailable",
                message = "Unable to reach the server. Check the API base URL and your connection.",
                retryable = true,
            )
        } catch (exception: HttpException) {
            BlacklistQueryResult.Failure(
                code = "http_${exception.code()}",
                message = "The server request failed unexpectedly.",
                retryable = exception.code() in 500..599,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            BlacklistQueryResult.Failure(
                code = "request_preparation_failed",
                message = exception.message ?: "The blacklist query request could not be prepared.",
            )
        }
    }

    private fun parseFailure(rawErrorBody: String?): ApiFailure {
        if (rawErrorBody.isNullOrBlank()) {
            return ApiFailure(
                code = "unexpected_error",
                message = "The request could not be completed.",
            )
        }

        return runCatching {
            gson.fromJson(rawErrorBody, ApiErrorEnvelope::class.java)
        }.getOrNull()?.let { errorEnvelope ->
            ApiFailure(
                code = errorEnvelope.code,
                message = errorEnvelope.message,
                fieldErrors = errorEnvelope.errors,
                retryable = errorEnvelope.meta.retryable == true,
            )
        } ?: ApiFailure(
            code = "unexpected_error",
            message = "The request could not be completed.",
        )
    }

    private data class ApiFailure(
        val code: String,
        val message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
        val retryable: Boolean = false,
    ) {
        fun toQueryFailure(): BlacklistQueryResult.Failure =
            BlacklistQueryResult.Failure(
                code = code,
                message = message,
                fieldErrors = fieldErrors,
                retryable = retryable,
            )
    }
}

