package br.com.sisgfin.recurrence

import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class RecurrenceGenerationResult(
    val templateId: Int,
    val description: String,
    val generated: Int,
    val skipped: Int
)

class RecurrenceEngine(
    private val templateRepository: RecurrenceTemplateRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionService: TransactionService
) {
    // Gera lançamentos para todos os templates ativos até `monthsAhead` meses à frente.
    // Chamado na abertura do app e após salvar/reativar um template.
    fun generateAhead(monthsAhead: Int = 2): List<RecurrenceGenerationResult> {
        val today   = LocalDate.now()
        val horizon = today.plusMonths(monthsAhead.toLong())
        return templateRepository.findAllActive().map { template ->
            generateForTemplate(template, today, horizon)
        }
    }

    // Gera apenas para um template específico (chamado ao salvar/reativar).
    fun generateForTemplate(templateId: Int): RecurrenceGenerationResult? {
        val template = templateRepository.findById(templateId) ?: return null
        if (!template.isActive) return null
        val today   = LocalDate.now()
        val horizon = today.plusMonths(2)
        return generateForTemplate(template, today, horizon)
    }

    private fun generateForTemplate(
        template: RecurrenceTemplate,
        from: LocalDate,
        horizon: LocalDate
    ): RecurrenceGenerationResult {
        var generated = 0
        var skipped   = 0

        val dates = nextDueDates(template, from, horizon)
        for (dueDate in dates) {
            if (transactionRepository.existsGeneratedFor(template.id, dueDate)) {
                skipped++
                continue
            }
            transactionService.createFromRecurrence(
                Transaction(
                    type                 = template.type,
                    status               = TransactionStatus.PENDING,
                    description          = template.description,
                    amount               = template.amount,
                    issueDate            = LocalDateTime.now(),
                    dueDate              = dueDate.atStartOfDay(),
                    accountId            = template.accountId,
                    supplierId           = template.supplierId,
                    categoryId           = template.categoryId,
                    costCenterId         = template.costCenterId,
                    documentType         = template.documentType,
                    notes                = template.notes,
                    recurrenceTemplateId = template.id,
                    contractId           = template.contractId
                )
            )
            generated++
        }
        return RecurrenceGenerationResult(template.id, template.description, generated, skipped)
    }

    private fun nextDueDates(
        template: RecurrenceTemplate,
        from: LocalDate,
        horizon: LocalDate
    ): List<LocalDate> = RecurrenceDateCalculator.nextDueDates(template, from, horizon)
}
