package com.example.myapplication.data

import com.example.myapplication.data.remote.ReportApi
import com.example.myapplication.data.remote.model.StoreReportRequest
import com.example.myapplication.security.SignedRequestFactory
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import com.example.myapplication.session.SessionRecovery
import com.example.myapplication.security.DeviceHardwareSecurityUnavailableException

class ReportRepositoryImpl(
    private val reportApi: ReportApi,
    private val signedRequestFactory: SignedRequestFactory,
    private val gson: Gson,
    private val sessionRecovery: SessionRecovery? = null,
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
                val failure = ApiFailure.parse(response)
                if (response.code() == 403 && !failure.responseMalformed && failure.code == "device_not_bound") {
                    sessionRecovery?.authorizationRejected(signedPayload.publicKey)
                }
                failure.toReportFailure()
            }
        } catch (exception: DeviceHardwareSecurityUnavailableException) {
            sessionRecovery?.refresh()
            ReportSubmissionResult.Failure("request_preparation_failed", exception.message ?: "Device key unavailable.")
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

}
