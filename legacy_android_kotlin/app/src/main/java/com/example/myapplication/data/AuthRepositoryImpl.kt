package com.example.myapplication.data

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.remote.AuthApi
import com.example.myapplication.data.remote.model.InitiateAuthRequest
import com.example.myapplication.data.remote.model.InitiateAuthSuccessEnvelope
import com.example.myapplication.data.remote.model.InitiateOwnerAuthRequest
import com.example.myapplication.data.remote.model.VerifyAuthRequest
import com.example.myapplication.phone.OwnerPhoneNumber
import com.google.gson.Gson
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.security.MessageDigest

private const val LOG_TAG = "AuthRepository"

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val gson: Gson,
) : AuthRepository {
    override suspend fun initiateAuth(adUrl: String): InitiateAuthResult {
        return initiate { authApi.initiateAuth(InitiateAuthRequest(adUrl = adUrl.trim())) }
    }

    override suspend fun initiateOwnerAuth(phoneNumber: String): InitiateAuthResult {
        if (!OwnerPhoneNumber.isValid(phoneNumber)) {
            return InitiateAuthResult.Failure(
                code = "validation_failed",
                message = "Enter a canonical international phone number.",
                fieldErrors = mapOf("phone_number" to listOf("Use + and 8-15 digits, starting with a nonzero digit; no spaces.")),
            )
        }
        return initiate { authApi.initiateOwnerAuth(InitiateOwnerAuthRequest(phoneNumber)) }
    }

    private suspend fun initiate(
        request: suspend () -> Response<InitiateAuthSuccessEnvelope>,
    ): InitiateAuthResult {
        return try {
            val response = request()
            if (response.isSuccessful) {
                val body = response.body()
                if (response.code() == 201 && body?.success == true && body.code == "auth.sms_initiated" &&
                    !body.data.challengeId.isNullOrBlank() && !body.data.maskedPhoneNumber.isNullOrBlank() &&
                    !body.data.otpExpiresAt.isNullOrBlank()
                ) {
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
                val failure = ApiFailure.parse(response).toInitiateFailure()
                if (failure.code == "owner_enrollment_unavailable") failure.copy(retryable = false)
                else failure
            }
        } catch (_: MalformedJsonException) {
            InitiateAuthResult.Failure(
                code = "response_parse_failed",
                message = "SMS delivery is unknown because the server response could not be processed. Retry manually later.",
                responseMalformed = true,
            )
        } catch (_: IOException) {
            InitiateAuthResult.Failure(
                code = "network_unavailable",
                message = "SMS delivery is unknown. Do not resend automatically; wait and retry manually if needed.",
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
                if (response.code() == 200 && body?.success == true && body.code == "auth.verified") {
                    VerifyAuthResult.Success(
                        challengeId = body.data.challengeId,
                        maskedPhoneNumber = body.data.maskedPhoneNumber,
                        verifiedAt = body.data.verifiedAt,
                        lifecycle = body.data.lifecycle?.let(com.example.myapplication.session.EnrollmentLifecycle::parse),
                    )
                } else {
                    VerifyAuthResult.Failure(
                        code = "unexpected_empty_body",
                        message = "The server returned an empty response.",
                    )
                }
            } else {
                ApiFailure.parse(response).toVerifyFailure()
            }
        } catch (_: MalformedJsonException) {
            VerifyAuthResult.Failure(
                code = "response_parse_failed",
                message = "The server response could not be processed.",
                responseMalformed = true,
            )
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


    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}
