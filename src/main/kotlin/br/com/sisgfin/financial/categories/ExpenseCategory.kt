package br.com.sisgfin.financial.categories

import br.com.sisgfin.core.domain.Activatable
import br.com.sisgfin.core.domain.Identifiable

/**
 * Categoria do plano de contas (ex: "1.1 - Vencimentos e Salários").
 * Persistida em `expense_categories`.
 */
data class ExpenseCategory(
    override val id: Int = 0,
    val code: String,
    val name: String,
    val description: String? = null,
    val groupCode: String? = null,
    val groupName: String? = null,
    val isIncome: Boolean = false,
    override val isActive: Boolean = true
) : Identifiable, Activatable {
    val displayName: String get() = "$code - $name"
}
