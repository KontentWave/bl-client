package com.example.myapplication.phone

import com.google.gson.Gson

data class PhoneNumberVector(val raw: String, val normalized: String?, val hash: String?)

object PhoneNumberVectors {
    fun load(): List<PhoneNumberVector> =
        checkNotNull(javaClass.getResourceAsStream("/phone-number-vectors.json")).bufferedReader().use {
            Gson().fromJson(it, Array<PhoneNumberVector>::class.java).toList()
        }
}
