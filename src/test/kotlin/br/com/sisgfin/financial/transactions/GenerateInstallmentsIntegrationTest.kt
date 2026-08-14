package br.com.sisgfin.financial.transactions

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * C-14 / Bloco 4 — caracterização de generateInstallments() via create().
 *
 * generateInstallments() é privado — testado exercitando create() com
 * installmentTotal > 1. O service REAL é instanciado com repositórios MockK.
 *
 * Regras verificadas:
 *   RN-17: N parcelas geradas; filhos têm parentTransactionId = pai.
 *   RN-18: última parcela absorve o arredondamento (totalAmount - slice*(N-1)).
 *   Datas: dueDate de cada filho = dueDate do pai + (installmentCurrent-1) meses.
 */
class GenerateInstallmentsIntegrationTest {

    private val baseDate: LocalDateTime = LocalDateTime.of(2026, 8, 1, 0, 0)

    private fun makeService(
        repo: TransactionRepository,
        accountRepo: FinancialAccountRepository = mockk()
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

    private fun templateTx(
        installmentTotal: Int?,
        amount: String = "1000.00"
    ) = Transaction(
        type             = TransactionType.EXPENSE,
        status           = TransactionStatus.PENDING,
        description      = "Parcela teste",
        amount           = Money.fromString(amount),
        issueDate        = baseDate,
        dueDate          = baseDate,
        accountId        = 1,
        installmentTotal = installmentTotal
    )

    // ── sem parcelamento ──────────────────────────────────────────────────────

    @Test
    fun `create sem parcelamento gera exatamente um insert`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        every { repo.insert(any()) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = null))

        verify(exactly = 1) { repo.insert(any()) }
    }

    @Test
    fun `create installmentTotal 1 gera exatamente um insert`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        every { repo.insert(any()) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 1))

        verify(exactly = 1) { repo.insert(any()) }
    }

    // ── 2 parcelas ────────────────────────────────────────────────────────────

    @Test
    fun `create 2 parcelas gera pai mais 1 filho - total 2 inserts`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 2, amount = "1000.00"))

        assertEquals(2, captured.size)
    }

    @Test
    fun `create 2 parcelas de 1000 - slice 500 para pai e filho`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 2, amount = "1000.00"))

        val parent = captured[0]
        val child  = captured[1]
        assertEquals(0, Money.fromString("500.00").compareTo(parent.amount))
        assertEquals(0, Money.fromString("500.00").compareTo(child.amount))
    }

    @Test
    fun `create 2 parcelas - filho tem parentTransactionId apontando para o pai`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 2, amount = "1000.00"))

        val child = captured[1]
        assertEquals(10, child.parentTransactionId)
    }

    @Test
    fun `create 2 parcelas - filho tem installmentCurrent 2`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 2, amount = "1000.00"))

        assertEquals(2, captured[1].installmentCurrent)
    }

    @Test
    fun `create 2 parcelas - dueDate do filho e baseDate mais 1 mes`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 2, amount = "1000.00"))

        val childDueDate = captured[1].dueDate
        assertEquals(baseDate.plusMonths(1), childDueDate)
    }

    @Test
    fun `create 2 parcelas - filho tem paymentDate null e paidAmount null`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 2, amount = "1000.00"))

        val child = captured[1]
        assertNull(child.paymentDate)
        assertNull(child.paidAmount)
    }

    // ── 3 parcelas ────────────────────────────────────────────────────────────

    @Test
    fun `create 3 parcelas gera pai mais 2 filhos - total 3 inserts`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "300.00"))

        assertEquals(3, captured.size)
    }

    @Test
    fun `create 3 parcelas iguais - todos com amount 100`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "300.00"))

        captured.forEach { tx ->
            assertEquals(0, Money.fromString("100.00").compareTo(tx.amount),
                "Esperado 100.00 mas foi ${tx.amount} em installmentCurrent=${tx.installmentCurrent}")
        }
    }

    @Test
    fun `create 3 parcelas com arredondamento - ultima absorve o centavo restante`() {
        // 100.00 / 3 = 33.33 (floor, scale=2); ultima = 100.00 - 33.33*2 = 33.34
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "100.00"))

        val parent = captured[0]
        val child2 = captured[1]
        val child3 = captured[2]
        assertEquals(0, Money.fromString("33.33").compareTo(parent.amount), "parent amount")
        assertEquals(0, Money.fromString("33.33").compareTo(child2.amount), "child2 amount")
        assertEquals(0, Money.fromString("33.34").compareTo(child3.amount), "child3 amount (absorve arredondamento)")
    }

    @Test
    fun `create 3 parcelas - datas incrementam mes a mes`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "300.00"))

        assertEquals(baseDate,               captured[0].dueDate) // pai = base
        assertEquals(baseDate.plusMonths(1), captured[1].dueDate) // filho 2 = base + 1m
        assertEquals(baseDate.plusMonths(2), captured[2].dueDate) // filho 3 = base + 2m
    }

    @Test
    fun `create 3 parcelas - installmentCurrent sequencial 1 2 3`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "300.00"))

        assertEquals(1, captured[0].installmentCurrent)
        assertEquals(2, captured[1].installmentCurrent)
        assertEquals(3, captured[2].installmentCurrent)
    }

    @Test
    fun `create 3 parcelas - installmentTotal 3 em todos`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "300.00"))

        captured.forEach { tx ->
            assertEquals(3, tx.installmentTotal)
        }
    }

    @Test
    fun `create 3 parcelas - filhos com parentTransactionId do pai e status PENDING`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "300.00"))

        val child2 = captured[1]
        val child3 = captured[2]
        assertEquals(10, child2.parentTransactionId)
        assertEquals(10, child3.parentTransactionId)
        assertEquals(TransactionStatus.PENDING, child2.status)
        assertEquals(TransactionStatus.PENDING, child3.status)
    }

    @Test
    fun `create 3 parcelas - soma dos amounts iguala o total original`() {
        val repo = mockk<TransactionRepository>()
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns FinancialAccount(id = 1, name = "Caixa")
        val captured = mutableListOf<Transaction>()
        every { repo.insert(capture(captured)) } returnsMany listOf(10, 11, 12)
        val service = makeService(repo, accountRepo)

        service.create(templateTx(installmentTotal = 3, amount = "100.00"))

        val soma = captured.fold(Money.ZERO) { acc, tx -> acc + tx.amount }
        assertEquals(0, Money.fromString("100.00").compareTo(soma),
            "Soma das parcelas deve igualar o total original: esperado 100.00, obtido $soma")
    }
}
