package br.com.sisgfin.financial.money

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `test arithmetic operations`() {
        val m1 = Money.fromDouble(10.50)
        val m2 = Money.fromDouble(5.25)
        
        assertEquals("10.50", m1.toString())
        assertEquals("15.75", (m1 + m2).toString())
        assertEquals("5.25", (m1 - m2).toString())
        assertEquals("21.00", (m1 * 2.0).toString())
        assertEquals("2.10", (m1 / 5.0).toString())
    }

    @Test
    fun `test precision and rounding`() {
        val m1 = Money.fromDouble(10.0)
        val result = m1 / 3.0 // 3.33333333...
        
        // No toString() deve arredondar para 2 casas
        assertEquals("3.33", result.toString())
        
        // Internamente deve manter mais precisão
        assertTrue(result.value > BigDecimal("3.33"))
    }

    @Test
    fun `test percentage`() {
        val m = Money.fromDouble(200.0)
        assertEquals("10.00", m.percentage(5.0).toString())
        assertEquals("33.33", m.percentage(16.666).toString())
    }

    @Test
    fun `test formatting`() {
        val m = Money.fromDouble(1250.45)
        // O teste de format depende do locale do sistema, então vamos verificar se contém os números
        val formatted = MoneyFormatter.format(m)
        // No locale pt-BR esperado: R$ 1.250,45
        assertTrue(formatted.contains("1.250,45") || formatted.contains("1,250.45"))
        
        val negative = Money.fromDouble(-500.0)
        // Normalizando espaços para evitar erros com non-breaking space
        val accounting = MoneyFormatter.formatAccounting(negative).replace('\u00A0', ' ').replace('\u202F', ' ')
        assertEquals("(R$ 500,00)", accounting)
    }

    @Test
    fun `test compact formatting`() {
        val kVal = MoneyFormatter.formatCompact(Money.fromDouble(1200.0)).replace('\u00A0', ' ').replace('\u202F', ' ')
        val mVal = MoneyFormatter.formatCompact(Money.fromDouble(2500000.0)).replace('\u00A0', ' ').replace('\u202F', ' ')
        val normalVal = MoneyFormatter.formatCompact(Money.fromDouble(500.0)).replace('\u00A0', ' ').replace('\u202F', ' ')
        
        assertEquals("R$ 1,2K", kVal)
        assertEquals("R$ 2,5M", mVal)
        assertEquals("R$ 500,00", normalVal)
    }

    @Test
    fun `test sum extension`() {
        val list = listOf(Money.fromDouble(10.0), Money.fromDouble(20.0), Money.fromDouble(30.0))
        assertEquals("60.00", list.sum().toString())
    }
}
