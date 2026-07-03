package br.com.sisgfin.financial.ledger

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import java.time.LocalDateTime

/**
 * Gancho arquitetural para o ledger (Fase ledger futura).
 * Implementação no-op nesta fase.
 */
class LedgerService {

    fun recordPayment(
        transaction: Transaction,
        paidAmount: Money,
        paymentDate: LocalDateTime
    ) {
        // LedgerService.recordPayment() — reservado para posting contábil
    }

    fun recordStatusChange(transaction: Transaction) {
        // reservado
    }
}
