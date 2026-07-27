package br.com.sisgfin.financial.money

import java.math.BigDecimal
import java.math.RoundingMode

fun Double.toMoney(): Money = Money.fromDouble(this)
fun String.toMoney(): Money = Money.fromString(this)
fun Long.toMoney(): Money = Money.fromLong(this)
fun Int.toMoney(): Money = this.toLong().toMoney()
fun BigDecimal.toMoney(): Money = Money(this)

/**
 * Converte Money para string de centavos (dígitos puros) usada pelo WsMoneyField.
 * Ex: Money(1500.45) → "150045". Money(0) → "".
 */
fun Money.toCentsStr(): String {
    if (isZero()) return ""
    return value.abs()
        .multiply(BigDecimal("100"))
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()
        .toString()
}

/**
 * Converte string de centavos devolvida pelo WsMoneyField de volta para Money.
 * Ex: "150045" → Money(1500.45). "" ou null → Money.ZERO.
 */
fun String.centsToMoney(): Money {
    val cents = trim().filter { it.isDigit() }.toLongOrNull() ?: return Money.ZERO
    return Money(BigDecimal(cents).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP))
}

// List Helpers
fun Iterable<Money>.sum(): Money {
    var total = Money.ZERO
    for (m in this) {
        total += m
    }
    return total
}
