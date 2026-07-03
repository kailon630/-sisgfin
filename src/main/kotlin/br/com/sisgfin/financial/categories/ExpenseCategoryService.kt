package br.com.sisgfin.financial.categories

import br.com.sisgfin.AuditRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.core.domain.AuditedCrudService
import br.com.sisgfin.financial.transactions.TransactionRepository

class ExpenseCategoryService(
    private val categoryRepository: ExpenseCategoryRepository,
    private val transactionRepository: TransactionRepository,
    auditRepository: AuditRepository,
    sessionManager: SessionManager
) : AuditedCrudService<ExpenseCategory>(
    repository    = categoryRepository,
    auditRepository   = auditRepository,
    sessionManager    = sessionManager,
    entityType        = "EXPENSE_CATEGORY",
    displayName       = { "${it.code} - ${it.name}" },
    withCreatedBy     = { item, _ -> item },
    withActiveFlag    = { item, active -> item.copy(isActive = active) },
    isActive          = { it.isActive }
) {
    // RN-10: bloqueia inativação se há lançamentos vinculados
    override fun toggleActive(id: Int) {
        val category = categoryRepository.findById(id) ?: return
        if (category.isActive && transactionRepository.existsByCategoryId(id)) {
            throw IllegalStateException(
                "A categoria \"${category.code} - ${category.name}\" possui lançamentos vinculados " +
                "e não pode ser inativada. Remova ou reclassifique os lançamentos antes de inativar."
            )
        }
        super.toggleActive(id)
    }
}
