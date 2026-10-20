package com.example.myapplication.time

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerTimestampTest {
    @Test fun explicitOffsetsRepresentTheSameInstant() {
        val expected = Instant.parse("2030-01-01T00:15:00Z")
        for (value in listOf(
            "2030-01-01T00:15:00Z", "2030-01-01T00:15:00+00:00",
            "2030-01-01T02:15:00+02:00", "2029-12-31T19:15:00-05:00",
        )) assertEquals(value, expected, ServerTimestamp.parse(value))
    }

    @Test fun fractionalSecondsArePreserved() {
        assertEquals(
            Instant.parse("2030-01-01T00:15:00.123456789Z"),
            ServerTimestamp.parse("2030-01-01T00:15:00.123456789+00:00"),
        )
    }

    @Test fun missingMalformedAndImpossibleDatesFailClosed() {
        for (value in listOf(
            null, "", "not-a-date", "2030-01-01T00:15:00", "2030-02-30T00:15:00+00:00",
            "2030-01-01T25:15:00+00:00", "2030-01-01T00:15:00+25:00",
            "2030-01-01T00:15:00+00:00junk", " 2030-01-01T00:15:00Z",
        )) assertNull(value, ServerTimestamp.parse(value))
    }
}
