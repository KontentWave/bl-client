package com.example.myapplication.data

import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import retrofit2.Response

/** Shared error decoder. Empty Laravel arrays are accepted, non-empty malformed errors are not. */
internal data class ApiFailure(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val retryable: Boolean = false,
    val retryAfterSeconds: Long? = null,
    val responseMalformed: Boolean = false,
) {
    fun toInitiateFailure() = InitiateAuthResult.Failure(code, message, fieldErrors, retryable, retryAfterSeconds, responseMalformed)
    fun toVerifyFailure() = VerifyAuthResult.Failure(code, message, fieldErrors, retryable, retryAfterSeconds, responseMalformed)
    fun toReportFailure() = ReportSubmissionResult.Failure(code, message, fieldErrors, retryable, retryAfterSeconds, responseMalformed)
    fun toQueryFailure() = BlacklistQueryResult.Failure(code, message, fieldErrors, retryable, retryAfterSeconds, responseMalformed)

    companion object {
        const val MAX_DELAY_SECONDS = 86_400L
        const val DEFAULT_DELAY_SECONDS = 60L

        private fun positiveSeconds(value: String?): Long? = value
            ?.takeIf { it.matches(Regex("[0-9]+")) }
            ?.toLongOrNull()?.takeIf { it > 0 }?.coerceAtMost(MAX_DELAY_SECONDS)

        fun parse(response: Response<*>): ApiFailure {
            val limited = response.code() == 429
            val headerDelay = positiveSeconds(response.headers()["Retry-After"]?.trim())
            return try {
                val root = JsonParser.parseString(response.errorBody()?.string().orEmpty()).asJsonObject
                fun requiredString(name: String): String {
                    val value = root.get(name)
                    if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString || value.asString.isBlank()) {
                        throw JsonParseException("Invalid error envelope")
                    }
                    return value.asString
                }
                val success = root.get("success")
                if (success == null || !success.isJsonPrimitive || !success.asJsonPrimitive.isBoolean || success.asBoolean) {
                    throw JsonParseException("Invalid error status")
                }
                val code = requiredString("code")
                val message = requiredString("message")
                val errors = root.get("errors")
                val fields = when {
                    errors == null || errors.isJsonNull -> emptyMap()
                    errors.isJsonArray && errors.asJsonArray.size() == 0 -> emptyMap()
                    errors.isJsonObject -> errors.asJsonObject.entrySet().associate { (key, value) ->
                        if (!value.isJsonArray) throw JsonParseException("Invalid field errors")
                        key to value.asJsonArray.map {
                            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) throw JsonParseException("Invalid field error")
                            it.asString
                        }
                    }
                    else -> throw JsonParseException("Invalid errors")
                }
                val meta = root.get("meta")
                val metaObject = when {
                    meta == null || meta.isJsonNull -> null
                    meta.isJsonObject -> meta.asJsonObject
                    meta.isJsonArray && meta.asJsonArray.size() == 0 -> null
                    else -> throw JsonParseException("Invalid metadata")
                }
                val retry = metaObject?.get("retryable")
                if (retry != null && !retry.isJsonNull && (!retry.isJsonPrimitive || !retry.asJsonPrimitive.isBoolean)) {
                    throw JsonParseException("Invalid retryability")
                }
                val delay = metaObject?.get("retry_after")?.takeIf {
                    it.isJsonPrimitive && it.asJsonPrimitive.isNumber
                }?.toString()?.let(::positiveSeconds)
                ApiFailure(
                    code = code,
                    message = message,
                    fieldErrors = fields,
                    retryable = retry?.takeUnless { it.isJsonNull }?.asBoolean == true,
                    // Metadata is authoritative if valid; the header is a fallback, not a scope hint.
                    retryAfterSeconds = if (limited) delay ?: headerDelay ?: DEFAULT_DELAY_SECONDS else null,
                )
            } catch (_: Exception) {
                ApiFailure(
                    code = if (limited) "rate_limited" else "response_parse_failed",
                    message = "The server response could not be processed. Please retry manually later.",
                    retryable = limited,
                    retryAfterSeconds = if (limited) headerDelay ?: DEFAULT_DELAY_SECONDS else null,
                    responseMalformed = true,
                )
            }
        }
    }
}
