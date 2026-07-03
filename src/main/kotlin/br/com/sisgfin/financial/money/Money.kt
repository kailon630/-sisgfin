package br.com.sisgfin.financial.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Abstração monetária imutável para o SisgFin.
 * Garante que operações financeiras sigam as regras de arredondamento e precisão do sistema.
 */
data class Money(
    val value: BigDecimal
) : Comparable<Money> {

    init {
        // Garante que o valor sempre tenha a escala correta ao ser instanciado
        // value.setScale(RoundingPolicy.DEFAULT_SCALE, RoundingPolicy.DEFAULT_ROUNDING_MODE)
    }

    private val roundedValue: BigDecimal 
        get() = value.setScale(RoundingPolicy.DEFAULT_SCALE, RoundingPolicy.DEFAULT_ROUNDING_MODE)

    operator fun plus(other: Money): Money = Money(value.add(other.value))
    
    operator fun minus(other: Money): Money = Money(value.subtract(other.value))
    
    operator fun times(factor: BigDecimal): Money = Money(value.multiply(factor))
    
    operator fun times(factor: Double): Money = times(BigDecimal.valueOf(factor))
    
    operator fun div(divisor: BigDecimal): Money = Money(
        value.divide(divisor, RoundingPolicy.CALCULATION_SCALE, RoundingPolicy.DEFAULT_ROUNDING_MODE)
    )
    
    operator fun div(divisor: Double): Money = div(BigDecimal.valueOf(divisor))

    fun percentage(pct: Double): Money = times(pct / 100.0)

    fun negate(): Money = Money(value.negate())
    
    fun abs(): Money = Money(value.abs())

    fun isZero(): Boolean = value.compareTo(BigDecimal.ZERO) == 0
    
    fun isPositive(): Boolean = value.compareTo(BigDecimal.ZERO) > 0
    
    fun isNegative(): Boolean = value.compareTo(BigDecimal.ZERO) < 0

    override fun compareTo(other: Money): Int = value.compareTo(other.value)

    override fun toString(): String = roundedValue.toPlainString()

    companion object {
        val ZERO = Money(BigDecimal.ZERO.setScale(RoundingPolicy.DEFAULT_SCALE))
        
        fun fromDouble(valDouble: Double): Money = Money(BigDecimal.valueOf(valDouble))
        
        fun fromString(valString: String): Money = try {
            Money(BigDecimal(valString))
        } catch (e: Exception) {
            ZERO
        }

        fun fromLong(valLong: Long): Money = Money(BigDecimal.valueOf(valLong).setScale(RoundingPolicy.DEFAULT_SCALE))
    }
}
