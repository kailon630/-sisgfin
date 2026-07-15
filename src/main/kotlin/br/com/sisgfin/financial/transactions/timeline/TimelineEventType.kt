package br.com.sisgfin.financial.transactions.timeline

enum class TimelineEventType(val displayLabel: String) {
    CREATED("Criada"),
    UPDATED("Atualizada"),
    STATUS_CHANGED("Status alterado"),
    PAYMENT("Quitada"),
    PARTIAL_PAYMENT("Pagamento parcial"),
    CANCELED("Cancelada"),
    DUPLICATED("Duplicada"),
    OVERDUE("Marcada como vencida"),
    WARNING("Aviso"),
    // RN-20/21: transferências
    TRANSFER_OUT("Transferência enviada"),
    TRANSFER_IN("Transferência recebida"),
    // RN-14/22/23: estorno
    REVERSED("Estornado"),
    REVERSAL_OF("Estorno de"),
    // Fase 6-D: importação OFX
    OFX_IMPORT("Importado via OFX"),
    // Fase 6-D.5: conciliação manual
    RECONCILED("Conciliado com extrato OFX"),
    // Fase 7-A: recorrência automática
    RECURRENCE_GENERATED("Gerado por recorrência"),
    // Fase 8-C: importação de folha de pagamento
    PAYROLL_IMPORT("Importado via folha de pagamento")
}
