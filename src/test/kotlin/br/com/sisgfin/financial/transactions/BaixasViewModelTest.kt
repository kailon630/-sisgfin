package br.com.sisgfin.financial.transactions

import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.budget.BudgetItemRepository
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPayment
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M5-B Block 1 — lista de baixas no ViewModel.
 *
 * B1-01: baixas carregadas ao selecionar transação
 * B1-02: reversePayment delega ao service e recarrega baixas
 * B1-03: canConfirmPayment reflete o service
 * B1-04: lista vazia não quebra (selectTransaction(null) limpa estado)
 */
class BaixasViewModelTest {

    private lateinit var service: TransactionService
    private lateinit var paymentRepo: TransactionPaymentRepository

    private val tx = Transaction(
        id          = 10,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PARTIAL,
        description = "Fornecedor XYZ",
        amount      = Money.fromString("1000.00"),
        paidAmount  = Money.fromString("300.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(30),
        accountId   = 1
    )

    private val baixaSample = TransactionPayment(
        id              = 1,
        transactionId   = 10,
        paymentDate     = LocalDate.now(),
        accountId       = 1,
        principalAmount = Money.fromString("300.00")
    )

    @BeforeEach
    fun setUp() {
        service     = mockk()
        paymentRepo = mockk()
        every { service.listAll()                   } returns emptyList()
        every { service.applyListFilter(any())      } just Runs
        every { service.getTimeline(any())          } returns emptyList()
        every { service.canConfirmPayment()         } returns true
        every { paymentRepo.findByTransaction(any()) } returns emptyList()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(): TransactionsViewModel = TransactionsViewModel(
        service              = service,
        accountRepository    = mockk(relaxed = true),
        supplierRepository   = mockk(relaxed = true),
        costCenterRepository = mockk(relaxed = true),
        categoryRepository   = mockk(relaxed = true),
        sessionManager       = mockk(relaxed = true),
        budgetRepository     = mockk(relaxed = true),
        counterpartyResolver = mockk { every { resolve(any()) } returns CounterpartyMap.EMPTY },
        paymentRepository    = paymentRepo
    )

    // ── B1-01 ────────────────────────────────────────────────────────────────

    @Test
    fun `B1-01 selectTransaction carrega baixas do paymentRepo`() {
        every { paymentRepo.findByTransaction(10) } returns listOf(baixaSample)
        val vm = buildVm()

        vm.selectTransaction(tx)
        Thread.sleep(200)

        assertEquals(listOf(baixaSample), vm.baixas.value)
    }

    // ── B1-02 ────────────────────────────────────────────────────────────────

    @Test
    fun `B1-02 reversePayment delega ao service e recarrega baixas`() {
        every { service.reversePayment(1, "Justificativa teste") } just Runs
        every { paymentRepo.findByTransaction(10) } returns listOf(baixaSample)
        val vm = buildVm()
        vm.selectTransaction(tx)
        Thread.sleep(200)

        every { paymentRepo.findByTransaction(10) } returns emptyList()
        vm.reversePayment(1, "Justificativa teste")
        Thread.sleep(200)

        verify(exactly = 1) { service.reversePayment(1, "Justificativa teste") }
        assertTrue(vm.baixas.value.isEmpty(), "baixas devem estar vazias após estorno")
    }

    // ── B1-03 ────────────────────────────────────────────────────────────────

    @Test
    fun `B1-03 canConfirmPayment false quando service retorna false`() {
        every { service.canConfirmPayment() } returns false
        val vm = buildVm()

        assertEquals(false, vm.canConfirmPayment())
    }

    // ── R5-01 ────────────────────────────────────────────────────────────────

    @Test
    fun `R5-01 canConfirmPayment true quando service retorna true`() {
        every { service.canConfirmPayment() } returns true
        val vm = buildVm()

        assertEquals(true, vm.canConfirmPayment())
    }

    // ── B1-04 ────────────────────────────────────────────────────────────────

    @Test
    fun `B1-04 selectTransaction null limpa baixas sem erro`() {
        every { paymentRepo.findByTransaction(10) } returns listOf(baixaSample)
        val vm = buildVm()
        vm.selectTransaction(tx)
        Thread.sleep(200)

        vm.selectTransaction(null)

        assertTrue(vm.baixas.value.isEmpty(), "baixas devem ser limpas ao deselecionar")
    }
}
