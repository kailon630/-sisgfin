package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertTrue

class TransactionValidatorTest {

    private val base = Transaction(
        id = 0,
        type = TransactionType.EXPENSE,
        status = TransactionStatus.PENDING,
        description = "Pagamento fornecedor",
        amount = Money.fromDouble(500.0),
        issueDate = LocalDateTime.of(2026, 1, 10, 0, 0),
        dueDate = LocalDateTime.of(2026, 1, 20, 0, 0),
        accountId = 1
    )

    // ── RN-16: dataPagamento >= dataEmissão ───────────────────────────────

    @Test
    fun `RN-16 pagamento na mesma data de emissao e valido`() {
        val tx = base.copy(
            status = TransactionStatus.PAID,
            paymentDate = LocalDateTime.of(2026, 1, 10, 0, 0),
            paidAmount = Money.fromDouble(500.0)
        )
        assertDoesNotThrow { TransactionValidator.validate(tx) }
    }

    @Test
    fun `RN-16 pagamento apos emissao e valido`() {
        val tx = base.copy(
            status = TransactionStatus.PAID,
            paymentDate = LocalDateTime.of(2026, 1, 15, 0, 0),
            paidAmount = Money.fromDouble(500.0)
        )
        assertDoesNotThrow { TransactionValidator.validate(tx) }
    }

    @Test
    fun `RN-16 pagamento anterior a emissao e invalido em PAID`() {
        val tx = base.copy(
            status = TransactionStatus.PAID,
            paymentDate = LocalDateTime.of(2026, 1, 9, 0, 0),
            paidAmount = Money.fromDouble(500.0)
        )
        val ex = assertThrows<IllegalArgumentException> { TransactionValidator.validate(tx) }
        assertTrue(ex.message!!.contains("anterior à data de emissão"))
    }

    @Test
    fun `RN-16 pagamento anterior a emissao e invalido em PARTIAL`() {
        val tx = base.copy(
            status = TransactionStatus.PARTIAL,
            paymentDate = LocalDateTime.of(2026, 1, 5, 0, 0),
            paidAmount = Money.fromDouble(200.0)
        )
        val ex = assertThrows<IllegalArgumentException> { TransactionValidator.validate(tx) }
        assertTrue(ex.message!!.contains("anterior à data de emissão"))
    }

    @Test
    fun `RN-16 validatePayment lanca quando pagamento anterior a emissao`() {
        val issueDate = LocalDateTime.of(2026, 1, 10, 0, 0)
        val paymentDate = LocalDateTime.of(2026, 1, 9, 0, 0)
        val ex = assertThrows<IllegalArgumentException> {
            TransactionValidator.validatePayment(
                total = Money.fromDouble(500.0),
                paidAmount = Money.fromDouble(500.0),
                paymentDate = paymentDate,
                issueDate = issueDate
            )
        }
        assertTrue(ex.message!!.contains("anterior à data de emissão"))
    }

    @Test
    fun `RN-16 validatePayment aceita pagamento na data de emissao`() {
        val date = LocalDateTime.of(2026, 1, 10, 0, 0)
        assertDoesNotThrow {
            TransactionValidator.validatePayment(
                total = Money.fromDouble(500.0),
                paidAmount = Money.fromDouble(500.0),
                paymentDate = date,
                issueDate = date
            )
        }
    }

    // ── RN-08: aviso fora do período do convênio (não bloqueia) ──────────

    @Test
    fun `RN-08 dueDate dentro do periodo retorna null`() {
        val result = TransactionValidator.checkProjectPeriod(
            dueDate = LocalDateTime.of(2026, 3, 15, 0, 0),
            startDate = LocalDateTime.of(2026, 1, 1, 0, 0),
            endDate = LocalDateTime.of(2026, 12, 31, 0, 0)
        )
        assertTrue(result == null)
    }

