package com.example.myapplication.security

interface ChallengePayloadSigner {
    fun signChallenge(challengeId: String): SignedVerifyPayload
}

class SecurityChallengePayloadSigner(
    private val securityManager: SecurityManager = SecurityManager(),
) : ChallengePayloadSigner {
    override fun signChallenge(challengeId: String): SignedVerifyPayload =
        securityManager.createSignedVerifyPayload(challengeId)
}

