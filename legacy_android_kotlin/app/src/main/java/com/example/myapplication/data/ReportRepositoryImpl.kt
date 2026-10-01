package com.example.myapplication.data

import com.example.myapplication.data.remote.ReportApi
import com.example.myapplication.data.remote.model.ApiErrorEnvelope
import com.example.myapplication.data.remote.model.StoreReportRequest
import com.example.myapplication.security.SignedRequestFactory
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class ReportRepositoryImpl(
    private val reportApi: ReportApi,
    private val signedRequestFactory: SignedRequestFactory,
    private val gson: Gson,
) : ReportRepository {
    override suspend fun submitReport(
        clientPhoneNumber: String,
        feature: ReportingFeature,
    ): ReportSubmissionResult {
        val normalizedClientPhoneNumber = clientPhoneNumber.trim()

        return try {
            val signedPayload = signedRequestFactory.createReportRequest(
                clientPhoneNumber = normalizedClientPhoneNumber,
                feature = feature.backendKey,
            )
            val response = reportApi.storeReport(
                StoreReportRequest(
                    clientPhoneNumber = normalizedClientPhoneNumber,
                    feature = feature.backendKey,
                    publicKey = signedPayload.publicKey,
                    signature = signedPayload.signature,
                ),
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    ReportSubmissionResult.Success(
                        clientHash = body.data.clientHash,
                        reporterHash = body.data.reporterHash,
                        feature = body.data.feature,
                        featureLabel = body.data.featureLabel,
                        uniqueReporterCount = body.data.uniqueReporterCount,
                        level = body.data.level,
                        readyForSync = body.data.readyForSync,
                    )
                } else {
                    ReportSubmissionResult.Failure(
                        code = "unexpected_empty_body",
                        message = "The server returned an empty response.",
                    )
                }
            } else {
                parseFailure(response.errorBody()?.charStream()?.readText()).toReportFailure()
            }
        } catch (_: IOException) {
            ReportSubmissionResult.Failure(
                code = "network_unavailable",
                message = "Unable to reach the server. Check the API base URL and your connection.",
                retryable = true,
            )
        } catch (exception: HttpException) {
            ReportSubmissionResult.Failure(
                code = "http_${exception.code()}",
                message = "The server request failed unexpectedly.",
                retryable = exception.code() in 500..599,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            ReportSubmissionResult.Failure(
                code = "request_preparation_failed",
                message = exception.message ?: "The report request could not be prepared.",
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
        fun toReportFailure(): ReportSubmissionResult.Failure =
            ReportSubmissionResult.Failure(
                code = code,
                message = message,
                fieldErrors = fieldErrors,
                retryable = retryable,
            )
    }
}

