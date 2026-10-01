package com.example.myapplication.data

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.remote.AuthApi
import com.example.myapplication.data.remote.model.ApiErrorEnvelope
import com.example.myapplication.data.remote.model.InitiateAuthRequest
import com.example.myapplication.data.remote.model.VerifyAuthRequest
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.security.MessageDigest

private const val LOG_TAG = "AuthRepository"

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val gson: Gson,
) : AuthRepository {
    override suspend fun initiateAuth(adUrl: String): InitiateAuthResult {
        val normalizedAdUrl = adUrl.trim()

        return try {
            val response = authApi.initiateAuth(InitiateAuthRequest(adUrl = normalizedAdUrl))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    InitiateAuthResult.Success(
                        challengeId = body.data.challengeId,
                        maskedPhoneNumber = body.data.maskedPhoneNumber,
                        otpExpiresAt = body.data.otpExpiresAt,
                    )
                } else {
                    InitiateAuthResult.Failure(
                        code = "unexpected_empty_body",
                        message = "The server returned an empty response.",
                    )
                }
            } else {
                parseFailure(response.errorBody()?.charStream()?.readText()).toInitiateFailure()
            }
        } catch (_: IOException) {
            InitiateAuthResult.Failure(
                code = "network_unavailable",
                message = "Unable to reach the server. Check the API base URL and your connection.",
                retryable = true,
            )
        } catch (exception: HttpException) {
            InitiateAuthResult.Failure(
                code = "http_${exception.code()}",
                message = "The server request failed unexpectedly.",
                retryable = exception.code() in 500..599,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            InitiateAuthResult.Failure(
                code = "response_parse_failed",
                message = "The server response could not be processed.",
            )
        }
    }

    override suspend fun verifyAuth(
        challengeId: String,
        otp: String,
        publicKey: String,
        signature: String,
    ): VerifyAuthResult {
        val request = VerifyAuthRequest(
            challengeId = challengeId,
            otp = otp.trim(),
            publicKey = publicKey,
            signature = signature,
        )
        logVerifyRequestDiagnostics(request)

        return try {
            val response = authApi.verifyAuth(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    VerifyAuthResult.Success(
                        challengeId = body.data.challengeId,
                        maskedPhoneNumber = body.data.maskedPhoneNumber,
                        verifiedAt = body.data.verifiedAt,
                    )
                } else {
                    VerifyAuthResult.Failure(
                        code = "unexpected_empty_body",
                        message = "The server returned an empty response.",
                    )
                }
            } else {
                val rawErrorBody = response.errorBody()?.charStream()?.readText()
                logVerifyFailureDiagnostics(rawErrorBody)
                parseFailure(rawErrorBody).toVerifyFailure()
            }
        } catch (_: IOException) {
            VerifyAuthResult.Failure(
                code = "network_unavailable",
                message = "Unable to reach the server. Check the API base URL and your connection.",
                retryable = true,
            )
        } catch (exception: HttpException) {
            VerifyAuthResult.Failure(
                code = "http_${exception.code()}",
                message = "The server request failed unexpectedly.",
                retryable = exception.code() in 500..599,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            VerifyAuthResult.Failure(
                code = "response_parse_failed",
                message = "The server response could not be processed.",
            )
        }
    }

    private fun logVerifyRequestDiagnostics(request: VerifyAuthRequest) {
        if (!BuildConfig.DEBUG) {
            return
        }

        val requestJson = gson.toJson(request)
        runCatching {
            Log.i(
                LOG_TAG,
                "verify_request_diag " +
                    "debug=${BuildConfig.DEBUG} " +
                    "challenge_id=${request.challengeId} " +
                    "otp_length=${request.otp.length} " +
                    "public_key_sha256=${request.publicKey.sha256()} " +
                    "signature_sha256=${request.signature.sha256()} " +
                    "request_json_sha256=${requestJson.sha256()} " +
                    "request_json_length=${requestJson.length}",
            )
        }
    }

    private fun logVerifyFailureDiagnostics(rawErrorBody: String?) {
        if (!BuildConfig.DEBUG) {
            return
        }

        runCatching {
            Log.w(
                LOG_TAG,
                "verify_response_diag " +
                    "debug=${BuildConfig.DEBUG} " +
                    "error_body_sha256=${rawErrorBody.orEmpty().sha256()} " +
                    "error_body=${rawErrorBody.orEmpty()}",
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
        fun toInitiateFailure(): InitiateAuthResult.Failure =
            InitiateAuthResult.Failure(
                code = code,
                message = message,
                fieldErrors = fieldErrors,
                retryable = retryable,
            )

        fun toVerifyFailure(): VerifyAuthResult.Failure =
            VerifyAuthResult.Failure(
                code = code,
                message = message,
                fieldErrors = fieldErrors,
                retryable = retryable,
            )
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}

