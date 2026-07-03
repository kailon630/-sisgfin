package br.com.sisgfin.reports

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class LivroDiarioFilter(
    val from: LocalDate = LocalDate.now().withDayOfMonth(1),
    val to: LocalDate = LocalDate.now(),
    val accountId: Int? = null
)

data class LivroDiarioEntry(
    val transaction: Transaction,
    val supplierName: String?,
    val accountName: String,
    val tcespDesc: String
)

data class BalanceteFilter(
    val year: Int = LocalDate.now().year,
    val month: Int? = null
)

data class BalanceteRow(
    val costCenterId: Int,
    val costCenterCode: String,
    val costCenterName: String,
    val categoryId: Int,
    val categoryCode: String,
    val categoryName: String,
    val monthlyAmount: Money,
    val annualAmount: Money,
    val realized: Money,
    val balance: Money,
    val utilizationPct: Double
) {
    val isOverBudget: Boolean get() = balance.isNegative()
}

data class ReportsUiState(
    val accounts: List<FinancialAccount> = emptyList(),
    val livroDiarioFilter: LivroDiarioFilter = LivroDiarioFilter(),
    val livroDiarioEntries: List<LivroDiarioEntry> = emptyList(),
    val balanceteFilter: BalanceteFilter = BalanceteFilter(),
    val balanceteRows: List<BalanceteRow> = emptyList(),
    val demonstrativoFilter: DemonstrativoFilter = DemonstrativoFilter(),
    val demonstrativoRows: List<DemonstrativoRow> = emptyList(),
    val isLoading: Boolean = false,
    val exportMessage: String? = null,
    val errorMessage: String? = null
)

fun buildTcespDesc(tx: Transaction, supplierName: String?): String {
    val prefix = when (tx.type) {
        TransactionType.INCOME, TransactionType.REVERSAL -> "RECEBIDO DE,"
        else -> "PAGO A,"
    }
    val creditor = (supplierName ?: tx.description).uppercase()
    val docPart = when {
        tx.documentType != null && tx.documentNumber != null ->
            " CF ${tx.documentType.uppercase()} ${tx.documentNumber}"
        tx.documentType != null -> " CF ${tx.documentType.uppercase()}"
        tx.documentNumber != null -> " CF DOC ${tx.documentNumber}"
        else -> ""
    }
    return "$prefix $creditor$docPart"
}

// ── Demonstrativo Financeiro ─────────────────────────────────────────────────

data class DemonstrativoFilter(
    val from: LocalDate = LocalDate.now().withDayOfMonth(1),
    val to: LocalDate = LocalDate.now()
)

data class DemonstrativoRow(
    val categoryId: Int,
    val categoryCode: String,
    val categoryName: String,
    val groupCode: String?,
    val groupName: String?,
    val isIncome: Boolean,
    val income: Money,
    val expense: Money
) {
    val balance: Money get() = income - expense
}

val MESES_PT = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)
