package com.example.myapplication.time

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Server ISO-8601 timestamps require an explicit offset; never infer the phone's timezone. */
object ServerTimestamp {
    fun parse(value: String?): Instant? {
        if (value == null) return null
        return try {
            // Bundled desugar_jdk_libs Instant.parse requires Z, unlike newer host JDKs.
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
