package br.com.sisgfin.financial.transactions

enum class TransactionStatus {
    DRAFT,
    PENDING,
    PAID,
    OVERDUE,
    PARTIAL,
    CANCELED,
    SCHEDULED;

    val displayName: String
        get() = when (this) {
            DRAFT -> "Rascunho"
            PENDING -> "Pendente"
            PAID -> "Pago"
            OVERDUE -> "Vencido"
            PARTIAL -> "Parcial"
            CANCELED -> "Cancelado"
            SCHEDULED -> "Agendado"
        }
}
