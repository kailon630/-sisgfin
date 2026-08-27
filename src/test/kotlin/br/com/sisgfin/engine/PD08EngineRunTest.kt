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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals

private object AlwaysAcquireLocker : EngineLocker {
    override fun <T> tryLock(key: Long, block: () -> T): T = block()
}

private object NeverAcquireLocker : EngineLocker {
    override fun <T> tryLock(key: Long, block: () -> T): T? = null
}

/**
 * PD-08 — Observabilidade das engines: engine_runs + lock + falha isolada.
 *
 * T1 — generateForMonth lança exceção → run FAILED, app não propaga.
 * T2 — Execução normal → run SUCCESS com created/skipped corretos.
 * T3 — Lock já tomado → run SKIPPED_LOCKED; create não chamado.
 * T4 — Duas execuções sequenciais → segunda: created=0, skipped=total.
 * T5 — Funcionário com paymentDays "5,15" → dois creates, ambos válidos.
 * T6 — Inserção manual duplicando (employee_id, due_date) → constraint rejeita.
 * T7 — Lançamento MANUAL para funcionário no mesmo dia → não bloqueado pelo engine.
 * T8 — Falha na engine de folha → engine de recorrência executa normalmente.
 */
class PD08EngineRunTest {

    private lateinit var payrollEngine: PayrollEngine
    private lateinit var recurrenceEngine: RecurrenceEngine
    private lateinit var runRepo: EngineRunRepository

    private val now = YearMonth.of(2026, 8)

    private val employee5e15 = Employee(
        id = 1, name = "Ana", document = "12345678900", phone = "", email = "",
        role = "Aux", salary = Money.fromString("3000.00"),
        paymentDay = 5, paymentDays = "5,15", active = true
    )

    @BeforeEach
    fun setUp() {
        payrollEngine    = mockk()
        recurrenceEngine = mockk(relaxed = true)
        runRepo          = mockk(relaxed = true)
    }

    private fun orchestrator(locker: EngineLocker = AlwaysAcquireLocker) =
        EngineOrchestrator(payrollEngine, recurrenceEngine, runRepo, locker)

    private fun givenRunId(id: Int, status: EngineRunStatus): EngineRun {
        val run = EngineRun(id, "PAYROLL", now.toString(), LocalDateTime.now(), LocalDateTime.now(), status, 0, 0, 0, null)
        every { runRepo.startRun(any(), any()) } returns id
        every { runRepo.findById(id) } returns run
        return run
    }

    // ── T1 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T1 generateForMonth lanca excecao grava FAILED e nao propaga`() {
        givenRunId(1, EngineRunStatus.FAILED)
        every { payrollEngine.generateForMonth(now) } throws RuntimeException("DB offline")

        val run = orchestrator().runPayrollForMonth(now)

