package br.com.sisgfin.core.validation

object DocumentValidator {

    fun validate(raw: String) {
        val digits = raw.onlyDigits()
        when (digits.length) {
            11 -> require(isValidCpf(digits)) { "CPF inválido." }
            14 -> require(isValidCnpj(digits)) { "CNPJ inválido." }
            else -> throw IllegalArgumentException("Documento inválido: deve ter 11 dígitos (CPF) ou 14 dígitos (CNPJ).")
        }
    }

    fun normalize(raw: String): String = raw.onlyDigits()

    fun isValidCpf(raw: String): Boolean {
        val d = raw.onlyDigits()
        if (d.length != 11) return false
        if (d.all { it == d[0] }) return false

        val d1 = checkDigit(d, weights = (10 downTo 2).toList())
        val d2 = checkDigit(d, weights = (11 downTo 2).toList())
        return d[9].digitToInt() == d1 && d[10].digitToInt() == d2
    }

    fun isValidCnpj(raw: String): Boolean {
        val d = raw.onlyDigits()
        if (d.length != 14) return false
        if (d.all { it == d[0] }) return false

        val w1 = listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val w2 = listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val d1 = checkDigit(d, w1)
        val d2 = checkDigit(d, w2)
        return d[12].digitToInt() == d1 && d[13].digitToInt() == d2
    }

    private fun checkDigit(digits: String, weights: List<Int>): Int {
        val sum = weights.indices.sumOf { digits[it].digitToInt() * weights[it] }
        val remainder = sum % 11
        return if (remainder < 2) 0 else 11 - remainder
    }

    private fun String.onlyDigits(): String = filter { it.isDigit() }
}
