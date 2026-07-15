package br.com.sisgfin.ofx

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import java.time.LocalDate

enum class OfxTrnType { DEP, DEBIT, XFER, OTHER }

/**
 * Uma transação individual extraída do arquivo OFX.
 * [amount] preserva o sinal original: positivo = entrada, negativo = saída.
 * Para XFER, use [isInflow] para determinar direção (XFER pode ir para qualquer lado).
 */
data class OfxTransaction(
    val fitId: String,
    val type: OfxTrnType,
    val date: LocalDate,
    val amount: Money,
    val checkNum: String?,
    val memo: String
) {
    val isInflow: Boolean get() = !amount.isNegative()
}

data class OfxStatement(
    val bankId: String,
    val acctId: String,
    val currency: String,
    val dtStart: LocalDate,
    val dtEnd: LocalDate,
    val transactions: List<OfxTransaction>
)

/**
 * Par candidato à conciliação: lançamento OFX importado vs lançamento manual pendente.
 * [ofxTxId] é o id do lançamento PAID criado pela importação OFX.
 * [manualTx] é o lançamento PENDING/OVERDUE criado manualmente pelo operador.
 */
data class ConciliationCandidate(
    val ofxTx: OfxTransaction,
    val ofxTxId: Int,
    val manualTx: Transaction
)

/** Lançamento OFX importado sem nenhum par manual candidato — já é PAID mas sem contexto. */
data class UnmatchedOfxEntry(
    val ofxTx: OfxTransaction,
    val txId: Int
)

data class OfxImportResult(
    val newCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val candidates: List<ConciliationCandidate> = emptyList(),
    /** Lançamentos OFX sem par manual — disponíveis para detalhar descrição/fornecedor. */
    val unmatchedOfx: List<UnmatchedOfxEntry> = emptyList(),
    /** Lançamentos manuais PENDING/OVERDUE no período do extrato que não foram conciliados. */
    val unmatchedManual: List<Transaction> = emptyList()
) {
    val totalProcessed: Int    get() = newCount + duplicateCount + errorCount
    val hasErrors: Boolean     get() = errorCount > 0
    val hasWarnings: Boolean   get() = warnings.isNotEmpty()
    val hasCandidates: Boolean get() = candidates.isNotEmpty()
}
