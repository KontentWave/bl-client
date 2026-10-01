package com.example.myapplication.security

data class SignedRequestPayload(
    val publicKey: String,
    val signature: String,
)

typealias SignedVerifyPayload = SignedRequestPayload

