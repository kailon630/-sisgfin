package br.com.sisgfin.budget

import br.com.sisgfin.AuditRepository
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.core.domain.AuditedCrudService
import br.com.sisgfin.financial.money.Money
import java.time.LocalDate

class BudgetItemService(
    private val budgetRepository: BudgetItemRepository,
    private val costCenterRepository: CostCenterRepository,
    auditRepository: AuditRepository,
    sessionManager: SessionManager
) : AuditedCrudService<BudgetItem>(
    repository = budgetRepository,
    auditRepository = auditRepository,
    sessionManager = sessionManager,
    entityType = "BUDGET_ITEM",
    displayName = { "CC#${it.costCenterId}/Cat#${it.categoryId}/${it.year}" },
    withCreatedBy = { item, userId -> item.copy(createdBy = userId) },
    withActiveFlag = { item, active -> item.copy(isActive = active) },
    isActive = { it.isActive }
) {
    override fun save(item: BudgetItem) {
        if (item.costCenterId == 0) throw IllegalArgumentException("Centro de custo é obrigatório.")
        if (item.categoryId == 0) throw IllegalArgumentException("Categoria é obrigatória.")
        if (item.year < 2000 || item.year > 2100) throw IllegalArgumentException("Ano inválido.")
        if (item.annualAmount.isNegative()) throw IllegalArgumentException("Dotação anual não pode ser negativa.")
        if (item.monthlyAmount.isNegative()) throw IllegalArgumentException("Dotação mensal não pode ser negativa.")

        // RN-28: bloqueia edição de orçamento de projeto encerrado
        val costCenter = costCenterRepository.findById(item.costCenterId)
        if (costCenter != null && costCenter.isEncerrado) {
            throw IllegalArgumentException(
                "Não é possível editar o orçamento do centro de custo \"${costCenter.name}\" porque ele está encerrado."
            )
        }

        val duplicate = budgetRepository.findDuplicate(item.costCenterId, item.categoryId, item.year, excludeId = item.id)
        if (duplicate != null) {
            throw IllegalArgumentException(
                "Já existe uma dotação para este centro de custo e categoria no ano ${item.year}."
            )
        }
        super.save(item)
    }

    // RN-24: calcula o realizado para um item de orçamento
    fun getRealized(item: BudgetItem): Money =
        budgetRepository.sumRealized(item.costCenterId, item.categoryId, item.year)

    fun findByYear(year: Int): List<BudgetItem> = budgetRepository.findByYear(year)

    fun currentYear(): Int = LocalDate.now().year
}
