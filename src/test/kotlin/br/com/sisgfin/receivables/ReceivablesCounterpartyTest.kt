package br.com.sisgfin.receivables

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.CounterpartyMap
import br.com.sisgfin.financial.transactions.CounterpartyResolver
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
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

/**
 * R5.6 — ReceivablesViewModel resolve contraparte via CounterpartyResolver (T-06).
 *
 * R56-01: cliente resolvido via resolver (supplierId)
 * R56-02: recebível com employeeId e sem supplierId exibe nome do funcionário
 * R56-03: sem contraparte → grupo "Sem cliente"
 */
class ReceivablesCounterpartyTest {

    private lateinit var repository: TransactionRepository
    private lateinit var resolver: CounterpartyResolver
    private lateinit var service: TransactionService

    private fun tx(id: Int, supplierId: Int? = null, employeeId: Int? = null) = Transaction(
        id          = id,
        type        = TransactionType.INCOME,
        status      = TransactionStatus.PENDING,
        description = "Recebível $id",
        amount      = Money.fromString("100.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(10),
        accountId   = 1,
        supplierId  = supplierId,
        employeeId  = employeeId
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        resolver   = mockk()
        service    = mockk()
        every { service.syncOverdueStatuses() } just Runs
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm() = ReceivablesViewModel(
        transactionRepository = repository,
        counterpartyResolver  = resolver,
        transactionService    = service
    )

    // ── R56-01 ───────────────────────────────────────────────────────────────

    @Test
    fun `R56-01 grupo recebe nome do cliente via resolver supplierId`() {
        val t = tx(1, supplierId = 5)
        every { repository.findReceivables() } returns listOf(t)
        every { resolver.resolve(listOf(t)) } returns CounterpartyMap(
            mapOf(5 to "Cliente ABC"), emptyMap()
        )
        val vm = buildVm()
        Thread.sleep(400)

        val groups = vm.uiState.value.groups
        assertEquals(1, groups.size)
        assertEquals("Cliente ABC", groups[0].clientName)
    }

    // ── R56-02 ───────────────────────────────────────────────────────────────

    @Test
    fun `R56-02 recebivel com employeeId e sem supplierId exibe nome do funcionario`() {
        val t = tx(2, supplierId = null, employeeId = 7)
        every { repository.findReceivables() } returns listOf(t)
        every { resolver.resolve(listOf(t)) } returns CounterpartyMap(
            emptyMap(), mapOf(7 to "João Silva")
        )
        val vm = buildVm()
        Thread.sleep(400)

        val groups = vm.uiState.value.groups
        assertEquals(1, groups.size)
        assertEquals("João Silva", groups[0].clientName)
    }

    // ── R56-03 ───────────────────────────────────────────────────────────────

    @Test
    fun `R56-03 sem contraparte agrupa como Sem cliente`() {
        val t = tx(3, supplierId = null, employeeId = null)
        every { repository.findReceivables() } returns listOf(t)
        every { resolver.resolve(listOf(t)) } returns CounterpartyMap.EMPTY
        val vm = buildVm()
        Thread.sleep(400)

        val groups = vm.uiState.value.groups
        assertEquals(1, groups.size)
        assertEquals("Sem cliente", groups[0].clientName)
    }
}