    @Test
    fun `RN-08 dueDate anterior ao inicio do projeto retorna aviso`() {
        val result = TransactionValidator.checkProjectPeriod(
            dueDate = LocalDateTime.of(2025, 12, 31, 0, 0),
            startDate = LocalDateTime.of(2026, 1, 1, 0, 0),
            endDate = LocalDateTime.of(2026, 12, 31, 0, 0)
        )
        assertTrue(result != null)
        assertTrue(result!!.contains("Aviso"))
        assertTrue(result.contains("2026-01-01"))
    }

    @Test
    fun `RN-08 dueDate posterior ao fim do projeto retorna aviso`() {
        val result = TransactionValidator.checkProjectPeriod(
            dueDate = LocalDateTime.of(2027, 1, 5, 0, 0),
            startDate = LocalDateTime.of(2026, 1, 1, 0, 0),
            endDate = LocalDateTime.of(2026, 12, 31, 0, 0)
        )
        assertTrue(result != null)
        assertTrue(result!!.contains("Aviso"))
        assertTrue(result.contains("2026-12-31"))
    }

    @Test
    fun `RN-08 dueDate igual ao inicio retorna null`() {
        val start = LocalDateTime.of(2026, 1, 1, 0, 0)
        val result = TransactionValidator.checkProjectPeriod(
            dueDate = start,
            startDate = start,
            endDate = LocalDateTime.of(2026, 12, 31, 0, 0)
        )
        assertTrue(result == null)
    }

    @Test
    fun `RN-08 dueDate igual ao fim retorna null`() {
        val end = LocalDateTime.of(2026, 12, 31, 0, 0)
        val result = TransactionValidator.checkProjectPeriod(
            dueDate = end,
            startDate = LocalDateTime.of(2026, 1, 1, 0, 0),
            endDate = end
        )
        assertTrue(result == null)
    }

    @Test
    fun `RN-08 sem periodo definido retorna null`() {
        val result = TransactionValidator.checkProjectPeriod(
            dueDate = LocalDateTime.of(2026, 6, 1, 0, 0),
            startDate = null,
            endDate = null
        )
        assertTrue(result == null)
    }

    @Test
    fun `RN-08 com apenas startDate definido fora do range retorna aviso`() {
        val result = TransactionValidator.checkProjectPeriod(
            dueDate = LocalDateTime.of(2025, 12, 1, 0, 0),
            startDate = LocalDateTime.of(2026, 1, 1, 0, 0),
            endDate = null
        )
        assertTrue(result != null)
    }

    // ── Validações existentes não regredidas ──────────────────────────────

    @Test
    fun `descricao em branco gera erro`() {
        val tx = base.copy(description = "  ")
        val ex = assertThrows<IllegalArgumentException> { TransactionValidator.validate(tx) }
        assertTrue(ex.message!!.contains("Descrição"))
    }

    @Test
    fun `valor zero gera erro`() {
        val tx = base.copy(amount = Money.fromDouble(0.0))
        val ex = assertThrows<IllegalArgumentException> { TransactionValidator.validate(tx) }
        assertTrue(ex.message!!.contains("Valor"))
    }

    @Test
    fun `PAID sem paymentDate gera erro`() {
        val tx = base.copy(
            status = TransactionStatus.PAID,
            paymentDate = null,
            paidAmount = Money.fromDouble(500.0)
        )
        val ex = assertThrows<IllegalArgumentException> { TransactionValidator.validate(tx) }
        assertTrue(ex.message!!.contains("data de pagamento"))
    }

    @Test
    fun `PARTIAL com paidAmount maior que total gera erro`() {
        val tx = base.copy(
            status = TransactionStatus.PARTIAL,
            paymentDate = LocalDateTime.of(2026, 1, 15, 0, 0),
            paidAmount = Money.fromDouble(600.0)
        )
        val ex = assertThrows<IllegalArgumentException> { TransactionValidator.validate(tx) }
        assertTrue(ex.message!!.contains("menor que o valor total"))
    }
}