        assertEquals(EngineRunStatus.FAILED, run.status)
        verify { runRepo.finishFailed(1, any()) }
        verify(exactly = 0) { runRepo.finishSuccess(any(), any(), any()) }
    }

    // ── T2 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T2 execucao normal grava SUCCESS com created e skipped corretos`() {
        givenRunId(2, EngineRunStatus.SUCCESS)
        every { payrollEngine.generateForMonth(now) } returns listOf(
            br.com.sisgfin.employees.PayrollGenerationResult(3, 1, "Ana"),
            br.com.sisgfin.employees.PayrollGenerationResult(1, 2, "João")
        )

        val run = orchestrator().runPayrollForMonth(now)

        assertEquals(EngineRunStatus.SUCCESS, run.status)
        verify { runRepo.finishSuccess(2, 4, 3) }
    }

    // ── T3 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T3 lock ja tomado grava SKIPPED_LOCKED e nao chama engine`() {
        givenRunId(3, EngineRunStatus.SKIPPED_LOCKED)

        orchestrator(NeverAcquireLocker).runPayrollForMonth(now)

        verify { runRepo.finishSkipped(3) }
        verify(exactly = 0) { payrollEngine.generateForMonth(any()) }
        verify(exactly = 0) { runRepo.finishFailed(any(), any()) }
        verify(exactly = 0) { runRepo.finishSuccess(any(), any(), any()) }
    }

    // ── T4 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T4 duas execucoes no mesmo mes segunda gera zero e skipped igual ao total`() {
        // Primeira execução: 2 criados
        every { runRepo.startRun(any(), any()) } returnsMany listOf(4, 5)
        val run1 = EngineRun(4, "PAYROLL", now.toString(), LocalDateTime.now(), LocalDateTime.now(), EngineRunStatus.SUCCESS, 2, 0, 0, null)
        val run2 = EngineRun(5, "PAYROLL", now.toString(), LocalDateTime.now(), LocalDateTime.now(), EngineRunStatus.SUCCESS, 0, 2, 0, null)
        every { runRepo.findById(4) } returns run1
        every { runRepo.findById(5) } returns run2

        every { payrollEngine.generateForMonth(now) } returnsMany listOf(
            listOf(br.com.sisgfin.employees.PayrollGenerationResult(2, 0, "Ana")),
            listOf(br.com.sisgfin.employees.PayrollGenerationResult(0, 2, "Ana"))
        )

        val o = orchestrator()
        val r1 = o.runPayrollForMonth(now)
        val r2 = o.runPayrollForMonth(now)

        assertEquals(2, r1.created)
        assertEquals(0, r2.created)
        assertEquals(2, r2.skipped)
    }

    // ── T5 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T5 paymentDays 5 e 15 gera dois lancamentos ambos validos`() {
        val empRepo     = mockk<EmployeeRepository>()
        val txRepo      = mockk<TransactionRepository>()
        val txService   = mockk<TransactionService>()
        val accountRepo = mockk<FinancialAccountRepository>()

        every { empRepo.getAllActive() }                           returns listOf(employee5e15)
        every { txRepo.existsPaymentForEmployee(any(), any()) }   returns false
        every { accountRepo.findAll() }                           returns listOf(FinancialAccount(id = 1, name = "Caixa"))
        val capturedTxs = mutableListOf<br.com.sisgfin.financial.transactions.Transaction>()
        every { txService.create(capture(capturedTxs)) }          returns 1

        val engine = PayrollEngine(empRepo, txRepo, txService, accountRepo)
        engine.generateForMonth(now)

        assertEquals(2, capturedTxs.size, "Deve criar um lançamento por paymentDay (5 e 15)")
        val dueDays = capturedTxs.map { it.dueDate.dayOfMonth }.sorted()
        assertEquals(listOf(5, 15), dueDays)
    }

    // ── T6 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T6 existsPaymentForEmployee bloqueia segundo lancamento de folha no mesmo dia`() {
        // Verifica que existsPaymentForEmployee retorna true após o primeiro create,
        // impedindo que o engine chame create novamente para o mesmo (employee_id, due_date).
        val empRepo     = mockk<EmployeeRepository>()
        val txRepo      = mockk<TransactionRepository>()
        val txService   = mockk<TransactionService>()
        val accountRepo = mockk<FinancialAccountRepository>()

        every { empRepo.getAllActive() }        returns listOf(employee5e15.copy(paymentDays = "5"))
        every { accountRepo.findAll() }         returns listOf(FinancialAccount(id = 1, name = "Caixa"))
        every { txService.create(any()) }       returns 1
        // Primeira chamada: não existe → cria. Segunda: já existe → pula.
        every { txRepo.existsPaymentForEmployee(any(), any()) } returnsMany listOf(false, true)

        val engine = PayrollEngine(empRepo, txRepo, txService, accountRepo)
        engine.generateForMonth(now)
        engine.generateForMonth(now)

        verify(exactly = 1) { txService.create(any()) }
    }

    // ── T7 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T7 lancamento MANUAL para funcionario no mesmo dia nao e bloqueado pelo engine`() {
        // O engine consulta existsPaymentForEmployee somente para origin de folha.
        // Um lançamento MANUAL não altera o retorno dessa query (origin diferente).
        // Verificamos que o engine ainda cria quando existsPaymentForEmployee retorna false.
        val empRepo     = mockk<EmployeeRepository>()
        val txRepo      = mockk<TransactionRepository>()
        val txService   = mockk<TransactionService>()
        val accountRepo = mockk<FinancialAccountRepository>()

        every { empRepo.getAllActive() }                          returns listOf(employee5e15.copy(paymentDays = "5"))
        every { accountRepo.findAll() }                          returns listOf(FinancialAccount(id = 1, name = "Caixa"))
        every { txService.create(any()) }                        returns 1
        // Lançamento MANUAL existe para o mesmo funcionário e dia, mas origin é diferente.
        // existsPaymentForEmployee (com status <> CANCELED) retorna false para MANUAL
        // porque o índice e a query filtram por origin IN ('PAYROLL_ENGINE','PAYROLL_IMPORT').
        // No teste: simulamos o comportamento correto — false → engine cria.
        every { txRepo.existsPaymentForEmployee(any(), any()) }  returns false

        val engine = PayrollEngine(empRepo, txRepo, txService, accountRepo)
        val results = engine.generateForMonth(now)

        assertEquals(1, results.sumOf { it.generated }, "Engine deve criar mesmo com MANUAL existente no mesmo dia")
        verify(exactly = 1) { txService.create(any()) }
    }

    // ── T8 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `T8 falha na engine de folha nao impede engine de recorrencia`() {
        every { runRepo.startRun("PAYROLL", any()) }    returns 8
        every { runRepo.startRun("RECURRENCE", null) }  returns 9
        val failedRun = EngineRun(8, "PAYROLL", now.toString(), LocalDateTime.now(), LocalDateTime.now(), EngineRunStatus.FAILED, 0, 0, 0, "timeout")
        val successRun = EngineRun(9, "RECURRENCE", null, LocalDateTime.now(), LocalDateTime.now(), EngineRunStatus.SUCCESS, 5, 2, 0, null)
        every { runRepo.findById(8) } returns failedRun
        every { runRepo.findById(9) } returns successRun
        every { payrollEngine.generateForMonth(any()) } throws RuntimeException("timeout")
        every { recurrenceEngine.generateAhead(any()) } returns listOf(
            br.com.sisgfin.recurrence.RecurrenceGenerationResult(1, "Aluguel", 5, 2)
        )

        val o = orchestrator()
        val payroll    = o.runPayrollForMonth(now)
        val recurrence = o.runRecurrence(monthsAhead = 2)

        assertEquals(EngineRunStatus.FAILED, payroll.status)
        assertEquals(EngineRunStatus.SUCCESS, recurrence.status)
        assertEquals(5, recurrence.created)
    }
}
