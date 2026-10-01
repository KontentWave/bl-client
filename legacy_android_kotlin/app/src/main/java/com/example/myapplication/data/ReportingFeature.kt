package com.example.myapplication.data

enum class ReportingFeature(
    val backendKey: String,
    val displayLabel: String,
) {
    AGGRESSIVE(
        backendKey = "aggressive",
        displayLabel = "Aggressive",
    ),
    NO_SHOW(
        backendKey = "no_show",
        displayLabel = "No-Show",
    ),
    NON_PAYMENT(
        backendKey = "non_payment",
        displayLabel = "Non-Payment",
    ),
    REFUSED_PROTECTION(
        backendKey = "refused_protection",
        displayLabel = "Refused Protection",
    );

    companion object {
        fun all(): List<ReportingFeature> = entries.toList()
    }
}

