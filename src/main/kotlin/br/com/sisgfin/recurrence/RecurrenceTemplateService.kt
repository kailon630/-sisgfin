package br.com.sisgfin.recurrence

import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.transactions.TransactionRepository
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceTemplateService(
    private val repository: RecurrenceTemplateRepository,
    private val transactionRepository: TransactionRepository,
    private val engine: RecurrenceEngine,
    private val sessionManager: SessionManager
) {
    fun listAll(): List<RecurrenceTemplate> = repository.findAll()

    fun findById(id: Int): RecurrenceTemplate? = repository.findById(id)

    fun save(template: RecurrenceTemplate): Int {
        require(template.description.isNotBlank()) { "Descrição é obrigatória." }
        require(template.amount.value > java.math.BigDecimal.ZERO) { "Valor deve ser maior que zero." }
        require(template.dayOfMonth in 1..31) { "Dia do mês inválido." }
        if (template.endsAt != null) {
            require(!template.endsAt.isBefore(template.startsAt)) {
                "Data de encerramento deve ser após a data de início."
            }
        }

        val userId = sessionManager.currentUser.value?.id
        val now    = LocalDateTime.now()

        return if (template.id == 0) {
            val id = repository.insert(template.copy(createdBy = userId, createdAt = now, updatedAt = now))
            engine.generateForTemplate(id)
            id
        } else {
            repository.update(template.copy(updatedAt = now))
            engine.generateForTemplate(template.id)
            template.id
        }
    }

    // Pausa o template — lançamentos já gerados são preservados
    fun pause(id: Int) {
        repository.setActive(id, false)
    }

    // Reativa o template e gera os próximos lançamentos
    fun resume(id: Int) {
        repository.setActive(id, true)
        engine.generateForTemplate(id)
    }

    // Pausa e cancela todos os PENDING futuros gerados pelo template
    fun cancelFuture(id: Int) {
        repository.setActive(id, false)
        transactionRepository.cancelFutureByRecurrenceTemplate(id, LocalDate.now())
    }

    fun getHistory(templateId: Int) = transactionRepository.findByRecurrenceTemplate(templateId)
}
