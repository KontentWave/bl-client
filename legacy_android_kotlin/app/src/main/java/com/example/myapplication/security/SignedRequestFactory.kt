package com.example.myapplication.security

interface SignedRequestFactory {
    fun createVerifyRequest(challengeId: String): SignedRequestPayload

    fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload

    fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload
}

class SecuritySignedRequestFactory(
    private val securityManager: SecurityManager = SecurityManager(),
) : SignedRequestFactory {
    override fun createVerifyRequest(challengeId: String): SignedRequestPayload {
        val normalizedPublicKey = normalizedPublicKey()
        val canonicalPayload = CanonicalPayloadFactory.createVerify(challengeId, normalizedPublicKey)
        return securityManager.createSignedRequestPayload(canonicalPayload, normalizedPublicKey)
    }

    override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload {
        val normalizedPublicKey = normalizedPublicKey()
        val canonicalPayload = CanonicalPayloadFactory.createReport(
            clientPhoneNumber = clientPhoneNumber,
            feature = feature,
            normalizedPublicKey = normalizedPublicKey,
        )
        return securityManager.createSignedRequestPayload(canonicalPayload, normalizedPublicKey)
    }

    override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload {
        val normalizedPublicKey = normalizedPublicKey()
        val canonicalPayload = CanonicalPayloadFactory.createBlacklistCheck(
            targetHash = targetHash,
            normalizedPublicKey = normalizedPublicKey,
        )
        return securityManager.createSignedRequestPayload(canonicalPayload, normalizedPublicKey)
    }

    private fun normalizedPublicKey(): String =
        PublicKeyPemEncoder.normalize(securityManager.getPublicKeyPem())
}

