package com.example.myapplication.phone

/**
 * Formatting-only subset of the backend's prefix policy, not a numbering-plan validator.
 * Never infer a country for bare local digits or discard letters/extensions.
 */
object PhoneNumberNormalizer {
    const val INVALID_INPUT_MESSAGE =
        "Use + or 00 international format, Slovak 0 trunk format, or a bare 421 country prefix. " +
            "Only ASCII digits, spaces and hyphens are supported; bare local numbers and extensions are not."

    fun normalize(rawNumber: String): String? {
        val input = rawNumber.trim { it in " \t\n\r\u0000\u000B" }
        if (input.isEmpty() || input.any { it !in '0'..'9' && it !in "+ -" }) return null
        if (input.first() == '-' || input.last() == '-') return null

        val compact = input.replace(" ", "").replace("-", "")
        if ('+' in compact.drop(1)) return null

        val normalized = when {
            compact.startsWith("+") -> compact
            compact.startsWith("00") -> "+" + compact.drop(2)
            compact.startsWith("421") && compact.length > 3 -> "+" + compact
            compact.startsWith("0") -> "+421" + compact.drop(1)
            else -> return null
        }
        val digits = normalized.drop(1)
        if (digits.isEmpty() || digits.first() !in '1'..'9' || digits.any { it !in '0'..'9' }) return null
        if (digits == "421") return null
        return normalized
    }
}
