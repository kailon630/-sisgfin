package br.com.sisgfin.financial.transactions

import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.budget.BudgetItemRepository
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import br.com.sisgfin.financial.transactions.CounterpartyResolver
import br.com.sisgfin.financial.transactions.CounterpartyMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * E1 — contrato de estado produzido por openNewExpense().
 *
 * Verifica que o item pré-populado tem os campos certos antes de qualquer
 * interação do usuário. Não testa UI; apenas o estado do ViewModel.
 */
class TransactionPanelStateTest {

    private lateinit var service: TransactionService
    private lateinit var accountRepo: FinancialAccountRepository
    private lateinit var supplierRepo: SupplierRepository
    private lateinit var costCenterRepo: CostCenterRepository
    private lateinit var categoryRepo: ExpenseCategoryRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var budgetRepo: BudgetItemRepository
    private lateinit var counterpartyResolver: CounterpartyResolver

    @BeforeEach
    fun setUp() {
        service              = mockk()
        accountRepo          = mockk()
        supplierRepo         = mockk()
        costCenterRepo       = mockk()
        categoryRepo         = mockk()
        sessionManager       = mockk()
        budgetRepo           = mockk()
        counterpartyResolver = mockk()

        every { service.listAll()                    } returns emptyList()
        every { service.applyListFilter(any())       } just Runs
        every { accountRepo.findAll()                } returns emptyList()
        every { supplierRepo.findAll()               } returns emptyList()
        every { costCenterRepo.findAll()             } returns emptyList()
        every { categoryRepo.findAll()               } returns emptyList()
        every { counterpartyResolver.resolve(any())  } returns CounterpartyMap.EMPTY

        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(): TransactionsViewModel = TransactionsViewModel(
        service              = service,
        accountRepository    = accountRepo,
        supplierRepository   = supplierRepo,
        costCenterRepository = costCenterRepo,
        categoryRepository   = categoryRepo,
        sessionManager       = sessionManager,
        budgetRepository     = budgetRepo,
        counterpartyResolver = counterpartyResolver
    )

    @Test
    fun `openNewExpense - selectedItem tem id zero`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem
        assertNotNull(item)
        assertEquals(0, item.id)
    }

    @Test
    fun `openNewExpense - selectedItem tem tipo EXPENSE`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertEquals(TransactionType.EXPENSE, item.type)
    }

    @Test
    fun `openNewExpense - selectedItem tem status PENDING`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertEquals(TransactionStatus.PENDING, item.status)
    }

    @Test
    fun `openNewExpense - selectedItem tem description vazia`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertEquals("", item.description)
    }

    @Test
    fun `openNewExpense - selectedItem tem amount de centavo`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertEquals(Money.fromString("0.01"), item.amount)
    }

    @Test
    fun `openNewExpense - selectedItem tem accountId zero`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertEquals(0, item.accountId)
    }

    @Test
    fun `openNewExpense - selectedItem tem installmentTotal nulo`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertNull(item.installmentTotal)
    }

    @Test
    fun `openNewExpense - selectedItem tem paidAmount nulo`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertNull(item.paidAmount)
    }

    @Test
    fun `openNewExpense - selectedItem tem paymentDate nulo`() {
        val vm = buildVm()
        vm.openNewExpense()
        val item = vm.uiState.value.selectedItem!!
        assertNull(item.paymentDate)
    }
}
