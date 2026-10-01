package com.example.myapplication.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerNumberNormalizerTest {
    private val normalizer = CallerNumberNormalizer()
    private val hasher = CallerNumberHasher()

    @Test
    fun normalize_acceptsInternationalNumbersWithFormatting() {
        assertEquals(
            "+421903223183",
            normalizer.normalize("+421 903 223 183"),
        )
    }

    @Test
    fun normalize_convertsLocalSlovakMobileToE164() {
        assertEquals(
            "+421903223183",
            normalizer.normalize("0903 223 183"),
        )
    }

    @Test
    fun normalize_rejectsInvalidInput() {
        assertNull(normalizer.normalize("unknown caller"))
    }

    @Test
    fun hash_matchesKnownBackendExample() {
        assertEquals(
            "0bd0a9af9829eb59a7a69b433a65f239efb0ef16ac289987e14affc6a622a469",
            hasher.sha256("+421900123456"),
        )
    }
}
