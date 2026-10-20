package com.example.myapplication.time

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises packaged/desugared java.time, unlike host-JVM tests. No API or key operations. */
@RunWith(AndroidJUnit4::class)
class ServerTimestampAndroidTest {
    @Test fun offsetAndZuluExpiriesParseOnAndroid() {
        val expected = Instant.ofEpochSecond(1893456900L)
        for (value in listOf(
            "2030-01-01T00:15:00Z", "2030-01-01T00:15:00+00:00",
            "2030-01-01T02:15:00+02:00", "2029-12-31T19:15:00-05:00",
        )) assertEquals(value, expected, ServerTimestamp.parse(value))
    }

    @Test fun invalidTimestampsRemainRejectedOnAndroid() {
        for (value in listOf(null, "", "2030-02-30T00:15:00+00:00", "2030-01-01T00:15:00")) {
            assertNull(value, ServerTimestamp.parse(value))
        }
    }
}
