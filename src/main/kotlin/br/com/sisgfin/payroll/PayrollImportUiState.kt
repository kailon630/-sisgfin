package br.com.sisgfin.payroll

import br.com.sisgfin.CostCenter
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.financial.categories.ExpenseCategory
import br.com.sisgfin.financial.money.Money
import java.io.File
import java.time.YearMonth

enum class PayrollImportStep { SELECT_FILE, PARSING, PREVIEW, CONFIRMING, DONE }

data class PayrollImportSummary(
    val transactionsCreated: Int,
    val employeesProcessed: Int,
    val notFoundCount: Int,
    val warningCount: Int,
    val notFoundEntries: List<PayrollEntry>
)

data class PayrollImportUiState(
    val step: PayrollImportStep = PayrollImportStep.SELECT_FILE,
    val selectedFile: File? = null,
    val result: PayrollImportResult? = null,
    val selectedIndices: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val summary: PayrollImportSummary? = null,
    // Dados de referência para o formulário
    val accounts: List<FinancialAccount> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val costCenters: List<CostCenter> = emptyList(),
    // Seleções do usuário
    val selectedAccountId: Int? = null,
    val selectedCategoryId: Int? = null,
    val selectedCostCenterId: Int? = null,
    val referenceMonth: YearMonth = YearMonth.now(),
    // Cadastro de funcionário ausente
    val registeringEntry: PayrollEntry? = null,
    val registeredCpfs: Set<String> = emptySet(),
    // Remessa bancária
    val exportLoading: Boolean = false,
    val missingBankData: List<String> = emptyList()
) {
    val canAdvance: Boolean
        get() = selectedFile != null && selectedAccountId != null && selectedCategoryId != null

    val totalSelected: Int
        get() = selectedIndices.size

    val totalAmountSelected: Money
        get() = result?.entries
            ?.filterIndexed { i, _ -> i in selectedIndices }
            ?.fold(Money.ZERO) { acc, e -> acc + e.adiantamento + e.liquido }
            ?: Money.ZERO
}
