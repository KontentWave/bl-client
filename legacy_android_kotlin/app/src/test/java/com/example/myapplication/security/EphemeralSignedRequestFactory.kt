package com.example.myapplication.security

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class EphemeralSignedRequestFactory : SignedRequestFactory {
    val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    val publicKeyPem = PublicKeyPemEncoder.toPem(keyPair.public)
    val reportPayloads = mutableListOf<String>()

    override fun createVerifyRequest(challengeId: String) =
        sign(CanonicalPayloadFactory.createVerify(challengeId, publicKeyPem))

    override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload =
        sign(CanonicalPayloadFactory.createReport(clientPhoneNumber, feature, publicKeyPem).also {
            reportPayloads += it
        })

    override fun createBlacklistCheckRequest(targetHash: String) =
        sign(CanonicalPayloadFactory.createBlacklistCheck(targetHash, publicKeyPem))

    private fun sign(payload: String): SignedRequestPayload {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(payload.toByteArray(Charsets.UTF_8))
        return SignedRequestPayload(publicKeyPem, Base64.getEncoder().encodeToString(signature.sign()))
    }
}
