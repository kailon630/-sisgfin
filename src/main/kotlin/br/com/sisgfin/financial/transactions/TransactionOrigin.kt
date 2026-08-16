package br.com.sisgfin.financial.transactions

enum class TransactionOrigin {
    MANUAL,
    PAYROLL_ENGINE,
    PAYROLL_IMPORT,
    RECURRENCE,
    OFX,
    API,
    INSTALLMENT,
    TRANSFER,
    REVERSAL,
    DUPLICATE
}
