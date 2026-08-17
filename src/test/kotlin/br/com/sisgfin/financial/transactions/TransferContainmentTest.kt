package br.com.sisgfin.financial.transactions

import br.com.sisgfin.AuditRepository
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.FinancialAccount
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
 * Testes de contenção de transferência que exercitam TransactionService real
 * com repositórios mockados.
 *
 * Abordagem: mockk concreto — a única forma de verificar que deactivate() não
 * é chamado quando a exceção é lançada antes da mutação. Extrair para função
 * pura não detectaria a regressão de ordenação (validação pós-deactivate).
 */
class TransferContainmentTest {

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        accountRepo: FinancialAccountRepository = mockk(relaxed = true),
        session: SessionManager = mockk()
    ): TransactionService {
        every { session.currentUser } returns MutableStateFlow(null)
        return TransactionService(
            repository           = repo,
            accountRepository    = accountRepo,
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

    private fun transferTx(
        id: Int,
        status: TransactionStatus = TransactionStatus.PENDING,
        parentId: Int? = null
    ) = Transaction(
        id                    = id,
        type                  = TransactionType.TRANSFER,
        status                = status,
        description           = "Transferência",
        amount                = Money.fromDouble(1_000.0),
        issueDate             = LocalDateTime.now(),
        dueDate               = LocalDateTime.now(),
        accountId             = 1,
        parentTransactionId   = parentId
    )

    private fun expenseTx(id: Int) = Transaction(
        id          = id,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = "Despesa",
        amount      = Money.fromDouble(500.0),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now(),
        accountId   = 1
    )

    // ── duplicate ─────────────────────────────────────────────────────────────

    @Test
    fun `duplicate de TRANSFER lanca IllegalArgumentException`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(42) } returns transferTx(42)
        val service = makeService(repo = repo)
        val ex = assertThrows<IllegalArgumentException> { service.duplicate(42) }
        assertTrue(ex.message!!.contains("Transferências"))
    }

    @Test
    fun `duplicate de EXPENSE comum nao lanca excecao`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { repo.findById(10) }   returns expenseTx(10)
        every { repo.insert(any()) }  returns 20
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val service = makeService(repo = repo, accountRepo = accountRepo)
        val newId = service.duplicate(10)
        assertEquals(20, newId)
    }

    // ── cancel — cascata ──────────────────────────────────────────────────────

    @Test
    fun `cancel com irma PENDING cancela as duas pernas`() {
        val repo = mockk<TransactionRepository>()
        val mainTx   = transferTx(id = 1, status = TransactionStatus.PENDING)
        val sisterTx = transferTx(id = 2, status = TransactionStatus.PENDING, parentId = 1)
        every { repo.findById(1) }               returns mainTx
        every { repo.findTransferDestination(1) } returns sisterTx
        every { repo.deactivate(any(), any()) }   just Runs
        val service = makeService(repo = repo)
        service.cancel(1)
        verify(exactly = 1) { repo.deactivate(1, any()) }
        verify(exactly = 1) { repo.deactivate(2, any()) }
    }

    @Test
    fun `cancel com irma PAID lanca IllegalStateException sem desativar nenhuma perna`() {
        val repo = mockk<TransactionRepository>()
        val mainTx   = transferTx(id = 1, status = TransactionStatus.PENDING)
        val sisterTx = transferTx(id = 2, status = TransactionStatus.PAID, parentId = 1)
        every { repo.findById(1) }               returns mainTx
        every { repo.findTransferDestination(1) } returns sisterTx
        val service = makeService(repo = repo)
        assertThrows<IllegalStateException> { service.cancel(1) }
        verify(exactly = 0) { repo.deactivate(any(), any()) }
    }

    @Test
    fun `cancel com irma CANCELED lanca IllegalStateException sem desativar nenhuma perna`() {
        val repo = mockk<TransactionRepository>()
        val mainTx   = transferTx(id = 1, status = TransactionStatus.PENDING)
        val sisterTx = transferTx(id = 2, status = TransactionStatus.CANCELED, parentId = 1)
        every { repo.findById(1) }               returns mainTx
        every { repo.findTransferDestination(1) } returns sisterTx
        val service = makeService(repo = repo)
        assertThrows<IllegalStateException> { service.cancel(1) }
        verify(exactly = 0) { repo.deactivate(any(), any()) }
    }
}
