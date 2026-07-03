package br.com.sisgfin.budget

import br.com.sisgfin.core.domain.Activatable
import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.financial.money.Money
import java.time.LocalDateTime

data class BudgetItem(
    override val id: Int = 0,
    val costCenterId: Int,
    val categoryId: Int,
    val year: Int,
    val monthlyAmount: Money,
    val annualAmount: Money,
    val notes: String? = null,
    override val isActive: Boolean = true,
    val createdBy: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : Identifiable, Activatable

data class BudgetItemSummary(
    val item: BudgetItem,
    val costCenterName: String,
    val categoryCode: String,
    val categoryName: String,
    val annualRealized: Money,
    val annualBalance: Money,
    val utilizationPct: Double
) {
    val isOverBudget: Boolean get() = annualBalance.isNegative()
}

// RN-25: saldo disponível de uma rubrica consultado de fora do BudgetViewModel
data class BudgetBalance(
    val annualAmount: Money,
    val realized: Money,
    val available: Money,       // annualAmount − realized
    val utilizationPct: Double  // 0..100+
) {
    val isOverBudget: Boolean get() = available.isNegative()
}
