package br.com.sisgfin.reports

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPayment
import br.com.sisgfin.financial.projects.ProjectStatus
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
    val payment: TransactionPayment,
    val creditorName: String?,
    val accountName: String,
    val tcespDesc: String
) {
    val cashEffective: Money get() = payment.cashEffective
    val paymentDate: LocalDate get() = payment.paymentDate
}

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
    val projectsFilter: ProjectsFilter = ProjectsFilter(),
    val projectSummaryRows: List<ProjectSummaryRow> = emptyList(),
    val isLoading: Boolean = false,
    val exportMessage: String? = null,
    val errorMessage: String? = null
)

fun buildTcespDesc(
    tx: Transaction,
    creditorName: String?,
    paymentOrdinal: Int = 1,
    totalPayments: Int? = null
): String {
    val prefix = when (tx.type) {
        TransactionType.INCOME, TransactionType.REVERSAL -> "RECEBIDO DE,"
        else -> "PAGO A,"
    }
    val creditor = (creditorName ?: "CREDOR NÃO IDENTIFICADO").uppercase()
    val docPart = when {
        tx.documentType != null && tx.documentNumber != null ->
            " CF ${tx.documentType.uppercase()} ${tx.documentNumber}"
        tx.documentType != null -> " CF ${tx.documentType.uppercase()}"
        tx.documentNumber != null -> " CF DOC ${tx.documentNumber}"
        else -> ""
    }
    // Sem sufixo quando há apenas uma baixa ativa; denominador omitido se título ainda PARTIAL.
    val multiplePayments = (totalPayments ?: paymentOrdinal) > 1
    val ordinalPart = when {
        !multiplePayments -> ""
        totalPayments != null -> " (PAGTO $paymentOrdinal/$totalPayments)"
        else -> " (PAGTO $paymentOrdinal)"
    }
    return "$prefix $creditor$docPart$ordinalPart"
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

// ── Relatório de Projetos ────────────────────────────────────────────────────

data class ProjectsFilter(
    val status: ProjectStatus? = null
)

data class ProjectSummaryRow(
    val projectId: Int,
    val code: String,
    val name: String,
    val status: ProjectStatus,
    val budget: Money?,
    val realized: Money,
    val executionPct: Double
) {
    val isOverBudget: Boolean get() = budget != null && realized > budget
}
