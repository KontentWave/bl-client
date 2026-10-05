package com.example.myapplication.data

import com.example.myapplication.data.remote.BlacklistApi
import com.example.myapplication.data.remote.model.CheckBlacklistRequest
import com.example.myapplication.security.SignedRequestFactory
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import retrofit2.HttpException
import java.io.IOException
import com.example.myapplication.session.SessionRecovery
import com.example.myapplication.security.DeviceHardwareSecurityUnavailableException

class BlacklistQueryRepositoryImpl(
    private val blacklistApi: BlacklistApi,
    private val signedRequestFactory: SignedRequestFactory,
    private val gson: Gson,
    private val sessionRecovery: SessionRecovery? = null,
) : BlacklistQueryRepository {
    override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult {
        val normalizedTargetHash = targetHash.trim().lowercase()

        return try {
            val signedPayload = runInterruptible(Dispatchers.IO) {
                signedRequestFactory.createBlacklistCheckRequest(normalizedTargetHash)
            }
            currentCoroutineContext().ensureActive()
            val response = blacklistApi.checkBlacklist(
                CheckBlacklistRequest(
                    targetHash = normalizedTargetHash,
                    publicKey = signedPayload.publicKey,
                    signature = signedPayload.signature,
                ),
            )
            currentCoroutineContext().ensureActive()
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
                val failure = ApiFailure.parse(response)
                if (response.code() == 403 && !failure.responseMalformed && failure.code == "blacklist_query_unauthorized") {
                    sessionRecovery?.authorizationRejected(signedPayload.publicKey)
                }
                failure.toQueryFailure()
            }
        } catch (exception: DeviceHardwareSecurityUnavailableException) {
            sessionRecovery?.refresh()
            BlacklistQueryResult.Failure("request_preparation_failed", exception.message ?: "Device key unavailable.")
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

}
