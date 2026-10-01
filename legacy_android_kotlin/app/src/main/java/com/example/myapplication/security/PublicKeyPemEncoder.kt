package com.example.myapplication.security

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object PublicKeyPemEncoder {
    fun toPem(publicKey: PublicKey): String {
        val base64Body = Base64.getEncoder().encodeToString(publicKey.encoded)
            .chunked(64)
            .joinToString(separator = "\n")

        return normalize(
            "-----BEGIN PUBLIC KEY-----\n" +
                base64Body +
                "\n-----END PUBLIC KEY-----",
        )
    }

    fun normalize(pem: String): String = pem.replace("\r\n", "\n").trim()

    fun fromPem(pem: String): PublicKey {
        val normalizedPem = normalize(pem)
        val base64Body = normalizedPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .trim()

        val encoded = Base64.getDecoder().decode(base64Body)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }
}


