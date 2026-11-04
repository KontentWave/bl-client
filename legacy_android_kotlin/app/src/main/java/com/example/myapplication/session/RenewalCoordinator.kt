package com.example.myapplication.session

import com.example.myapplication.data.ApiFailure
import com.example.myapplication.data.remote.LifecycleApi
import com.example.myapplication.security.DeviceHardwareSecurityUnavailableException
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.time.ServerTimestamp
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex

class RenewalCoordinator(
    private val recovery: SessionRecovery,
    private val api: LifecycleApi,
    private val signer: SignedRequestFactory,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val requestId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    @Volatile private var storageUnavailable = false

    fun retryStorageOnForeground() { storageUnavailable = false }

    fun nextEligibleAt(): Long? {
        if (storageUnavailable) return null
        val metadata = recovery.state.value.metadata ?: return null
        val lifecycle = metadata.lifecycle
        if (lifecycle != null && (lifecycle.terminal || lifecycle.kind != "ad")) return null
        val attempt = metadata.renewalAttempt
        val due = lifecycle?.nextRenewalAt?.let { ServerTimestamp.parse(it)?.toEpochMilli() } ?: nowMillis()
        return attempt?.eligibleAt ?: due
    }

    suspend fun runEligible() {
        if (!mutex.tryLock()) return
        try {
            val now = nowMillis()
            if (nextEligibleAt()?.let { it > now } != false) return
            val ticket = recovery.ticket() ?: return
            val metadata = recovery.state.value.metadata ?: return
            val previous = metadata.renewalAttempt
            val status = ticket.enrollmentId == null || previous?.statusRequired == true
            val sameWindow = previous != null && now < previous.windowStart + DAY
            val attempts = if (sameWindow) previous!!.attempts else 0
            val failures = if (sameWindow) previous!!.failures else 0
            if (attempts >= 4 && previous?.requestId == null) {
                recovery.recordAttempt(ticket, previous!!.copy(eligibleAt = previous.windowStart + DAY))
                return
            }
            val id = previous?.requestId ?: requestId()
            val attempt = RenewalAttempt(
                if (sameWindow) previous!!.windowStart else now,
                attempts + if (previous?.requestId != null) 0 else 1,
                failures,
                now + FIFTEEN_MINUTES,
                id,
                statusRequired = status,
            )
            if (!recovery.recordAttempt(ticket, attempt)) {
                storageUnavailable = true
                return
            }
            try {
                val signed = runInterruptible(Dispatchers.IO) {
                    signer.createLifecycleRequest(id, if (status) null else ticket.enrollmentId,
                        if (status) null else metadata.lifecycle?.nextRenewalAt)
                }
                currentCoroutineContext().ensureActive()
                if (!recovery.matches(ticket, signed.publicKey)) return
                val request = JsonObject().apply {
                    addProperty("request_id", id)
                    addProperty("public_key", signed.publicKey)
                    addProperty("signature", signed.signature)
                    if (!status) {
                        addProperty("enrollment_id", ticket.enrollmentId)
                        addProperty("next_renewal_at", metadata.lifecycle?.nextRenewalAt)
                    }
                }
                val response = if (status) api.status(request) else api.renew(request)
                currentCoroutineContext().ensureActive()
                if (!recovery.matches(ticket, signed.publicKey)) return
                if (response.code() == 200) {
                    val body = response.body() ?: throw JsonParseException("Empty lifecycle response")
                    val operationCodes = if (status) setOf("auth.status")
                        else setOf("auth.renewed", "auth.renewal_not_due", "auth.renewal_exempt")
                    val success = body.get("success")
                    if (success == null || !success.isJsonPrimitive || !success.asJsonPrimitive.isBoolean || !success.asBoolean ||
                        requiredText(body, "code") !in operationCodes) throw JsonParseException("Unexpected lifecycle response")
                    val data = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: throw JsonParseException("Missing lifecycle response data")
                    if (requiredText(data, "request_id") != id) throw JsonParseException("Mismatched lifecycle request")
                    val serverTime = ServerTimestamp.parse(requiredText(data, "server_time"))
                        ?: throw JsonParseException("Invalid server time")
                    val lifecycle = EnrollmentLifecycle.parse(data.get("lifecycle")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: throw JsonParseException("Missing lifecycle"))
                    if (lifecycle.terminal) throw JsonParseException("Terminal state in success envelope")
                    if (status && ticket.enrollmentId != null && lifecycle.enrollmentId != ticket.enrollmentId) {
                        recovery.authorizationRejected(signed.publicKey, ticket)
                        return
                    }
                    val code = requiredText(body, "code")
                    if (serverTime.isBefore(ServerTimestamp.parse(lifecycle.verifiedAt)!!) ||
                        lifecycle.lastRenewedAt?.let { serverTime.isBefore(ServerTimestamp.parse(it)!!) } == true ||
                        (code == "auth.renewal_not_due" &&
                            lifecycle.nextRenewalAt?.let { !serverTime.isBefore(ServerTimestamp.parse(it)!!) } != false)
                    ) throw JsonParseException("Inconsistent server lifecycle time")
                    if ((code == "auth.renewal_exempt" && lifecycle.kind == "ad") ||
                        (code == "auth.renewed" && (lifecycle.kind != "ad" || lifecycle.lastRenewedAt == null)) ||
                        (code == "auth.renewal_not_due" && lifecycle.kind != "ad")) {
                        throw JsonParseException("Inconsistent lifecycle outcome")
                    }
                    val dueDelay = lifecycle.nextRenewalAt?.let {
                        (ServerTimestamp.parse(it)!!.toEpochMilli() - serverTime.toEpochMilli()).coerceAtLeast(0)
                    } ?: DAY
                    recovery.applyLifecycle(ticket, signed.publicKey, lifecycle, attempt.copy(
                        failures = 0, eligibleAt = nowMillis() + dueDelay, requestId = null, statusRequired = false,
                    ), updateAttempt = true)
                } else {
                    val failure = ApiFailure.parse(response)
                    if (!failure.responseMalformed && failure.requestId == id && failure.serverTime != null &&
                        response.code() == 403 && failure.code == "device_offboarded" &&
                        !failure.retryable && failure.lifecycle?.terminal == true
                    ) {
                        recovery.applyLifecycle(ticket, signed.publicKey, failure.lifecycle)
                    } else if (!failure.responseMalformed && response.code() == 409 &&
                        failure.code == "auth.renewal_cycle_changed" && failure.requestId == id &&
                        failure.serverTime != null && failure.lifecycle?.terminal == false
                    ) {
                        val serverTime = ServerTimestamp.parse(failure.serverTime)!!
                        if (failure.lifecycle.kind != "ad" ||
                            serverTime.isBefore(ServerTimestamp.parse(failure.lifecycle.verifiedAt)!!) ||
                            failure.lifecycle.lastRenewedAt?.let { serverTime.isBefore(ServerTimestamp.parse(it)!!) } == true
                        ) throw JsonParseException("Inconsistent renewal cycle snapshot")
                        val due = ServerTimestamp.parse(failure.lifecycle.nextRenewalAt!!)!!
                        recovery.applyLifecycle(ticket, signed.publicKey, failure.lifecycle, attempt.copy(
                            requestId = null, eligibleAt = nowMillis() + (due.toEpochMilli() - serverTime.toEpochMilli()).coerceAtLeast(FIFTEEN_MINUTES),
                        ), updateAttempt = true)
                    } else if (!failure.responseMalformed && response.code() == 409 &&
                        failure.code == "auth.enrollment_changed" && !failure.retryable && !status
                    ) {
                        finishFailure(ticket, attempt.copy(statusRequired = true),
                            "Enrollment changed; a bounded signed status reconciliation is required.", null, true)
                    } else {
                        val retry = failure.responseMalformed || (failure.retryable &&
                            ((response.code() == 429 && failure.code == "rate_limited") ||
                                (response.code() == 503 && failure.code in setOf("auth.ad_status_unavailable", "abuse_protection_unavailable"))))
                        finishFailure(ticket, attempt, failure.message, failure.retryAfterSeconds, retry)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: DeviceHardwareSecurityUnavailableException) {
                finishFailure(ticket, attempt, exception.message ?: "Device key unavailable.", null, true)
            } catch (_: IOException) {
                finishFailure(ticket, attempt, "Renewal could not reach the server. Existing access was retained.", null, true)
            } catch (_: JsonParseException) {
                finishFailure(ticket, attempt, "Renewal response was malformed. Existing lifecycle was retained.", null, true)
            } catch (_: IllegalStateException) {
                finishFailure(ticket, attempt, "Renewal response or signing state could not be validated.", null, true)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun finishFailure(ticket: RecoveryTicket, attempt: RenewalAttempt, message: String, retrySeconds: Long?, retry: Boolean) {
        val failures = (attempt.failures + 1).coerceAtMost(4)
        val delay = listOf(FIFTEEN_MINUTES, HOUR, 6 * HOUR)[(failures - 1).coerceAtMost(2)]
        val eligible = maxOf(nowMillis() + delay, nowMillis() + (retrySeconds ?: 0) * 1000,
            if (attempt.attempts >= 4) attempt.windowStart + DAY else 0)
        if (!recovery.recordAttempt(ticket, attempt.copy(failures = failures, requestId = null,
            eligibleAt = if (retry) eligible else Long.MAX_VALUE))) {
            storageUnavailable = true
            return
        }
        recovery.renewalMessage(ticket, "$message No SMS was sent. " +
            if (retry) "Renewal will retry only at its bounded next opportunity." else "Explicit verification or support is required; automatic requests stopped.")
    }

    private fun requiredText(root: JsonObject, name: String): String {
        val value = root.get(name)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString || value.asString.isBlank()) {
            throw JsonParseException("Invalid $name")
        }
        return value.asString
    }

    companion object {
        const val FIFTEEN_MINUTES = 900_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
    }
}
