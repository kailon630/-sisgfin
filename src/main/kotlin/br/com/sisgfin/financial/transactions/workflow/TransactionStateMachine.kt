package br.com.sisgfin.financial.transactions.workflow

import br.com.sisgfin.financial.transactions.TransactionStatus

/**
 * Máquina de estados centralizada — única fonte de verdade para transições de status.
 */
object TransactionStateMachine {

    private val allowedTransitions: Map<TransactionStatus, Set<TransactionStatus>> = mapOf(
        TransactionStatus.DRAFT to setOf(
            TransactionStatus.PENDING,
            TransactionStatus.CANCELED,
            TransactionStatus.SCHEDULED
        ),
        TransactionStatus.SCHEDULED to setOf(
            TransactionStatus.PENDING,
            TransactionStatus.CANCELED
        ),
        TransactionStatus.PENDING to setOf(
            TransactionStatus.PAID,
            TransactionStatus.OVERDUE,
            TransactionStatus.CANCELED,
            TransactionStatus.PARTIAL
        ),
        TransactionStatus.OVERDUE to setOf(
            TransactionStatus.PAID,
            TransactionStatus.PARTIAL,
            TransactionStatus.CANCELED
        ),
        TransactionStatus.PARTIAL to setOf(
            TransactionStatus.PAID,
            TransactionStatus.PARTIAL,
            TransactionStatus.CANCELED
        ),
        TransactionStatus.PAID to emptySet(),
        TransactionStatus.CANCELED to emptySet()
    )

    private val forbiddenExplicit = setOf(
        TransactionStatus.CANCELED to TransactionStatus.PENDING,
        TransactionStatus.PAID to TransactionStatus.DRAFT,
        TransactionStatus.PAID to TransactionStatus.PENDING,
        TransactionStatus.PAID to TransactionStatus.PARTIAL,
        TransactionStatus.PAID to TransactionStatus.OVERDUE
    )

    fun canTransition(from: TransactionStatus, to: TransactionStatus): Boolean {
        if (from == to) return true
        if ((from to to) in forbiddenExplicit) return false
        return allowedTransitions[from]?.contains(to) == true
    }

    fun assertTransition(from: TransactionStatus, to: TransactionStatus) {
        if (!canTransition(from, to)) {
            throw IllegalStateException(
                "Transição de status inválida: ${from.name} → ${to.name}"
            )
        }
    }

    /** Status finais — sem edição operacional de classificação via painel. */
    fun isTerminal(status: TransactionStatus): Boolean =
        status == TransactionStatus.PAID || status == TransactionStatus.CANCELED

    fun allowsPayment(status: TransactionStatus): Boolean =
        status in setOf(
            TransactionStatus.PENDING,
            TransactionStatus.OVERDUE,
            TransactionStatus.PARTIAL
        )

    fun allowsCancel(status: TransactionStatus): Boolean =
        !isTerminal(status)

    fun allowsDuplicate(status: TransactionStatus): Boolean = true

    /**
     * Transição exclusiva para estorno de baixa individual (M5-A, D1).
     * PAID deixa de ser terminal por esta via — a baixa errada é anulada
     * e o status recalculado das baixas restantes pode ser qualquer não-terminal.
     * Não altera assertTransition, preservando a RN-13 para todos os caminhos normais.
     */
    fun assertReversalTransition(from: TransactionStatus, to: TransactionStatus) {
        if (from == to) return
        if (from == TransactionStatus.CANCELED) {
            throw IllegalStateException(
                "Estorno de baixa não altera lançamento CANCELED: ${from.name} → ${to.name}"
            )
        }
        if (to == TransactionStatus.CANCELED) {
            throw IllegalStateException(
                "Estorno de baixa não pode produzir status CANCELED."
            )
        }
    }

    fun resolveStatusAfterPayment(
        totalAmount: java.math.BigDecimal,
        paidAmount: java.math.BigDecimal
    ): TransactionStatus = when {
        paidAmount.compareTo(totalAmount) >= 0 -> TransactionStatus.PAID
        paidAmount.compareTo(java.math.BigDecimal.ZERO) > 0 -> TransactionStatus.PARTIAL
        else -> throw IllegalArgumentException("Valor pago deve ser maior que zero.")
    }
}
