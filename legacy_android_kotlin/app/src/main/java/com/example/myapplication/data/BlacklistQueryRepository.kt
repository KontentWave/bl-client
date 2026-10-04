package com.example.myapplication.data

interface BlacklistQueryRepository {
    suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult
}

sealed interface BlacklistQueryResult {
    data class Success(
        val targetHash: String,
        val features: List<String>,
    ) : BlacklistQueryResult

    data class Failure(
        val code: String,
        val message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
        val retryable: Boolean = false,
        val retryAfterSeconds: Long? = null,
        val responseMalformed: Boolean = false,
    ) : BlacklistQueryResult
}

