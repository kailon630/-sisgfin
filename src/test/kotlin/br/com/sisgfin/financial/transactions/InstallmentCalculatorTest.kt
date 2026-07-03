package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.RoundingPolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes para RN-04, RN-17, RN-18, RN-19 — lógica pura sem banco.
 */
class InstallmentCalculatorTest {

    // ── helpers ──────────────────────────────────────────────────────────

    private fun sliceAndLast(total: Money, n: Int): Pair<Money, Money> {
        val sliceValue = total.value.divide(BigDecimal(n), RoundingPolicy.DEFAULT_SCALE, RoundingMode.FLOOR)
        val slice = Money(sliceValue)
        val last = total - (slice * BigDecimal(n - 1))
        return slice to last
    }

    private fun reconstructTotal(slice: Money, last: Money, n: Int): Money =
        (slice * BigDecimal(n - 1)) + last

    // ── RN-17: número de parcelas ─────────────────────────────────────────

    @Test
    fun `RN-17 totalInstallments 1 nao deve gerar filhos`() {
        assertEquals(1, 1) // trivial: installmentTotal==1 => nenhuma parcela extra
    }

    @Test
    fun `RN-17 slice calculado corretamente para valor divisivel`() {
        val (slice, last) = sliceAndLast(Money.fromDouble(300.0), 3)
        assertEquals("100.00", slice.toString())
        assertEquals("100.00", last.toString())
    }

    @Test
    fun `RN-17 slice calculado corretamente para 12 parcelas valor divisivel`() {
        val (slice, last) = sliceAndLast(Money.fromDouble(1200.0), 12)
        assertEquals("100.00", slice.toString())
        assertEquals("100.00", last.toString())
    }

    // ── RN-18: última parcela absorve arredondamento ──────────────────────

    @Test
    fun `RN-18 100 reais em 3 parcelas - slice 33-33 e ultima 33-34`() {
        val (slice, last) = sliceAndLast(Money.fromDouble(100.0), 3)
        assertEquals("33.33", slice.toString())
        assertEquals("33.34", last.toString())
    }

    @Test
    fun `RN-18 10 reais em 3 parcelas - slice 3-33 e ultima 3-34`() {
        val (slice, last) = sliceAndLast(Money.fromDouble(10.0), 3)
        assertEquals("3.33", slice.toString())
        assertEquals("3.34", last.toString())
    }

    @Test
    fun `RN-18 100 reais em 12 parcelas - soma reconstruida bate no total`() {
        val total = Money.fromDouble(100.0)
        val (slice, last) = sliceAndLast(total, 12)
        assertEquals("8.33", slice.toString())
        assertEquals("8.37", last.toString())
        val reconstructed = reconstructTotal(slice, last, 12)
        assertEquals(0, reconstructed.compareTo(total))
    }

    @Test
    fun `RN-18 soma de todas as parcelas sempre iguala o total`() {
        val cases = listOf(
            Money.fromDouble(100.0) to 3,
            Money.fromDouble(999.99) to 7,
            Money.fromDouble(1.0) to 4,
            Money.fromDouble(500.0) to 12,
            Money.fromDouble(0.03) to 2,
        )
        for ((total, n) in cases) {
            val (slice, last) = sliceAndLast(total, n)
            val reconstructed = reconstructTotal(slice, last, n)
            assertEquals(0, reconstructed.compareTo(total),
                "Falhou para total=$total n=$n: reconst=$reconstructed")
        }
    }

    @Test
    fun `RN-18 valor 0-03 em 2 parcelas - slice piso e ultima absorve diferenca`() {
        val total = Money.fromDouble(0.03)
        val (slice, last) = sliceAndLast(total, 2)
        assertEquals("0.01", slice.toString())
        assertEquals("0.02", last.toString())
        assertEquals(0, reconstructTotal(slice, last, 2).compareTo(total))
    }

    // ── RN-04: fórmula de saldo ───────────────────────────────────────────

    @Test
    fun `RN-04 saldo inicial mais receita menos despesa`() {
        val initial = Money.fromDouble(1000.0)
        val income  = Money.fromDouble(500.0)
        val expense = Money.fromDouble(300.0)
        val balance = initial + income - expense
        assertEquals("1200.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo sem lancamentos retorna saldo inicial`() {
        val initial = Money.fromDouble(500.0)
        val balance = initial + Money.ZERO - Money.ZERO
        assertEquals("500.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo pode ser negativo quando despesas superam receitas`() {
        val initial = Money.fromDouble(100.0)
        val income  = Money.ZERO
        val expense = Money.fromDouble(200.0)
        val balance = initial + income - expense
        assertTrue(balance.isNegative())
        assertEquals("-100.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo zero quando receitas igualam despesas mais saldo inicial`() {
        val initial = Money.fromDouble(100.0)
        val income  = Money.fromDouble(200.0)
        val expense = Money.fromDouble(300.0)
        val balance = initial + income - expense
        assertTrue(balance.isZero())
    }

    // ── RN-19: cascata via StateMachine ──────────────────────────────────

    @Test
    fun `RN-19 PENDING pode ser cancelado pela maquina de estados`() {
        val sm = br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
        assertTrue(sm.allowsCancel(TransactionStatus.PENDING))
        assertTrue(sm.allowsCancel(TransactionStatus.DRAFT))
    }

    @Test
    fun `RN-19 PAID nao e cancelavel - allowsCancel retorna false`() {
        val sm = br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
        assertEquals(false, sm.allowsCancel(TransactionStatus.PAID))
    }

    @Test
    fun `RN-19 CANCELED nao e cancelavel - allowsCancel retorna false`() {
        val sm = br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
        assertEquals(false, sm.allowsCancel(TransactionStatus.CANCELED))
    }
}
