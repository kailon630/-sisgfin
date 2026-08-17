package br.com.sisgfin.financial.payments

import br.com.sisgfin.financial.transactions.Transaction

/**
 * M4b — resultado de findPaymentEntries: uma baixa com o título associado.
 *
 * paymentOrdinal: posição desta baixa entre as baixas ativas do título (1-based).
 * totalPayments : total de baixas ativas quando o título está PAID; null quando PARTIAL
 *                 (mais baixas podem vir, denominador desconhecido).
 */
data class PaymentEntry(
    val payment: TransactionPayment,
    val transaction: Transaction,
    val paymentOrdinal: Int,
    val totalPayments: Int?
)
