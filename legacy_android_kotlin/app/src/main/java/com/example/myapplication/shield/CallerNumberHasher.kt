package com.example.myapplication.shield

import java.security.MessageDigest

class CallerNumberHasher {
    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
