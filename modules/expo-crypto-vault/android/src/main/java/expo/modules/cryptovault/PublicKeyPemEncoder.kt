package expo.modules.cryptovault

import java.security.PublicKey
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
}