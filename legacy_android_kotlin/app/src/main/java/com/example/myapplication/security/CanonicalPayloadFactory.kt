package com.example.myapplication.security

object CanonicalPayloadFactory {
    fun create(challengeId: String, normalizedPublicKey: String): String =
        createVerify(challengeId = challengeId, normalizedPublicKey = normalizedPublicKey)

    fun createVerify(challengeId: String, normalizedPublicKey: String): String =
        createOrderedJson(
            "challenge_id" to challengeId,
            "public_key" to normalizedPublicKey,
        )

    fun createReport(
        clientPhoneNumber: String,
        feature: String,
        normalizedPublicKey: String,
    ): String =
        createOrderedJson(
            "client_phone_number" to clientPhoneNumber,
            "feature" to feature,
            "public_key" to normalizedPublicKey,
        )

    fun createBlacklistCheck(targetHash: String, normalizedPublicKey: String): String =
        createOrderedJson(
            "target_hash" to targetHash,
            "public_key" to normalizedPublicKey,
        )

    private fun createOrderedJson(vararg fields: Pair<String, String>): String =
        buildString {
            append('{')
            fields.forEachIndexed { index, (key, value) ->
                if (index > 0) {
                    append(',')
                }
                append('"')
                append(key)
                append("\":\"")
                append(value.escapeJson())
                append('"')
            }
            append('}')
        }

    private fun String.escapeJson(): String = buildString(length + 8) {
        this@escapeJson.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '/' -> append("\\/")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }
}

