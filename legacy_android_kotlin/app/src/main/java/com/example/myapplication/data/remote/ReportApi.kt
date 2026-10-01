package com.example.myapplication.data.remote

import com.example.myapplication.data.remote.model.StoreReportRequest
import com.example.myapplication.data.remote.model.StoreReportSuccessEnvelope
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ReportApi {
    @POST("reports")
    suspend fun storeReport(
        @Body request: StoreReportRequest,
    ): Response<StoreReportSuccessEnvelope>
}

