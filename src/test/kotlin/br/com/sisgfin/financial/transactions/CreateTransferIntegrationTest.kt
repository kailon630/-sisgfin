package br.com.sisgfin.financial.transactions

import br.com.sisgfin.FinancialAccount
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * C-14 / Bloco 2 — caracterização de TransactionService.createTransfer().
 * C-15 — atomicidade: os dois inserts ocorrem em insertTransferPair() via transaction{}.
 *
 * Exercita o service REAL com repositórios mockados.
 * Verifica: validações de pré-condição, estrutura do par de transações
 * geradas (source/destinationTemplate) e integridade dos campos-chave.
 */
class CreateTransferIntegrationTest {

    private val transferDate: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

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

    // ── validações de pré-condição ────────────────────────────────────────────

    @Test
    fun `createTransfer mesma conta lanca IllegalArgumentException`() {
        val service = makeService()

        val ex = assertThrows<IllegalArgumentException> {
            service.createTransfer(1, 1, Money.fromString("500.00"), transferDate, "TED")
        }
        assertTrue(ex.message!!.contains("origem e destino"))
    }

    @Test
    fun `createTransfer valor zero lanca IllegalArgumentException`() {
        val service = makeService()

        val ex = assertThrows<IllegalArgumentException> {
            service.createTransfer(1, 2, Money.ZERO, transferDate, "TED")
        }
        assertTrue(ex.message!!.contains("maior que zero"))
    }

    @Test
    fun `createTransfer valor negativo lanca IllegalArgumentException`() {
        val service = makeService()

        val ex = assertThrows<IllegalArgumentException> {
            service.createTransfer(1, 2, Money.fromString("-100.00"), transferDate, "TED")
        }
        assertTrue(ex.message!!.contains("maior que zero"))
    }

    @Test
    fun `createTransfer conta de origem inexistente lanca IllegalArgumentException`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns null
        every { accountRepo.findById(2) } returns account(2)
        val service = makeService(accountRepo = accountRepo)

        assertThrows<IllegalArgumentException> {
            service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")
        }
    }

    @Test
    fun `createTransfer conta de destino inexistente lanca IllegalArgumentException`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        every { accountRepo.findById(2) } returns null
        val service = makeService(accountRepo = accountRepo)

        assertThrows<IllegalArgumentException> {
            service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")
        }
    }

    // ── par de transações gerado ──────────────────────────────────────────────

    @Test
    fun `createTransfer valida cria dois lancamentos e retorna par de ids`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        every { accountRepo.findById(2) } returns account(2)
        val repo = mockk<TransactionRepository>()
        every { repo.insertTransferPair(any(), any()) } returns (10 to 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        val (sourceId, destId) = service.createTransfer(
            sourceAccountId      = 1,
            destinationAccountId = 2,
            amount               = Money.fromString("500.00"),
            date                 = transferDate,
            description          = "TED para fornecedor"
        )

        assertEquals(10, sourceId)
        assertEquals(20, destId)
        verify(exactly = 1) { repo.insertTransferPair(any(), any()) }
    }

    @Test
    fun `createTransfer source tem tipo TRANSFER status PENDING e sem parentId`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val sourceSlot = slot<Transaction>()
        every { repo.insertTransferPair(capture(sourceSlot), any()) } returns (10 to 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")

        val source = sourceSlot.captured
        assertEquals(TransactionType.TRANSFER, source.type)
        assertEquals(TransactionStatus.PENDING, source.status)
        assertEquals(1, source.accountId)
        assertNull(source.parentTransactionId)
        assertEquals(0, Money.fromString("500.00").compareTo(source.amount))
    }

    @Test
    fun `createTransfer destination template tem tipo TRANSFER status PENDING e accountId destino`() {
        // parentTransactionId e vinculado dentro de insertTransferPair (nivel repositorio).
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val destSlot = slot<Transaction>()
        every { repo.insertTransferPair(any(), capture(destSlot)) } returns (10 to 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")

        val destTemplate = destSlot.captured
        assertEquals(TransactionType.TRANSFER, destTemplate.type)
        assertEquals(TransactionStatus.PENDING, destTemplate.status)
        assertEquals(2, destTemplate.accountId)
        assertEquals(0, Money.fromString("500.00").compareTo(destTemplate.amount))
    }

    @Test
    fun `createTransfer destination description e prefixada com Recebimento`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val destSlot = slot<Transaction>()
        every { repo.insertTransferPair(any(), capture(destSlot)) } returns (10 to 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED para fornecedor")

        val destTemplate = destSlot.captured
        assertTrue(destTemplate.description.startsWith("Recebimento:"))
        assertTrue(destTemplate.description.contains("TED para fornecedor"))
    }

    @Test
    fun `createTransfer source e destination com mesmo amount`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val sourceSlot = slot<Transaction>()
        val destSlot = slot<Transaction>()
        every { repo.insertTransferPair(capture(sourceSlot), capture(destSlot)) } returns (10 to 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        val amount = Money.fromString("1234.56")
        service.createTransfer(1, 2, amount, transferDate, "Transferência")

        assertEquals(0, sourceSlot.captured.amount.compareTo(destSlot.captured.amount))
    }

    @Test
    fun `createTransfer propaga costCenterId e categoryId para ambas as pernas`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val sourceSlot = slot<Transaction>()
        val destSlot = slot<Transaction>()
        every { repo.insertTransferPair(capture(sourceSlot), capture(destSlot)) } returns (10 to 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(
            sourceAccountId      = 1,
            destinationAccountId = 2,
            amount               = Money.fromString("500.00"),
            date                 = transferDate,
            description          = "TED",
            costCenterId         = 7,
            categoryId           = 3
        )

        assertEquals(7, sourceSlot.captured.costCenterId)
        assertEquals(3, sourceSlot.captured.categoryId)
        assertEquals(7, destSlot.captured.costCenterId)
        assertEquals(3, destSlot.captured.categoryId)
    }

    // ── atomicidade — comportamento CORRETO ──────────────────────────────────

    @Test
    fun `createTransfer atomico excecao na criacao nao deixa perna orfa`() {
        // C-15 CORRIGIDO: os dois inserts ocorrem dentro de insertTransferPair(),
        // que usa transaction{} do Exposed. Se o metodo lancar, nenhum insert foi
        // confirmado no banco — nao ha perna de origem orfa.
        // Verificado aqui: se insertTransferPair() lanca, o service propaga a excecao
        // e NUNCA chama insert() separadamente (o que poderia deixar a origem gravada).
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>(relaxed = true)
        every { repo.insertTransferPair(any(), any()) } throws RuntimeException("DB error atomico")
        val service = makeService(repo = repo, accountRepo = accountRepo)

        assertThrows<RuntimeException> {
            service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")
        }

        // chamada unica e atomica — nunca inserts separados (que poderiam deixar origem orfa)
        verify(exactly = 1) { repo.insertTransferPair(any(), any()) }
        verify(exactly = 0) { repo.insert(any()) }
    }
}
