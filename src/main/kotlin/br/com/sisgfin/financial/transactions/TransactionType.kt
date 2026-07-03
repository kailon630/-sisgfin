package br.com.sisgfin.financial.transactions

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    ADJUSTMENT,
    REVERSAL;

    val displayName: String
        get() = when (this) {
            INCOME -> "Receita"
            EXPENSE -> "Despesa"
            TRANSFER -> "Transferência"
            ADJUSTMENT -> "Ajuste"
            REVERSAL -> "Estorno"
        }
}
