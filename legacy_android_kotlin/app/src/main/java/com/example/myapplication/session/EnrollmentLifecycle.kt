package com.example.myapplication.session

import com.example.myapplication.time.ServerTimestamp
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import java.util.UUID

data class EnrollmentLifecycle(
    @SerializedName("enrollment_id") val enrollmentId: String,
    val kind: String,
    val state: String,
    val version: Long,
    @SerializedName("verified_at") val verifiedAt: String,
    @SerializedName("last_renewed_at") val lastRenewedAt: String?,
    @SerializedName("next_renewal_at") val nextRenewalAt: String?,
    @SerializedName("offboarded_at") val offboardedAt: String?,
    val reason: String?,
) {
    val terminal: Boolean get() = state != "active"

    fun json(): String = GsonBuilder().serializeNulls().create().toJson(this)

    companion object {
        fun parse(root: JsonObject): EnrollmentLifecycle {
            fun text(name: String, nullable: Boolean = false): String? {
                val value = root.get(name) ?: throw JsonParseException("Missing lifecycle $name")
                if (nullable && value.isJsonNull) return null
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString || value.asString.isBlank()) {
                    throw JsonParseException("Invalid lifecycle $name")
                }
                return value.asString
            }
            val id = text("enrollment_id")!!
            if (!isUuid(id)) throw JsonParseException("Invalid enrollment identity")
            val kind = text("kind")!!
            val state = text("state")!!
            val number = root.get("version")
            val version = number?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.toString()?.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()
                ?: throw JsonParseException("Invalid lifecycle version")
            val verified = text("verified_at")!!
            val renewed = text("last_renewed_at", true)
            val due = text("next_renewal_at", true)
            val offboarded = text("offboarded_at", true)
            val reason = text("reason", true)
            val verifiedTime = ServerTimestamp.parse(verified) ?: throw JsonParseException("Invalid verification time")
            fun time(value: String?) = value?.let {
                ServerTimestamp.parse(it) ?: throw JsonParseException("Invalid lifecycle time")
            }
            val renewedTime = time(renewed)
            val dueTime = time(due)
            val offboardedTime = time(offboarded)
            if (kind !in setOf("ad", "phone_only", "legacy") ||
                state !in setOf("active", "offboarded", "superseded") ||
                renewedTime?.isBefore(verifiedTime) == true ||
                offboardedTime?.isBefore(verifiedTime) == true ||
                (state == "active" && (offboarded != null || reason != null)) ||
                (state == "active" && kind == "ad" &&
                    dueTime != (renewedTime ?: verifiedTime).plusSeconds(86_400)) ||
                (kind != "ad" && (renewed != null || due != null)) ||
                (state != "active" && (due != null || offboarded == null ||
                    reason !in setOf("ad_disabled", "ad_expired", "ad_deleted", "contact_changed", "superseded"))) ||
                (state == "offboarded" && (kind != "ad" || reason == "superseded")) ||
                (state == "superseded" && reason != "superseded")
            ) throw JsonParseException("Inconsistent enrollment lifecycle")
            return EnrollmentLifecycle(id, kind, state, version, verified, renewed, due, offboarded, reason)
        }

        fun isUuid(value: String): Boolean = value.length == 36 && try {
            UUID.fromString(value).toString().equals(value, ignoreCase = true)
        } catch (_: IllegalArgumentException) { false }
    }
}

data class RecoveryTicket(val fingerprint: String, val revision: Long, val enrollmentId: String?)

data class RenewalAttempt(
    val windowStart: Long,
    val attempts: Int,
    val failures: Int,
    val eligibleAt: Long,
    val requestId: String? = null,
    val statusRequired: Boolean = false,
) {
    fun json(): String = GsonBuilder().serializeNulls().create().toJson(this)

    companion object {
        fun parse(root: JsonObject): RenewalAttempt {
            fun number(name: String): Long {
                val value = root.get(name)
                return value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                    ?.toString()?.takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
                    ?: throw JsonParseException("Invalid renewal $name")
            }
            val start = number("windowStart")
            val attempts = number("attempts")
            val failures = number("failures")
            val eligible = number("eligibleAt")
            val idValue = root.get("requestId") ?: throw JsonParseException("Missing pending request identity")
            val id = if (idValue.isJsonNull) null else {
                if (!idValue.isJsonPrimitive || !idValue.asJsonPrimitive.isString ||
                    !EnrollmentLifecycle.isUuid(idValue.asString)) throw JsonParseException("Invalid pending request identity")
                idValue.asString
            }
            val statusValue = root.get("statusRequired")
            if (statusValue == null || !statusValue.isJsonPrimitive || !statusValue.asJsonPrimitive.isBoolean) {
                throw JsonParseException("Invalid reconciliation state")
            }
            if (attempts > 4 || failures > 4 || eligible < start) throw JsonParseException("Invalid renewal attempt bounds")
            return RenewalAttempt(start, attempts.toInt(), failures.toInt(), eligible, id, statusValue.asBoolean)
        }
    }
}
