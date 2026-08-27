package br.com.sisgfin.employees

import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionOrigin
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import br.com.sisgfin.core.errors.AppLogger
import br.com.sisgfin.core.errors.ErrorClassifier
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt       = DateTimeFormatter.ofPattern("MMMM/yyyy", Locale("pt", "BR"))
private val competenciaFmt = DateTimeFormatter.ofPattern("MM/yyyy")

data class PayrollGenerationResult(
    val generated: Int,
    val skipped: Int,
    val employeeName: String,
    val failed: Int = 0,
    val failureReason: String? = null
)

class PayrollEngine(
    private val employeeRepository: EmployeeRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionService: TransactionService,
    private val accountRepository: FinancialAccountRepository
) {
    // Gera lançamentos para todos os funcionários com paymentDays configurado.
    // Chamado na abertura do app para o mês corrente e o próximo.
    fun generateForMonth(yearMonth: YearMonth): List<PayrollGenerationResult> {
        val defaultAccountId = accountRepository.findAll()
            .firstOrNull { it.isActive }?.id
            ?: return emptyList()

        return employeeRepository.getAllActive()
            .filter { it.effectivePaymentDays().isNotEmpty() }
            .map { employee ->
                runCatching {
                    var generated = 0
                    var skipped   = 0
                    for (day in employee.effectivePaymentDays()) {
                        val dueDate = yearMonth.atDay(day.coerceAtMost(yearMonth.lengthOfMonth()))
                        if (transactionRepository.existsPaymentForEmployee(employee.id, dueDate)) {
                            skipped++
                            continue
                        }
                        val monthLabel  = yearMonth.format(monthFmt).replaceFirstChar { it.uppercase() }
                        val competencia = yearMonth.format(competenciaFmt)
                        transactionService.create(
                            Transaction(
                                type           = TransactionType.EXPENSE,
                                status         = TransactionStatus.PENDING,
                                description    = "Pagamento ${employee.name} — $monthLabel",
                                amount         = employee.salary,
                                issueDate      = LocalDateTime.now(),
                                dueDate        = dueDate.atStartOfDay(),
                                accountId      = defaultAccountId,
                                employeeId     = employee.id,
                                origin         = TransactionOrigin.PAYROLL_ENGINE,
                                documentType   = "FOLHA",
                                documentNumber = competencia
                            )
                        )
                        generated++
                    }
                    PayrollGenerationResult(generated, skipped, employee.name)
                }.getOrElse { e ->
                    AppLogger.error(ErrorClassifier.classify(e))
                    PayrollGenerationResult(0, 0, employee.name, failed = 1, failureReason = e.message ?: "Erro desconhecido")
                }
            }
    }

    // Gera para um único funcionário (chamado após salvar/reativar).
    // Janela: mês corrente + próximo mês. Não passa por generateForMonth para não
    // processar outros funcionários e evitar erros de validação cruzada.
    fun generateForEmployee(employeeId: Int): List<PayrollGenerationResult> {
        val employee = employeeRepository.getById(employeeId) ?: return emptyList()
        if (!employee.active || employee.effectivePaymentDays().isEmpty()) return emptyList()
        if (employee.salary.isZero() || employee.salary.isNegative()) return emptyList()

        val defaultAccountId = accountRepository.findAll()
            .firstOrNull { it.isActive }?.id
            ?: return emptyList()

        val now = YearMonth.now()
        var generated = 0
        var skipped   = 0

        for (yearMonth in listOf(now, now.plusMonths(1))) {
            for (day in employee.effectivePaymentDays()) {
                val dueDate = yearMonth.atDay(day.coerceAtMost(yearMonth.lengthOfMonth()))
                if (transactionRepository.existsPaymentForEmployee(employee.id, dueDate)) {
                    skipped++
                    continue
                }
                val monthLabel  = yearMonth.format(monthFmt).replaceFirstChar { it.uppercase() }
                val competencia = yearMonth.format(competenciaFmt)
                transactionService.create(
                    Transaction(
                        type           = TransactionType.EXPENSE,
                        status         = TransactionStatus.PENDING,
                        description    = "Pagamento ${employee.name} — $monthLabel",
                        amount         = employee.salary,
                        issueDate      = LocalDateTime.now(),
                        dueDate        = dueDate.atStartOfDay(),
                        accountId      = defaultAccountId,
                        employeeId     = employee.id,
                        origin         = TransactionOrigin.PAYROLL_ENGINE,
                        documentType   = "FOLHA",
                        documentNumber = competencia
                    )
                )
                generated++
            }
        }

        return listOf(PayrollGenerationResult(generated, skipped, employee.name))
    }
}
