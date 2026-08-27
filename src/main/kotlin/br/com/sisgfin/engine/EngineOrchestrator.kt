package br.com.sisgfin.engine

import br.com.sisgfin.core.errors.AppLogger
import br.com.sisgfin.core.errors.ErrorClassifier
import br.com.sisgfin.employees.PayrollEngine
import br.com.sisgfin.recurrence.RecurrenceEngine
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.YearMonth

private const val LOCK_PAYROLL    = 8471001L
private const val LOCK_RECURRENCE = 8471002L

interface EngineLocker {
    fun <T> tryLock(key: Long, block: () -> T): T?
}

object AdvisoryLocker : EngineLocker {
    override fun <T> tryLock(key: Long, block: () -> T): T? = transaction {
        val acquired = exec("SELECT pg_try_advisory_lock($key)") { rs ->
            rs.next(); rs.getBoolean(1)
        } ?: false
        if (!acquired) return@transaction null
        try { block() } finally { exec("SELECT pg_advisory_unlock($key)") {} }
    }
}

class EngineOrchestrator(
    private val payrollEngine: PayrollEngine,
    private val recurrenceEngine: RecurrenceEngine,
    private val runRepository: EngineRunRepository,
    private val locker: EngineLocker = AdvisoryLocker
) {
    fun runPayrollForMonth(yearMonth: YearMonth): EngineRun {
        val ref   = yearMonth.toString()  // "2026-08"
        val runId = runRepository.startRun("PAYROLL", ref)

        val acquired = locker.tryLock(LOCK_PAYROLL) {
            runCatching { payrollEngine.generateForMonth(yearMonth) }
                .onSuccess { results ->
                    val created = results.sumOf { it.generated }
                    val skipped = results.sumOf { it.skipped }
                    val failed  = results.sumOf { it.failed }
                    when {
                        failed > 0 && created > 0 -> {
                            val errors = results.filter { it.failed > 0 }
                                .joinToString("; ") { "${it.employeeName}: ${it.failureReason ?: "erro"}" }
                            runRepository.finishPartial(runId, created, skipped, failed, errors)
                        }
                        failed > 0 -> {
                            val errors = results.filter { it.failed > 0 }
                                .joinToString("; ") { "${it.employeeName}: ${it.failureReason ?: "erro"}" }
                            runRepository.finishFailed(runId, errors)
                        }
                        else -> runRepository.finishSuccess(runId, created, skipped)
                    }
                }
                .onFailure { e ->
                    AppLogger.error(ErrorClassifier.classify(e))
                    runRepository.finishFailed(runId, e.message ?: "Erro desconhecido")
                }
        }
        if (acquired == null) runRepository.finishSkipped(runId)

        return runRepository.findById(runId)!!
    }

    fun runRecurrence(monthsAhead: Int = 2): EngineRun {
        val runId = runRepository.startRun("RECURRENCE", null)

        val acquired = locker.tryLock(LOCK_RECURRENCE) {
            runCatching { recurrenceEngine.generateAhead(monthsAhead) }
                .onSuccess { results ->
                    val created = results.sumOf { it.generated }
                    val skipped = results.sumOf { it.skipped }
                    val failed  = results.sumOf { it.failed }
                    when {
                        failed > 0 && created > 0 -> {
                            val errors = results.filter { it.failed > 0 }
                                .joinToString("; ") { "${it.description}: ${it.failureReason ?: "erro"}" }
                            runRepository.finishPartial(runId, created, skipped, failed, errors)
                        }
                        failed > 0 -> {
                            val errors = results.filter { it.failed > 0 }
                                .joinToString("; ") { "${it.description}: ${it.failureReason ?: "erro"}" }
                            runRepository.finishFailed(runId, errors)
                        }
                        else -> runRepository.finishSuccess(runId, created, skipped)
                    }
                }
                .onFailure { e ->
                    AppLogger.error(ErrorClassifier.classify(e))
                    runRepository.finishFailed(runId, e.message ?: "Erro desconhecido")
                }
        }
        if (acquired == null) runRepository.finishSkipped(runId)

        return runRepository.findById(runId)!!
    }

    fun findLastPayrollRun(yearMonth: YearMonth): EngineRun? =
        runRepository.findLastSuccessByEngineAndReference("PAYROLL", yearMonth.toString())
}
