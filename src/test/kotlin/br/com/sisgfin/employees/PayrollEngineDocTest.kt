package br.com.sisgfin.employees

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import io.mockk.*
import org.junit.jupiter.api.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * T-03 — PayrollEngine grava referência documental FOLHA + competência.
 *
 * PE-01: lançamento para 08/2026 tem documentType=FOLHA e documentNumber=08/2026
 * PE-02: generateForEmployee gera dois meses com competências distintas
 * PE-03: buildTcespDesc do lançamento resultante contém CF FOLHA 08/2026
 */
class PayrollEngineDocTest {

    private val employee = Employee(
        id          = 1,
        name        = "Ana Silva",
        document    = "12345678900",
        phone       = "",
        email       = "",
        role        = "Aux",
        salary      = Money.fromString("2000.00"),
        paymentDay  = 5,
        paymentDays = "5",
        active      = true
    )

    private fun buildEngine(capturedTxs: MutableList<Transaction>): PayrollEngine {
        val empRepo     = mockk<EmployeeRepository>()
        val txRepo      = mockk<TransactionRepository>()
        val txService   = mockk<TransactionService>()
        val accountRepo = mockk<FinancialAccountRepository>()

        every { empRepo.getAllActive() }               returns listOf(employee)
        every { empRepo.getById(1) }                  returns employee
        every { txRepo.existsPaymentForEmployee(any(), any()) } returns false
        every { accountRepo.findAll() }               returns listOf(FinancialAccount(id = 1, name = "Caixa"))
        every { txService.create(capture(capturedTxs)) } returns 1

        return PayrollEngine(empRepo, txRepo, txService, accountRepo)
    }

    // ── PE-01 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-01 generateForMonth agosto 2026 grava FOLHA e competencia 08 barra 2026`() {
        val captured = mutableListOf<Transaction>()
        val engine = buildEngine(captured)

        engine.generateForMonth(YearMonth.of(2026, 8))

        assertEquals(1, captured.size)
        val tx = captured[0]
        assertEquals("FOLHA", tx.documentType)
        assertEquals("08/2026", tx.documentNumber)
    }

    // ── PE-02 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-02 generateForEmployee gera dois meses com competencias distintas`() {
        val captured = mutableListOf<Transaction>()
        val engine = buildEngine(captured)

        engine.generateForEmployee(1)

        assertEquals(2, captured.size)
        val numbers = captured.map { it.documentNumber }
        assertEquals(2, numbers.distinct().size, "cada mes deve ter competencia diferente")
        captured.forEach { assertNotNull(it.documentType) }
    }

    // ── PE-03 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-03 buildTcespDesc do lancamento resultante contem CF FOLHA 08 barra 2026`() {
        val captured = mutableListOf<Transaction>()
        val engine = buildEngine(captured)
        engine.generateForMonth(YearMonth.of(2026, 8))

        val tx   = captured[0]
        val desc = br.com.sisgfin.reports.buildTcespDesc(tx, "Ana Silva")

        assert(desc.contains("CF FOLHA 08/2026")) {
            "esperado 'CF FOLHA 08/2026' em '$desc'"
        }
    }
}
