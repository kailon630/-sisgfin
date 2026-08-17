package br.com.sisgfin.financial.transactions

import br.com.sisgfin.AuditRepository
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.financial.ledger.LedgerService
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-21: verifica que TransactionService sanitiza description, notes,
 * documentType e documentNumber antes de persistir.
 * Nenhum acesso a BD — o slot captura o objeto passado a repository.insert().
 */
class TransactionSanitizeTest {

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        accountRepo: FinancialAccountRepository = mockk(relaxed = true)
    ): TransactionService {
        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns true
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

    private fun account(id: Int) = FinancialAccount(id = id, name = "Conta #$id")

    private fun baseExpense(
        description: String = "Despesa",
        notes: String? = null,
        documentType: String? = null,
        documentNumber: String? = null
    ) = Transaction(
        type           = TransactionType.EXPENSE,
        status         = TransactionStatus.PENDING,
        description    = description,
        notes          = notes,
        documentType   = documentType,
        documentNumber = documentNumber,
        amount         = Money.fromString("100.00"),
        issueDate      = LocalDateTime.now(),
        dueDate        = LocalDateTime.now().plusDays(7),
        accountId      = 1
    )

    // ── create() ─────────────────────────────────────────────────────────────

    @Test
    fun `TSAN-01 description com CR no final e removido no create`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseExpense(description = "Despesa de teste\r"))

        assertEquals("Despesa de teste", slot.captured.description)
    }

    @Test
    fun `TSAN-02 description com CR embedded e removido e espacos colapsados`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseExpense(description = "ENERGIA\rELETRICA"))

        assertEquals("ENERGIA ELETRICA", slot.captured.description)
    }

    @Test
    fun `TSAN-03 notes com LF interno preservado`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseExpense(notes = "Linha 1\nLinha 2"))

        assertEquals("Linha 1\nLinha 2", slot.captured.notes)
    }

    @Test
    fun `TSAN-04 notes com CRLF vira LF`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseExpense(notes = "Linha 1\r\nLinha 2"))

        assertEquals("Linha 1\nLinha 2", slot.captured.notes)
    }

    @Test
    fun `TSAN-05 notes null permanece null`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseExpense(notes = null))

        assertNull(slot.captured.notes)
    }

    @Test
    fun `TSAN-06 documentType e documentNumber com CR sao limpos`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseExpense(documentType = "NF\r", documentNumber = "12345\r"))

        assertEquals("NF", slot.captured.documentType)
        assertEquals("12345", slot.captured.documentNumber)
    }

    @Test
    fun `TSAN-07 descricao gerada pelo PayrollEngine passa inalterada`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        val engineDesc = "Pagamento RICHARD EDUARDO INACIO DA SILVA — 08/2026"
        service.create(baseExpense(description = engineDesc))

        assertEquals(engineDesc, slot.captured.description)
    }

    @Test
    fun `TSAN-08 descricao ja limpa e idempotente`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseExpense(description = "Despesa normal", notes = "Obs sem problema"))

        assertEquals("Despesa normal", slot.captured.description)
        assertEquals("Obs sem problema", slot.captured.notes)
    }

    @Test
    fun `TSAN-09 description vazia nao quebra`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        every { repo.insert(any()) } returns 1
        val service = makeService(repo, accountRepo)

        // TransactionValidator rejeita description vazia, mas o sanitize nao deve lancar
        // Se validator rejeitar, a excecao e de validacao, nao de NullPointerException
        try {
            service.create(baseExpense(description = ""))
        } catch (e: IllegalArgumentException) {
            // esperado do validator — sanitize nao e o culpado
        }
    }

    // ── update() ─────────────────────────────────────────────────────────────

    @Test
    fun `TSAN-10 update sanitiza description antes de persistir`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        val existing = baseExpense("Original").copy(id = 5, status = TransactionStatus.PENDING)
        every { accountRepo.findById(1) } returns account(1)
        every { repo.findById(5) } returns existing
        val slot = slot<Transaction>()
        every { repo.update(capture(slot)) } just Runs
        val service = makeService(repo, accountRepo)

        service.update(existing.copy(description = "Atualizado\r"))

        assertEquals("Atualizado", slot.captured.description)
    }

    // ── createFromPayrollImport() ─────────────────────────────────────────────

    @Test
    fun `TSAN-11 createFromPayrollImport sanitiza description`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.createFromPayrollImport(
            baseExpense(description = "Salário 08/2026 — NOME\r")
                .copy(origin = TransactionOrigin.PAYROLL_IMPORT, employeeId = 3)
        )

        assertEquals("Salário 08/2026 — NOME", slot.captured.description)
    }
}
