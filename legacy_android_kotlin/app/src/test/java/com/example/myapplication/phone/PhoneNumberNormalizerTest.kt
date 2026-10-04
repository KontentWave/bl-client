package com.example.myapplication.phone

import com.example.myapplication.shield.CallerNumberHasher
import com.example.myapplication.shield.CallerNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberNormalizerTest {
    @Test
    fun reportAndCallerPoliciesMatchGoldenNormalizationAndHashVectors() {
        val caller = CallerNumberNormalizer()
        val hasher = CallerNumberHasher()
        for (vector in PhoneNumberVectors.load()) {
            assertEquals(vector.raw, vector.normalized, PhoneNumberNormalizer.normalize(vector.raw))
            assertEquals(vector.raw, vector.normalized, caller.normalize(vector.raw))
            vector.normalized?.let {
                assertEquals(vector.raw, vector.hash, hasher.sha256(it))
                assertEquals(it, PhoneNumberNormalizer.normalize(it))
            }
        }
    }
}
