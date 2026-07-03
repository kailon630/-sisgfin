package br.com.sisgfin.financial.money

import java.math.BigDecimal
import java.math.RoundingMode

object RoundingPolicy {
    val DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN
    const val DEFAULT_SCALE = 2
    
    // Precisão interna para cálculos intermediários (evita perda em divisões sucessivas)
    const val CALCULATION_SCALE = 8
}
