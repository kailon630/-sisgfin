package br.com.sisgfin.financial.money

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

object MoneyFormatter {
    private val ptBrLocale = Locale("pt", "BR")
    
    private val standardFormat = NumberFormat.getCurrencyInstance(ptBrLocale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /**
     * Formata padrão: R$ 1.250,45
     */
    fun format(money: Money): String {
        return standardFormat.format(money.value)
    }

    /**
     * Formata contábil: Negativos entre parênteses (R$ 500,00)
     */
    fun formatAccounting(money: Money): String {
        val formatted = format(money.abs())
        return if (money.isNegative()) "($formatted)" else formatted
    }

    /**
     * Formata compacto: R$ 1,2K, R$ 2,4M
     */
    fun formatCompact(money: Money): String {
        val valDouble = money.value.toDouble()
        val absVal = Math.abs(valDouble)
        val symbol = "R$ "
        
        return when {
            absVal >= 1_000_000 -> symbol + "%.1fM".format(ptBrLocale, valDouble / 1_000_000.0)
            absVal >= 1_000 -> symbol + "%.1fK".format(ptBrLocale, valDouble / 1_000.0)
            else -> format(money)
        }
    }
}
