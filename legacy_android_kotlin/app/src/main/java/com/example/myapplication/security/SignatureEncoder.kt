package com.example.myapplication.security

import android.util.Base64

object SignatureEncoder {
    fun toBase64(signatureBytes: ByteArray): String =
        Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
}


