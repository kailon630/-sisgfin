package br.com.sisgfin.financial.transactions

import br.com.sisgfin.AuditRepository
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C-14 / Bloco 1 — caracterização de TransactionService.recordPayment().
 *
 * Todos os testes exercitam o service REAL com repositórios mockados.
 * Os testes P0-5 documentavam comportamento incorreto do calculateBalance.
 * calculateBalance foi corrigido no M4 (usa cashEffective das baixas).
 * As asserções sobre amount=1000 permanecem corretas: o campo face value
 * não muda — o que mudou foi o que calculateBalance usa para o saldo.
 */
class RecordPaymentIntegrationTest {

    private val paymentDate: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        withPermission: Boolean = true,
        paymentRepo: TransactionPaymentRepository = mockk(relaxed = true)
    ): TransactionService {
        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns withPermission
        return TransactionService(
            repository           = repo,
            accountRepository    = mockk(relaxed = true),
            supplierRepository   = mockk(relaxed = true),
            costCenterRepository = mockk(relaxed = true),
            auditRepository      = mockk(relaxed = true),
            timelineRepository   = mockk(relaxed = true),
            sessionManager       = session,
            ledgerService        = mockk(relaxed = true),
            employeeRepository   = mockk(relaxed = true),
            paymentRepository    = paymentRepo
        )
    }

    private fun expense(
        id: Int = 1,
        amount: String = "1000.00",
        status: TransactionStatus = TransactionStatus.PENDING,
        paidAmount: String? = null,
        interestAmount: String? = null,
        fineAmount: String? = null
    ) = Transaction(
        id             = id,
        type           = TransactionType.EXPENSE,
        status         = status,
        description    = "Despesa teste",
        amount         = Money.fromString(amount),
        issueDate      = LocalDateTime.of(2026, 8, 1, 0, 0),
        dueDate        = LocalDateTime.of(2026, 8, 31, 0, 0),
        accountId      = 1,
        paidAmount     = paidAmount?.let { Money.fromString(it) },
        interestAmount = interestAmount?.let { Money.fromString(it) },
        fineAmount     = fineAmount?.let { Money.fromString(it) }
    )

    // ── pagamento integral ────────────────────────────────────────────────────

    @Test
    fun `recordPayment integral EXPENSE PENDING - status vira PAID paidAmount e paymentDate gravados`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))

        val updated = slot.captured
        assertEquals(TransactionStatus.PAID, updated.status)
        assertEquals(0, Money.fromString("1000.00").compareTo(updated.paidAmount!!))
        assertEquals(paymentDate, updated.paymentDate)
    }

    @Test
    fun `recordPayment integral INCOME PENDING - status vira PAID`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense().copy(type = TransactionType.INCOME)
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))

        assertEquals(TransactionStatus.PAID, slot.captured.status)
    }

    // ── pagamento parcial ─────────────────────────────────────────────────────

    @Test
    fun `recordPayment parcial - status vira PARTIAL paidAmount e paymentDate gravados`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("400.00"))

        val updated = slot.captured
        assertEquals(TransactionStatus.PARTIAL, updated.status)
        assertEquals(0, Money.fromString("400.00").compareTo(updated.paidAmount!!))
        assertEquals(paymentDate, updated.paymentDate)
    }

    // ── OVERDUE ───────────────────────────────────────────────────────────────

    @Test
    fun `recordPayment integral de OVERDUE - status vira PAID`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense(status = TransactionStatus.OVERDUE)
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))

        assertEquals(TransactionStatus.PAID, slot.captured.status)
    }

    // ── segunda baixa em PARTIAL ──────────────────────────────────────────────

    @Test
    fun `segunda baixa em PARTIAL quita titulo - status vira PAID e paidAmount acumulado`() {
        // PARTIAL: 400 amortizados, outstanding = 600
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense(status = TransactionStatus.PARTIAL, paidAmount = "400.00")
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("600.00"))

        val updated = slot.captured
        assertEquals(TransactionStatus.PAID, updated.status)
        assertEquals(0, Money.fromString("1000.00").compareTo(updated.paidAmount!!))
    }

    // ── validações de pré-condição ────────────────────────────────────────────

    @Test
    fun `recordPayment em titulo PAID lanca IllegalStateException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense(status = TransactionStatus.PAID, paidAmount = "1000.00")
        val service = makeService(repo)

        assertThrows<IllegalStateException> {
            service.recordPayment(1, paymentDate, Money.fromString("1000.00"))
        }
    }

    @Test
    fun `recordPayment em titulo CANCELED lanca IllegalStateException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense(status = TransactionStatus.CANCELED)
        val service = makeService(repo)

        assertThrows<IllegalStateException> {
            service.recordPayment(1, paymentDate, Money.fromString("1000.00"))
        }
    }

    @Test
    fun `recordPayment com valor acima do outstanding lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val service = makeService(repo)

        assertThrows<IllegalArgumentException> {
            service.recordPayment(1, paymentDate, Money.fromString("1001.00"))
        }
    }

    @Test
    fun `recordPayment com valor zero lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val service = makeService(repo)

        assertThrows<IllegalArgumentException> {
            service.recordPayment(1, paymentDate, Money.ZERO)
        }
    }

    @Test
    fun `recordPayment com data de pagamento anterior a emissao lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val service = makeService(repo)

        assertThrows<IllegalArgumentException> {
            service.recordPayment(1, LocalDateTime.of(2025, 1, 1, 0, 0), Money.fromString("1000.00"))
        }
    }

    @Test
    fun `recordPayment sem permissao lanca SecurityException`() {
        val service = makeService(withPermission = false)

        assertThrows<SecurityException> {
            service.recordPayment(1, paymentDate, Money.fromString("1000.00"))
        }
    }

    @Test
    fun `recordPayment para transacao inexistente lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(99) } returns null
        val service = makeService(repo)

        val ex = assertThrows<IllegalArgumentException> {
            service.recordPayment(99, paymentDate, Money.fromString("500.00"))
        }
        assertTrue(ex.message!!.contains("não encontrada"))
    }

    // ── encargos (comportamento de armazenamento) ─────────────────────────────

    @Test
    fun `recordPayment com juros - paidAmount armazena principal mais juros e interestAmount isolado`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"),
            interestAmount = Money.fromString("50.00"))

        val updated = slot.captured
        assertEquals(0, Money.fromString("1050.00").compareTo(updated.paidAmount!!))
        assertEquals(0, Money.fromString("50.00").compareTo(updated.interestAmount!!))
        assertEquals(0, Money.fromString("1000.00").compareTo(updated.amount))
    }

    @Test
    fun `recordPayment com multa - paidAmount armazena principal mais multa e fineAmount isolado`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"),
            fineAmount = Money.fromString("30.00"))

        val updated = slot.captured
        assertEquals(0, Money.fromString("1030.00").compareTo(updated.paidAmount!!))
        assertEquals(0, Money.fromString("30.00").compareTo(updated.fineAmount!!))
    }

    @Test
    fun `recordPayment com juros e multa - paidAmount acumula os tres componentes`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"),
            interestAmount = Money.fromString("50.00"),
            fineAmount     = Money.fromString("20.00"))

        val updated = slot.captured
        // paidAmount = 1000 + 50 + 20 = 1070
        assertEquals(0, Money.fromString("1070.00").compareTo(updated.paidAmount!!))
        assertEquals(0, Money.fromString("50.00").compareTo(updated.interestAmount!!))
        assertEquals(0, Money.fromString("20.00").compareTo(updated.fineAmount!!))
    }

    // ── CARACTERIZAÇÃO — comportamentos incorretos conhecidos ─────────────────

    @Test
    fun `P0-5 recordPayment com juros grava paidAmount correto e amount permanece face value`() {
        // P0-5 (corrigido no M4): amount=1000 é o face value e não muda — correto.
        // paidAmount=1050 é o caixa efetivo (principal + juros) — correto.
        // calculateBalance agora usa cashEffective das baixas (M4), portanto
        // R$50 de juros SÃO visíveis no saldo. Nenhum valor errado neste teste.
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"),
            interestAmount = Money.fromString("50.00"))

        val updated = slot.captured
        // paidAmount = principal + juros = 1050 (caixa efetivo, fonte das baixas)
        assertEquals(0, Money.fromString("1050.00").compareTo(updated.paidAmount!!))
        // amount permanece o face value do título (1000) — imutável por design
        assertEquals(0, Money.fromString("1000.00").compareTo(updated.amount))
    }

    @Test
    fun `P0-5 ao quitar PARTIAL paidAmount acumula corretamente e amount permanece face value`() {
        // P0-5 (corrigido no M4): a assimetria PARTIAL→PAID foi eliminada.
        // calculateBalance usa cashEffective das baixas, não amount nem paidAmount do título.
        // Cada baixa contribui com seu cashEffective — a transição de status não afeta o saldo.
        //
        // Estado pós-1ª baixa: 300 principal + 50 juros → PARTIAL
        //   paidAmount = 350, interestAmount = 50, amount = 1000 (face value)
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense(
            status         = TransactionStatus.PARTIAL,
            paidAmount     = "350.00",
            interestAmount = "50.00"
        )
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        // 2ª baixa: 700 principal restante → PAID
        service.recordPayment(1, paymentDate, Money.fromString("700.00"))

        val updated = slot.captured
        assertEquals(TransactionStatus.PAID, updated.status)
        // paidAmount acumulado: 350 (1ª baixa) + 700 (2ª baixa) = 1050
        assertEquals(0, Money.fromString("1050.00").compareTo(updated.paidAmount!!))
        // amount permanece o face value do título — imutável por design
        assertEquals(0, Money.fromString("1000.00").compareTo(updated.amount))
    }

    // ── CARACTERIZAÇÃO — cascade de transferência ─────────────────────────────

    @Test
    fun `CARACTERIZACAO C-01 recordPayment em perna TRANSFER nao cascateia para a irma`() {
        // CARACTERIZAÇÃO — comportamento INCORRETO, mantido de propósito.
        // C-01: quitar a perna de origem de uma transferência NÃO propaga o status
        // PAID para a perna de destino. A perna destino fica PENDING indefinidamente.
        // Decisão D5 pendente — ver C00_C01_TRANSFERENCIA_E_SPEC.md seção B.4.
        // Será resolvido em C-09. Quando este teste QUEBRAR, é sinal de sucesso:
        // inverta a asserção de verify(exactly = 0) para verify(exactly = 1).
        val sourceTx = expense(id = 1).copy(type = TransactionType.TRANSFER)
        val sisterTx = expense(id = 2).copy(type = TransactionType.TRANSFER, accountId = 2)
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns sourceTx
        every { repo.findById(2) } returns sisterTx
        val slot = slot<Transaction>()
        every { repo.updateWithPayment(capture(slot), any()) } returns true
        val service = makeService(repo)

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))

        // source foi quitada: exatamente um updateWithPayment
        verify(exactly = 1) { repo.updateWithPayment(any(), any()) }
        assertEquals(TransactionStatus.PAID, slot.captured.status)
        // irmã NUNCA foi consultada nem atualizada — ausência de cascade
        verify(exactly = 0) { repo.findById(2) }
    }
}
