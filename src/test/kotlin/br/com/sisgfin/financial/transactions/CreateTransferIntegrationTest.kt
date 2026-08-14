package br.com.sisgfin.financial.transactions

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * C-14 / Bloco 2 — caracterização de TransactionService.createTransfer().
 *
 * Exercita o service REAL com repositórios mockados.
 * Verifica: validações de pré-condição, estrutura do par de transações
 * geradas (source/destination) e integridade dos campos-chave.
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
            sessionManager       = session
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
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 20)
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
        assertEquals(2, captured.size)
    }

    @Test
    fun `createTransfer source tem tipo TRANSFER status PENDING e sem parentId`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")

        val source = captured[0]
        assertEquals(TransactionType.TRANSFER, source.type)
        assertEquals(TransactionStatus.PENDING, source.status)
        assertEquals(1, source.accountId)
        assertNull(source.parentTransactionId)
        assertEquals(0, Money.fromString("500.00").compareTo(source.amount))
    }

    @Test
    fun `createTransfer destination tem tipo TRANSFER status PENDING e parentId apontando para source`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")

        val destination = captured[1]
        assertEquals(TransactionType.TRANSFER, destination.type)
        assertEquals(TransactionStatus.PENDING, destination.status)
        assertEquals(2, destination.accountId)
        assertEquals(10, destination.parentTransactionId)
        assertEquals(0, Money.fromString("500.00").compareTo(destination.amount))
    }

    @Test
    fun `createTransfer destination description e prefixada com Recebimento`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED para fornecedor")

        val destination = captured[1]
        assertTrue(destination.description.startsWith("Recebimento:"))
        assertTrue(destination.description.contains("TED para fornecedor"))
    }

    @Test
    fun `createTransfer source e destination com mesmo amount`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 20)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        val amount = Money.fromString("1234.56")
        service.createTransfer(1, 2, amount, transferDate, "Transferência")

        assertEquals(0, captured[0].amount.compareTo(captured[1].amount))
    }

    @Test
    fun `createTransfer propaga costCenterId e categoryId para ambas as pernas`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 20)
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

        assertEquals(7, captured[0].costCenterId)
        assertEquals(3, captured[0].categoryId)
        assertEquals(7, captured[1].costCenterId)
        assertEquals(3, captured[1].categoryId)
    }

    // ── CARACTERIZAÇÃO — atomicidade ──────────────────────────────────────────

    @Test
    fun `CARACTERIZACAO C-15 createTransfer nao e atomico perna de origem fica orfa`() {
        // CARACTERIZAÇÃO — comportamento INCORRETO, mantido de propósito.
        // Não existe transaction{} do Exposed envolvendo os dois inserts.
        // Se o segundo insert falhar, a perna de origem já está gravada no banco
        // sem par — dinheiro sai da conta de origem sem entra na de destino.
        // Corrigir em C-15: envolver os dois inserts em transaction{} do Exposed.
        // Quando este teste QUEBRAR (nenhuma exceção ou eventually rollback), é sinal de sucesso.
        //
        // Nota técnica: MockK conta invocações mesmo quando lançam exceção. Por isso
        // usamos match { accountId == N } para distinguir cada perna individualmente.
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>(relaxed = true)
        // primeiro insert (origem) sucede; segundo (destino) lança
        every { repo.insert(any()) } returns 10 andThenThrows RuntimeException("DB error na segunda perna")
        val service = makeService(repo = repo, accountRepo = accountRepo)

        assertThrows<RuntimeException> {
            service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")
        }

        // perna de origem (accountId=1, sem parentId) chegou ao insert — em banco real já persistida
        verify(exactly = 1) { repo.insert(match { it.accountId == 1 && it.parentTransactionId == null }) }
        // perna de destino foi tentada e causou a exceção — não há rollback da origem
        verify(exactly = 1) { repo.insert(match { it.accountId == 2 }) }
    }
}
