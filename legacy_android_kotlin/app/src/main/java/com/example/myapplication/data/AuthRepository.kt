package com.example.myapplication.data

interface AuthRepository {
    suspend fun initiateAuth(adUrl: String): InitiateAuthResult

    suspend fun verifyAuth(
        challengeId: String,
        otp: String,
        publicKey: String,
        signature: String,
    ): VerifyAuthResult
}

sealed interface InitiateAuthResult {
    data class Success(
        val challengeId: String,
        val maskedPhoneNumber: String,
        val otpExpiresAt: String,
    ) : InitiateAuthResult

    data class Failure(
        val code: String,
        val message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
        val retryable: Boolean = false,
    ) : InitiateAuthResult
}

sealed interface VerifyAuthResult {
    data class Success(
        val challengeId: String,
        val maskedPhoneNumber: String,
        val verifiedAt: String,
    ) : VerifyAuthResult

    data class Failure(
        val code: String,
        val message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
        val retryable: Boolean = false,
    ) : VerifyAuthResult
}

