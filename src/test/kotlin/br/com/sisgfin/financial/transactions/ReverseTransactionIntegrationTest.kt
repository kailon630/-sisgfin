package br.com.sisgfin.financial.transactions

import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * C-14 / Bloco 3 — caracterização de TransactionService.reverseTransaction().
 *
 * Exercita o service REAL com repositórios mockados.
 * Cobre: guardas de permissão, justificativa, elegibilidade de status/tipo,
 * duplicidade de estorno, e estrutura do lançamento REVERSAL criado.
 */
class ReverseTransactionIntegrationTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        withPermission: Boolean = true
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
            paymentRepository    = mockk(relaxed = true)
        )
    }

    private fun paidTx(
        id: Int = 1,
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "1000.00"
    ) = Transaction(
        id          = id,
        type        = type,
        status      = TransactionStatus.PAID,
        description = "Despesa quitada",
        amount      = Money.fromString(amount),
        issueDate   = LocalDateTime.of(2026, 8, 1, 0, 0),
        dueDate     = LocalDateTime.of(2026, 8, 10, 0, 0),
        paymentDate = LocalDateTime.of(2026, 8, 10, 0, 0),
        paidAmount  = Money.fromString(amount),
        accountId   = 5
    )

    // ── guardas de entrada ────────────────────────────────────────────────────

    @Test
    fun `reverseTransaction sem permissao lanca SecurityException`() {
        val service = makeService(withPermission = false)

        assertThrows<SecurityException> {
            service.reverseTransaction(1, "Pagamento duplicado")
        }
    }

    @Test
    fun `reverseTransaction com justificativa em branco lanca IllegalArgumentException`() {
        val service = makeService()

        assertThrows<IllegalArgumentException> {
            service.reverseTransaction(1, "   ")
        }
    }

    @Test
    fun `reverseTransaction com justificativa vazia lanca IllegalArgumentException`() {
        val service = makeService()

        assertThrows<IllegalArgumentException> {
            service.reverseTransaction(1, "")
        }
    }

    @Test
    fun `reverseTransaction com ID inexistente lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(99) } returns null
        val service = makeService(repo)

        val ex = assertThrows<IllegalArgumentException> {
            service.reverseTransaction(99, "NF cancelada")
        }
        assertTrue(ex.message!!.contains("não encontrado"))
    }

    // ── elegibilidade de status ───────────────────────────────────────────────

    @Test
    fun `reverseTransaction de PENDING lanca IllegalStateException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx().copy(status = TransactionStatus.PENDING, paymentDate = null, paidAmount = null)
        val service = makeService(repo)

        val ex = assertThrows<IllegalStateException> {
            service.reverseTransaction(1, "NF cancelada")
        }
        assertTrue(ex.message!!.contains("Pago"))
    }

    @Test
    fun `reverseTransaction de PARTIAL lanca IllegalStateException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx().copy(status = TransactionStatus.PARTIAL)
        val service = makeService(repo)

        assertThrows<IllegalStateException> {
            service.reverseTransaction(1, "NF cancelada")
        }
    }

    @Test
    fun `reverseTransaction de CANCELED lanca IllegalStateException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx().copy(status = TransactionStatus.CANCELED)
        val service = makeService(repo)

        assertThrows<IllegalStateException> {
            service.reverseTransaction(1, "NF cancelada")
        }
    }

    // ── elegibilidade de tipo ─────────────────────────────────────────────────

    @Test
    fun `reverseTransaction de REVERSAL lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx(type = TransactionType.REVERSAL)
        val service = makeService(repo)

        val ex = assertThrows<IllegalArgumentException> {
            service.reverseTransaction(1, "NF cancelada")
        }
        assertTrue(ex.message!!.contains("estorno"))
    }

    @Test
    fun `reverseTransaction de TRANSFER lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx(type = TransactionType.TRANSFER)
        val service = makeService(repo)

        val ex = assertThrows<IllegalArgumentException> {
            service.reverseTransaction(1, "NF cancelada")
        }
        assertTrue(ex.message!!.contains("Transferências"))
    }

    // ── duplicidade de estorno ────────────────────────────────────────────────

    @Test
    fun `reverseTransaction quando ja existe estorno lanca IllegalStateException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx()
        every { repo.hasReversal(1) } returns true
        val service = makeService(repo)

        val ex = assertThrows<IllegalStateException> {
            service.reverseTransaction(1, "NF cancelada")
        }
        assertTrue(ex.message!!.contains("já possui"))
    }

    // ── lançamento REVERSAL criado ────────────────────────────────────────────

    @Test
    fun `reverseTransaction de EXPENSE cria REVERSAL com reversedType EXPENSE e status PAID`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx(type = TransactionType.EXPENSE)
        every { repo.hasReversal(1) } returns false
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 99
        val service = makeService(repo)

        val reversalId = service.reverseTransaction(1, "Pagamento duplicado")

        assertEquals(99, reversalId)
        val reversal = slot.captured
        assertEquals(TransactionType.REVERSAL, reversal.type)
        assertEquals(TransactionStatus.PAID, reversal.status)
        assertEquals(TransactionType.EXPENSE, reversal.reversedType)
        assertEquals(1, reversal.parentTransactionId)
        assertEquals(5, reversal.accountId)
        assertEquals(0, Money.fromString("1000.00").compareTo(reversal.amount))
    }

    @Test
    fun `reverseTransaction de INCOME cria REVERSAL com reversedType INCOME`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx(type = TransactionType.INCOME)
        every { repo.hasReversal(1) } returns false
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 99
        val service = makeService(repo)

        service.reverseTransaction(1, "Devolução")

        assertEquals(TransactionType.INCOME, slot.captured.reversedType)
    }

    @Test
    fun `reverseTransaction de ADJUSTMENT cria REVERSAL com reversedType ADJUSTMENT`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx(type = TransactionType.ADJUSTMENT)
        every { repo.hasReversal(1) } returns false
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 99
        val service = makeService(repo)

        service.reverseTransaction(1, "Ajuste incorreto")

        assertEquals(TransactionType.ADJUSTMENT, slot.captured.reversedType)
    }

    @Test
    fun `reverseTransaction REVERSAL herda amount do original`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx(amount = "2500.75")
        every { repo.hasReversal(1) } returns false
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 99
        val service = makeService(repo)

        service.reverseTransaction(1, "NF cancelada")

        assertEquals(0, Money.fromString("2500.75").compareTo(slot.captured.amount))
    }

    @Test
    fun `reverseTransaction REVERSAL description contem prefixo Estorno`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx()
        every { repo.hasReversal(1) } returns false
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 99
        val service = makeService(repo)

        service.reverseTransaction(1, "Pagamento duplicado")

        assertTrue(slot.captured.description.startsWith("Estorno:"))
    }

    @Test
    fun `reverseTransaction REVERSAL tem paymentDate e paidAmount preenchidos`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns paidTx()
        every { repo.hasReversal(1) } returns false
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 99
        val service = makeService(repo)

        service.reverseTransaction(1, "NF cancelada")

        val reversal = slot.captured
        assertNotNull(reversal.paymentDate)
        assertNotNull(reversal.paidAmount)
        assertEquals(0, reversal.amount.compareTo(reversal.paidAmount!!))
    }
}
