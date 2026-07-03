package br.com.sisgfin.financial.money

import java.math.BigDecimal

fun Double.toMoney(): Money = Money.fromDouble(this)
fun String.toMoney(): Money = Money.fromString(this)
fun Long.toMoney(): Money = Money.fromLong(this)
fun Int.toMoney(): Money = this.toLong().toMoney()
fun BigDecimal.toMoney(): Money = Money(this)

// List Helpers
fun Iterable<Money>.sum(): Money {
    var total = Money.ZERO
    for (m in this) {
        total += m
    }
    return total
}
