package com.example.myapplication.phone

/** Owner issuance accepts canonical E.164 only; report/caller normalization is separate. */
object OwnerPhoneNumber {
    private val canonical = Regex("\\+[1-9][0-9]{7,14}")

    fun isValid(value: String): Boolean = value.length in 9..16 && canonical.matches(value)
}
