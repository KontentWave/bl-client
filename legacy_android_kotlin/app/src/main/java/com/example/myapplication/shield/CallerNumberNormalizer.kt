package com.example.myapplication.shield

class CallerNumberNormalizer(
    private val defaultCountryDialCode: String = "+421",
) {
    fun normalize(rawNumber: String): String? {
        val trimmed = rawNumber.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val compact = trimmed.filterIndexed { index, character ->
            character.isDigit() || (character == '+' && index == 0)
        }

        if (compact.isBlank()) {
            return null
        }

        val normalized = when {
            compact.startsWith("+") -> compact
            compact.startsWith("00") -> "+${compact.drop(2)}"
            compact.startsWith("0") -> defaultCountryDialCode + compact.drop(1)
            compact.all(Char::isDigit) && compact.length == 9 -> defaultCountryDialCode + compact
            else -> null
        }

        return normalized
            ?.takeIf { it.startsWith("+") }
            ?.takeIf { it.drop(1).all(Char::isDigit) }
    }
}
