package com.example.myapplication.shield

import com.example.myapplication.phone.PhoneNumberNormalizer

class CallerNumberNormalizer {
    fun normalize(rawNumber: String): String? = PhoneNumberNormalizer.normalize(rawNumber)
}
