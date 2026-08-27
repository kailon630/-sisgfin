package br.com.sisgfin.engine

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.employees.PayrollEngine
import br.com.sisgfin.recurrence.RecurrenceEngine
import io.mockk.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals

private object AlwaysLocker : EngineLocker {
    override fun <T> tryLock(key: Long, block: () -> T): T = block()
}

/**
 * PD-08B — PARTIAL_FAILURE: falha por funcionário não aborta os demais.
 *
 * TB1 — 3 funcionários, do meio falha → created=2, failed=1, PARTIAL_FAILURE, terceiro tem lançamento.
 * TB2 — Todos os 3 falham → failed=3, created=0, status=FAILED (não PARTIAL).
 * TB3 — PARTIAL_FAILURE: orquestrador chama finishPartial, nunca finishSuccess.
 */
class PD08BPartialFailureTest {

    private val now = YearMonth.of(2026, 8)

    private fun employee(id: Int, name: String) = Employee(
        id = id, name = name, document = "0000000000$id", phone = "", email = "",
        role = "Aux", salary = Money.fromString("3000.00"),
        paymentDay = 5, paymentDays = "5", active = true
    )

    // ── TB1 ────────────────────────────────────────────────────────────────────

    @Test
    fun `TB1 funcionario do meio falha cria para os outros e registra PARTIAL_FAILURE`() {
        val empRepo     = mockk<EmployeeRepository>()
        val txRepo      = mockk<TransactionRepository>()
        val txService   = mockk<TransactionService>()
        val accountRepo = mockk<FinancialAccountRepository>()
        val runRepo     = mockk<EngineRunRepository>(relaxed = true)

        every { accountRepo.findAll() }                               returns listOf(FinancialAccount(id = 1, name = "Caixa"))
        every { empRepo.getAllActive() }                               returns listOf(employee(1, "Ana"), employee(2, "Bob"), employee(3, "Carlos"))
        every { txRepo.existsPaymentForEmployee(1, any()) }           returns false
        every { txRepo.existsPaymentForEmployee(2, any()) }           throws RuntimeException("falha de banco")
        every { txRepo.existsPaymentForEmployee(3, any()) }           returns false
        every { txService.create(any()) }                             returns 1

        val payrollEngine = PayrollEngine(empRepo, txRepo, txService, accountRepo)
        val results = payrollEngine.generateForMonth(now)

        assertEquals(2, results.sumOf { it.generated }, "Ana e Carlos devem ter lançamento criado")
        assertEquals(1, results.sumOf { it.failed },    "Bob deve contar como falha")
        assertEquals(0, results.first { it.employeeName == "Bob" }.generated)
        assertEquals(1, results.first { it.employeeName == "Carlos" }.generated,
            "Carlos deve ter lançamento gerado mesmo após falha do Bob")

        every { runRepo.startRun(any(), any()) } returns 10
        val partialRun = EngineRun(10, "PAYROLL", now.toString(), LocalDateTime.now(), LocalDateTime.now(),
            EngineRunStatus.PARTIAL_FAILURE, 2, 0, 1, "Bob: falha de banco")
        every { runRepo.findById(10) } returns partialRun

        val recurrenceEngine = mockk<RecurrenceEngine>(relaxed = true)
        every { recurrenceEngine.generateAhead(any()) } returns emptyList()

        val orchestrator = EngineOrchestrator(payrollEngine, recurrenceEngine, runRepo, AlwaysLocker)
        val run = orchestrator.runPayrollForMonth(now)

        assertEquals(EngineRunStatus.PARTIAL_FAILURE, run.status)
        verify { runRepo.finishPartial(10, 2, 0, 1, any()) }
        verify(exactly = 0) { runRepo.finishSuccess(any(), any(), any()) }
        verify(exactly = 0) { runRepo.finishFailed(any(), any()) }
    }

    // ── TB2 ────────────────────────────────────────────────────────────────────

    @Test
    fun `TB2 todos os funcionarios falham status e FAILED nao PARTIAL`() {
        val empRepo     = mockk<EmployeeRepository>()
        val txRepo      = mockk<TransactionRepository>()
        val txService   = mockk<TransactionService>()
        val accountRepo = mockk<FinancialAccountRepository>()
        val runRepo     = mockk<EngineRunRepository>(relaxed = true)

        every { accountRepo.findAll() }                               returns listOf(FinancialAccount(id = 1, name = "Caixa"))
        every { empRepo.getAllActive() }                               returns listOf(employee(1, "Ana"), employee(2, "Bob"))
        every { txRepo.existsPaymentForEmployee(any(), any()) }       throws RuntimeException("DB offline")
        every { txService.create(any()) }                             returns 1

        val payrollEngine = PayrollEngine(empRepo, txRepo, txService, accountRepo)
        val results = payrollEngine.generateForMonth(now)

        assertEquals(0, results.sumOf { it.generated })
        assertEquals(2, results.sumOf { it.failed })

        every { runRepo.startRun(any(), any()) } returns 20
        val failedRun = EngineRun(20, "PAYROLL", now.toString(), LocalDateTime.now(), LocalDateTime.now(),
            EngineRunStatus.FAILED, 0, 0, 2, "Ana: DB offline; Bob: DB offline")
        every { runRepo.findById(20) } returns failedRun

        val recurrenceEngine = mockk<RecurrenceEngine>(relaxed = true)
        val orchestrator = EngineOrchestrator(payrollEngine, recurrenceEngine, runRepo, AlwaysLocker)
        val run = orchestrator.runPayrollForMonth(now)

        assertEquals(EngineRunStatus.FAILED, run.status)
        verify { runRepo.finishFailed(20, any()) }
        verify(exactly = 0) { runRepo.finishPartial(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { runRepo.finishSuccess(any(), any(), any()) }
    }

    // ── TB3 ────────────────────────────────────────────────────────────────────

    @Test
    fun `TB3 orquestrador com resultados parciais chama finishPartial nunca finishSuccess`() {
        val payrollEngine    = mockk<PayrollEngine>()
        val recurrenceEngine = mockk<RecurrenceEngine>(relaxed = true)
        val runRepo          = mockk<EngineRunRepository>(relaxed = true)

        every { recurrenceEngine.generateAhead(any()) } returns emptyList()
        every { runRepo.startRun(any(), any()) } returns 30
        val partialRun = EngineRun(30, "PAYROLL", now.toString(), LocalDateTime.now(), LocalDateTime.now(),
            EngineRunStatus.PARTIAL_FAILURE, 1, 0, 1, "Bob: timeout")
        every { runRepo.findById(30) } returns partialRun
        every { payrollEngine.generateForMonth(now) } returns listOf(
            br.com.sisgfin.employees.PayrollGenerationResult(1, 0, "Ana"),
            br.com.sisgfin.employees.PayrollGenerationResult(0, 0, "Bob", failed = 1, failureReason = "timeout")
        )

        val orchestrator = EngineOrchestrator(payrollEngine, recurrenceEngine, runRepo, AlwaysLocker)
        orchestrator.runPayrollForMonth(now)

        verify { runRepo.finishPartial(30, 1, 0, 1, match { it.contains("Bob") }) }
        verify(exactly = 0) { runRepo.finishSuccess(any(), any(), any()) }
    }
}
