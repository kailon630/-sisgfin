package br.com.sisgfin.payroll

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class PayrollImportService(
    private val employeeRepository: EmployeeRepository,
    private val transactionService: TransactionService
) {
    private val parser = PayrollXlsxParser()
    private val monthFmt = DateTimeFormatter.ofPattern("MMM/yyyy", Locale.forLanguageTag("pt-BR"))

    /**
     * Analisa o arquivo XLSX e enriquece cada entrada com lookup de CPF no BD.
     * Não cria transações — retorna o preview para revisão do usuário.
     */
    fun import(
        file: File,
        accountId: Int,
        categoryId: Int,
        costCenterId: Int?,
        referenceMonth: YearMonth,
        userId: Int
    ): PayrollImportResult {
        val (rawEntries, parserWarnings) = parser.parse(file)
        val allWarnings = parserWarnings.toMutableList()

        val entries = rawEntries.map { raw ->
            val employee = employeeRepository.findByCpf(raw.cpf)
            val employeeFound = employee != null

            val (adiantamentoDueDate, liquidoDueDate) = calculateDates(employee, referenceMonth)

            val warning: String? = if (employee == null)
                "CPF ${raw.cpf.formatCpf()} não localizado nos funcionários cadastrados"
            else null

            PayrollEntry(
                raw = raw,
                employeeId = employee?.id,
                adiantamentoDueDate = adiantamentoDueDate,
                liquidoDueDate = liquidoDueDate,
                employeeFound = employeeFound,
                warningMessage = warning
            )
        }

        allWarnings.addAll(
            entries.filter { !it.employeeFound }
                .map { "Não localizado: ${it.nome} (CPF: ${it.raw.cpf.formatCpf()})" }
        )

        return PayrollImportResult(
            entries = entries,
            notFoundCount = entries.count { !it.employeeFound },
            warnings = allWarnings,
            referenceMonth = referenceMonth
        )
    }

    /**
     * Confirma a importação: cancela lançamentos anteriores do PayrollEngine e cria os novos
     * com os valores reais da folha. Processa apenas entradas com employeeFound == true.
     * Retorna o total de transações criadas.
     */
    fun confirm(
        result: PayrollImportResult,
        accountId: Int,
        categoryId: Int,
        costCenterId: Int?,
        userId: Int
    ): Int {
        val monthLabel = result.referenceMonth.format(monthFmt).uppercase()
        var created = 0

        result.entries.filter { it.employeeFound }.forEach { entry ->
            // Cancela lançamentos do PayrollEngine (salário fixo) para evitar duplicatas
            transactionService.cancelPendingPayrollForMonth(entry.employeeId!!, result.referenceMonth)

            val now = LocalDateTime.now()

            // 1ª parcela: Adiantamento (só criado se valor > 0)
            if (!entry.adiantamento.isZero()) {
                transactionService.createFromPayrollImport(
                    Transaction(
                        type = TransactionType.EXPENSE,
                        status = TransactionStatus.PENDING,
                        description = "Adiantamento $monthLabel — ${entry.nome}",
                        amount = entry.adiantamento,
                        issueDate = now,
                        dueDate = entry.adiantamentoDueDate.atStartOfDay(),
                        accountId = accountId,
                        costCenterId = costCenterId,
                        categoryId = categoryId,
                        employeeId = entry.employeeId,
                        createdBy = userId
                    )
                )
                created++
            }

            // 2ª parcela: Salário (líquido) — sempre criado
            transactionService.createFromPayrollImport(
                Transaction(
                    type = TransactionType.EXPENSE,
                    status = TransactionStatus.PENDING,
                    description = "Salário $monthLabel — ${entry.nome}",
                    amount = entry.liquido,
                    issueDate = now,
                    dueDate = entry.liquidoDueDate.atStartOfDay(),
                    accountId = accountId,
                    costCenterId = costCenterId,
                    categoryId = categoryId,
                    employeeId = entry.employeeId,
                    createdBy = userId
                )
            )
            created++
        }

        return created
    }

    // Calcula datas de vencimento das duas parcelas.
    // Se o funcionário tiver paymentDays configurado (ex: "20,5"), usa esses dias.
    // Caso contrário: dia 20 do mês de referência e dia 5 do mês seguinte.
    private fun calculateDates(employee: Employee?, month: YearMonth): Pair<LocalDate, LocalDate> {
        val days = employee?.paymentDays
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }

        val adiantamentoDay = days?.getOrNull(0)?.coerceIn(1, month.lengthOfMonth()) ?: 20
        val nextMonth = month.plusMonths(1)
        val liquidoDay = days?.getOrNull(1)?.coerceIn(1, nextMonth.lengthOfMonth()) ?: 5

        return month.atDay(adiantamentoDay) to nextMonth.atDay(liquidoDay)
    }

    private fun String.formatCpf(): String =
        if (length == 11) "${substring(0, 3)}.${substring(3, 6)}.${substring(6, 9)}-${substring(9)}"
        else this
}
