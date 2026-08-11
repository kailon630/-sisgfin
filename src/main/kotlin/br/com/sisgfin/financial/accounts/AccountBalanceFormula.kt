package br.com.sisgfin.financial.accounts

import br.com.sisgfin.financial.money.Money

/**
 * Fórmula única de saldo de conta (RN-04 estendida).
 * Usada por [br.com.sisgfin.FinancialAccountService.calculateBalance] e
 * [br.com.sisgfin.financial.transactions.TransactionRepository.openingBalance]
 * — qualquer alteração deve manter as duas chamadas alinhadas.
 *
 * Estornos são dirigidos por [reversedType] do lançamento original:
 * - crédito: estorno de EXPENSE (devolve ao caixa)
 * - débito: estorno de INCOME / ADJUSTMENT (retira do caixa)
 */
object AccountBalanceFormula {

    fun compute(
        initialBalance: Money,
        income: Money = Money.ZERO,
        incomePartial: Money = Money.ZERO,
        expense: Money = Money.ZERO,
        expensePartial: Money = Money.ZERO,
        adjustment: Money = Money.ZERO,
        transferIn: Money = Money.ZERO,
        transferOut: Money = Money.ZERO,
        reversalCredit: Money = Money.ZERO,
        reversalDebit: Money = Money.ZERO
    ): Money =
        initialBalance +
            income + incomePartial + adjustment + transferIn + reversalCredit -
            expense - expensePartial - transferOut - reversalDebit
}
