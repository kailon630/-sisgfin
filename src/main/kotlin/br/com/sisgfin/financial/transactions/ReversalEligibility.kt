package br.com.sisgfin.financial.transactions

/**
 * Pré-condições de estorno (RN-14/22/23 + C1.4).
 * Extraídas para permitir teste unitário sem infraestrutura de banco.
 */
object ReversalEligibility {

    fun assertCanReverse(original: Transaction) {
        if (original.status != TransactionStatus.PAID) {
            throw IllegalStateException(
                "Apenas lançamentos com status Pago podem ser estornados. " +
                "Status atual: ${original.status.displayName}."
            )
        }
        if (original.type == TransactionType.REVERSAL) {
            throw IllegalArgumentException("Não é possível estornar um lançamento de estorno.")
        }
        if (original.type == TransactionType.TRANSFER) {
            throw IllegalArgumentException(
                "Transferências não podem ser estornadas individualmente. " +
                "Cancele a transferência para reverter o par."
            )
        }
    }
}
