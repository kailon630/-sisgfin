package br.com.sisgfin.payables

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.CounterpartyMap
import br.com.sisgfin.financial.transactions.CounterpartyResolver
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * F2-fix P0 — operações de escrita em PayablesViewModel.
 *
 * Testes 1–3: exceção em write op → errorMessage preenchido (e log emitido).
 * Teste 6: título PARTIAL com amount=1000 e paidAmount=300 → recordPayment recebe 700.
 *
 * Todos devem falhar ANTES da correção e passar DEPOIS.
 */
class PayablesViewModelWriteTest {

    private lateinit var service: TransactionService
    private lateinit var resolver: CounterpartyResolver

    private val pendingTx = Transaction(
        id          = 1,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = "Despesa pendente",
        amount      = Money.fromString("500.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(7),
        accountId   = 1
    )

    private val partialTx = Transaction(
        id          = 2,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PARTIAL,
        description = "Despesa parcial",
        amount      = Money.fromString("1000.00"),
        paidAmount  = Money.fromString("300.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(7),
        accountId   = 1
    )

    @BeforeEach
    fun setUp() {
        service  = mockk()
        resolver = mockk()
        // UnconfinedTestDispatcher faz launch {} executar inline até a primeira suspensão.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Constrói o ViewModel com mocks básicos configurados e aguarda o init { load() }.
     * Deve ser chamado APÓS configurar os mocks específicos de cada teste.
     */
    private fun buildVm(items: List<Transaction> = listOf(pendingTx)): PayablesViewModel {
        every { service.syncOverdueStatuses() } just Runs
        every { service.listByQuery(any())     } returns items
        every { resolver.resolve(any())        } returns CounterpartyMap.EMPTY
        every { service.canConfirmPayment()    } returns true
        val vm = PayablesViewModel(service, resolver)
        Thread.sleep(400) // aguarda init { load() } completar no IO thread
        return vm
    }

    // ── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    fun `P0-1 recordPayment lanca excecao - errorMessage preenchido`() {
        every { service.recordPayment(any(), any(), any(), any(), any()) } throws RuntimeException("Falha no banco")
        val vm = buildVm()

        vm.markAsPaidFull(pendingTx.id)
        Thread.sleep(400)

        assertNotNull(
            vm.uiState.value.errorMessage,
            "errorMessage deveria estar preenchido após exceção em recordPayment"
        )
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    fun `P0-2 cancel lanca excecao - errorMessage preenchido`() {
        every { service.cancel(any()) } throws RuntimeException("Falha no banco")
        val vm = buildVm()

        vm.cancelTransaction(pendingTx.id)
        Thread.sleep(400)

        assertNotNull(
            vm.uiState.value.errorMessage,
            "errorMessage deveria estar preenchido após exceção em cancel"
        )
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────

    @Test
    fun `P0-3 duplicate lanca excecao - errorMessage preenchido`() {
        every { service.duplicate(any()) } throws RuntimeException("Falha no banco")
        val vm = buildVm()

        vm.duplicateTransaction(pendingTx.id)
        Thread.sleep(400)

        assertNotNull(
            vm.uiState.value.errorMessage,
            "errorMessage deveria estar preenchido após exceção em duplicate"
        )
    }

    // ── Test 6 ───────────────────────────────────────────────────────────────

    @Test
    fun `P0-6 PARTIAL 1000 com 300 pagos - markAsPaidFull envia saldo restante 700`() {
        val capturedAmount = slot<Money>()
        every { service.recordPayment(any(), any(), capture(capturedAmount), any(), any()) } just Runs
        val vm = buildVm(items = listOf(partialTx))

        vm.markAsPaidFull(partialTx.id)
        Thread.sleep(400)

        assertEquals(
            Money.fromString("700.00"),
            capturedAmount.captured,
            "markAsPaidFull deve passar o saldo restante (700), não o valor total (1000)"
        )
    }
}
