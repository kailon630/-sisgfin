package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * M5-B Block 3 — D3a: campo de desconto no diálogo de quitação.
 *
 * B3-01: discountAmount é repassado ao service
 * B3-02: discountAmount null → service recebe Money.ZERO
 * B3-03: discountAmount passado explicitamente como zero não altera comportamento
 */
class DescontoViewModelTest {

    private lateinit var service: TransactionService

    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        service = mockk()
        every { service.listAll()                } returns emptyList()
        every { service.applyListFilter(any())   } just Runs
        every { service.recordPayment(any(), any(), any(), any(), any(), any()) } just Runs
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
        paymentRepository    = mockk(relaxed = true)
    )

    // ── B3-01 ────────────────────────────────────────────────────────────────

    @Test
    fun `B3-01 discountAmount e repassado ao service`() {
        val vm = buildVm()
        val discount = Money.fromString("50.00")

        vm.recordPayment(
            id            = 1,
            paymentDate   = now,
            paidAmount    = Money.fromString("950.00"),
            discountAmount = discount
        )
        Thread.sleep(200)

        verify {
            service.recordPayment(
                1, now, Money.fromString("950.00"), null, null,
                discountAmount = discount
            )
        }
    }

    // ── B3-02 ────────────────────────────────────────────────────────────────

    @Test
    fun `B3-02 discountAmount null resulta em Money ZERO no service`() {
        val vm = buildVm()

        vm.recordPayment(
            id           = 1,
            paymentDate  = now,
            paidAmount   = Money.fromString("1000.00"),
            discountAmount = null
        )
        Thread.sleep(200)

        verify {
            service.recordPayment(
                1, now, Money.fromString("1000.00"), null, null,
                discountAmount = Money.ZERO
            )
        }
    }

    // ── B3-03 ────────────────────────────────────────────────────────────────

    @Test
    fun `B3-03 discountAmount explicito zero equivale a nenhum desconto`() {
        val vm = buildVm()

        vm.recordPayment(
            id             = 1,
            paymentDate    = now,
            paidAmount     = Money.fromString("1000.00"),
            discountAmount = Money.ZERO
        )
        Thread.sleep(200)

        verify {
            service.recordPayment(
                1, now, Money.fromString("1000.00"), null, null,
                discountAmount = Money.ZERO
            )
        }
    }
}
