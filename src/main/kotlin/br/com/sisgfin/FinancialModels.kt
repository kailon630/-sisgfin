package br.com.sisgfin

import br.com.sisgfin.core.domain.Activatable
import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.suppliers.EntityType
import java.time.LocalDateTime

data class Supplier(
    override val id: Int = 0,
    val document: String,
    val name: String,
    val tradeName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val pixKey: String? = null,
    val bank: String? = null,
    val agency: String? = null,
    val account: String? = null,
    val notes: String? = null,
    val entityType: EntityType = EntityType.FORNECEDOR,
    override val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: Int? = null
) : Identifiable, Activatable

enum class FinancialAccountType {
    BANK, CASH, SAVINGS, INVESTMENT;

    val displayName: String
        get() = when (this) {
            BANK -> "Conta Bancária"
            CASH -> "Caixa"
            SAVINGS -> "Poupança"
            INVESTMENT -> "Aplicação"
        }
}

data class FinancialAccount(
    override val id: Int = 0,
    val name: String,
    val bankName: String? = null,
    val agency: String? = null,
    val accountNumber: String? = null,
    val accountType: FinancialAccountType = FinancialAccountType.BANK,
    val initialBalance: Money = Money.ZERO,
    val investmentBroker: String? = null,
    override val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: Int? = null
) : Identifiable, Activatable

data class CostCenter(
    override val id: Int = 0,
    val code: String,
    val name: String,
    val description: String? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    override val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: Int? = null
) : Identifiable, Activatable {
    // RN-28: encerrado = inativo OU data de fim já passou
    val isEncerrado: Boolean
        get() = !isActive || (endDate != null && endDate.isBefore(LocalDateTime.now()))
}

