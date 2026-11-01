package com.minhasofertas.app.ui

object InputFormatters {

    private val VALID_DDDS = setOf(
        11, 12, 13, 14, 15, 16, 17, 18, 19,
        21, 22, 24, 27, 28,
        31, 32, 33, 34, 35, 37, 38,
        41, 42, 43, 44, 45, 46, 47, 48, 49,
        51, 53, 54, 55,
        61, 62, 63, 64, 65, 66, 67, 68, 69,
        71, 73, 74, 75, 77, 79,
        81, 82, 83, 84, 85, 86, 87, 88, 89,
        91, 92, 93, 94, 95, 96, 97, 98, 99
    )

    fun formatBrazilianPhone(rawDigits: String): String {
        val digits = rawDigits.filter { it.isDigit() }.take(11)
        return when {
            digits.isEmpty() -> ""
            digits.length <= 2 -> "(${digits}"
            digits.length <= 6 -> "(${digits.take(2)}) ${digits.drop(2)}"
            digits.length <= 10 -> "(${digits.take(2)}) ${digits.substring(2, 6)}-${digits.substring(6)}"
            else -> "(${digits.take(2)}) ${digits.substring(2, 7)}-${digits.substring(7)}"
        }
    }

    fun validateBrazilianPhone(rawDigits: String): Pair<Boolean, String?> {
        val digits = rawDigits.filter { it.isDigit() }
        if (digits.isEmpty()) {
            return false to "Informe o número do celular com DDD."
        }
        if (digits.length < 2) {
            return false to "Digite o DDD (2 dígitos)."
        }
        val ddd = digits.take(2).toIntOrNull() ?: 0
        if (!VALID_DDDS.contains(ddd)) {
            return false to "DDD $ddd não é válido no Brasil."
        }
        if (digits.length < 10) {
            return false to "Número incompleto (${digits.length}/11 dígitos)."
        }
        if (digits.length == 11 && digits[2] != '9') {
            return false to "Celulares com 11 dígitos no Brasil iniciam com 9."
        }
        return true to null
    }

    fun isValidApiId(apiId: String): Boolean {
        val digits = apiId.trim().filter { it.isDigit() }
        return digits.length in 6..10
    }

    fun isValidApiHash(apiHash: String): Boolean {
        val clean = apiHash.trim().lowercase()
        return clean.length == 32 && clean.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
