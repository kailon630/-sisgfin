package br.com.sisgfin.payroll

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionService
import io.mockk.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/**
 * T-03 — PayrollImportService.confirm() grava referência documental FOLHA.
 *
 * PI-01: adiantamento recebe documentNumber = "08/2026-ADT"
 * PI-02: salário/líquido recebe documentNumber = "08/2026"
 * PI-03: os dois lançamentos do mesmo funcionário no mesmo mês são distinguíveis
 */
class PayrollImportDocTest {

    private val agosto = YearMonth.of(2026, 8)

    private val rawEntry = PayrollRawEntry(
        matricula   = 1,
        nome        = "João Souza",
        cpf         = "12345678900",
        funcao      = "Aux",
        adiantamento = Money.fromString("500.00"),
        liquido      = Money.fromString("1500.00"),
        salaryBase   = Money.fromString("2000.00"),
        liquidoCount = 1
    )

    private val entry = PayrollEntry(
        raw                 = rawEntry,
        employeeId          = 42,
        adiantamentoDueDate = LocalDate.of(2026, 8, 20),
        liquidoDueDate      = LocalDate.of(2026, 9, 5),
        employeeFound       = true,
        warningMessage      = null
    )

    private val result = PayrollImportResult(
        entries        = listOf(entry),
        notFoundCount  = 0,
        warnings       = emptyList(),
        referenceMonth = agosto
    )

    private fun buildService(capturedTxs: MutableList<Transaction>): PayrollImportService {
        val empRepo  = mockk<EmployeeRepository>()
        val service  = mockk<TransactionService>()
        every { empRepo.findByCpf(any()) } returns mockk<Employee>(relaxed = true)
        every { service.cancelPendingPayrollForMonth(any(), any()) } returns 0
        every { service.createFromPayrollImport(capture(capturedTxs)) } returns 1
        return PayrollImportService(empRepo, service)
    }

    // ── PI-01 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PI-01 adiantamento tem documentNumber com sufixo ADT`() {
        val captured = mutableListOf<Transaction>()
        buildService(captured).confirm(result, accountId = 1, categoryId = 1, costCenterId = null, userId = 1)

        val adt = captured.first { it.description.startsWith("Adiantamento") }
        assertEquals("FOLHA", adt.documentType)
        assertEquals("08/2026-ADT", adt.documentNumber)
    }

    // ── PI-02 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PI-02 salario tem documentNumber sem sufixo`() {
        val captured = mutableListOf<Transaction>()
        buildService(captured).confirm(result, accountId = 1, categoryId = 1, costCenterId = null, userId = 1)

        val sal = captured.first { it.description.startsWith("Salário") }
        assertEquals("FOLHA", sal.documentType)
        assertEquals("08/2026", sal.documentNumber)
    }

    // ── PI-03 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PI-03 adiantamento e salario do mesmo mes sao distinguiveis pela referencia`() {
        val captured = mutableListOf<Transaction>()
        buildService(captured).confirm(result, accountId = 1, categoryId = 1, costCenterId = null, userId = 1)

        assertEquals(2, captured.size)
        val numbers = captured.map { it.documentNumber }
        assertEquals(2, numbers.distinct().size, "documentNumber deve diferir entre adiantamento e salario")
    }
}
