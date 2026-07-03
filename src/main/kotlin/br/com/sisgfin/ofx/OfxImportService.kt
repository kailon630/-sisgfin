package br.com.sisgfin.ofx

import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import java.io.File
import java.time.LocalDateTime

class OfxImportService(
    private val parser: OfxParser,
    private val transactionService: TransactionService,
    private val ofxImportRepository: OfxImportRepository,
    private val accountRepository: FinancialAccountRepository
) {

    /**
     * Importa um arquivo OFX para a conta indicada.
     *
     * Idempotente: reimportar o mesmo arquivo não cria duplicatas — lançamentos com FITID
     * já existente para a conta são contados em [OfxImportResult.duplicateCount] e ignorados.
     */
    fun import(file: File, accountId: Int, userId: Int?): OfxImportResult {
        // Passo 1: parse
        val statement = runCatching { parser.parse(file) }.getOrElse { e ->
            return OfxImportResult(
                newCount = 0, duplicateCount = 0, errorCount = 1,
                errors = listOf("Falha ao interpretar o arquivo OFX: ${e.message}")
            )
        }

        val warnings = mutableListOf<String>()

        // Passo 2: validação do ACCTID (aviso, não bloqueia)
        val accountNumber = accountRepository.findById(accountId)?.accountNumber
        if (accountNumber != null) {
            val digitsOfx     = statement.acctId.replace(Regex("[^0-9]"), "")
            val digitsAccount = accountNumber.replace(Regex("[^0-9]"), "")
            if (!digitsOfx.contains(digitsAccount) && !digitsAccount.contains(digitsOfx)) {
                warnings.add(
                    "ACCTID do arquivo (${statement.acctId}) não corresponde ao número de conta registrado ($accountNumber). " +
                    "Verifique se o arquivo pertence à conta correta."
                )
            }
        }

        var newCount       = 0
        var duplicateCount = 0
        var errorCount     = 0
        val errors         = mutableListOf<String>()
        val newTxIds       = mutableMapOf<String, Int>() // fitId → id do lançamento criado

        for (ofxTx in statement.transactions) {
            // Passo 3: deduplicação por FITID
            if (ofxImportRepository.existsByFitId(accountId, ofxTx.fitId)) {
                duplicateCount++
                continue
            }

            // Passo 4: mapeamento de tipo
            val type = mapType(ofxTx)

            // Passo 5: construção do lançamento
            val dateTime   = ofxTx.date.atStartOfDay()
            val absAmount  = ofxTx.amount.abs()
            val description = ofxTx.memo.ifBlank { "Importação OFX" }

            val transaction = Transaction(
                type        = type,
                status      = TransactionStatus.PAID,
                description = description,
                amount      = absAmount,
                issueDate   = dateTime,
                dueDate     = dateTime,
                paymentDate = dateTime,
                paidAmount  = absAmount,
                accountId   = accountId,
                ofxFitId    = ofxTx.fitId,
                createdBy   = userId
            )

            // Passo 6: persistência via service (audit + timeline inclusos)
            runCatching { transactionService.createFromOfx(transaction) }
                .onSuccess { newId ->
                    newCount++
                    newTxIds[ofxTx.fitId] = newId
                }
                .onFailure { e ->
                    errorCount++
                    errors.add("FITID ${ofxTx.fitId}: ${e.message}")
                }
        }

        // Passo 6b: detectar candidatos à conciliação para lançamentos recém-importados
        val candidates = mutableListOf<ConciliationCandidate>()
        for (ofxTx in statement.transactions) {
            val ofxTxId = newTxIds[ofxTx.fitId] ?: continue
            val absAmount = ofxTx.amount.abs()
            transactionService.findPendingCandidates(accountId, absAmount, ofxTx.date)
                .forEach { manual -> candidates.add(ConciliationCandidate(ofxTx, ofxTxId, manual)) }
        }

        // Passo 7: registro do log de importação
        runCatching {
            ofxImportRepository.insert(
                OfxImport(
                    accountId        = accountId,
                    filename         = file.name,
                    bankId           = statement.bankId,
                    acctId           = statement.acctId,
                    dtStart          = statement.dtStart,
                    dtEnd            = statement.dtEnd,
                    importedAt       = LocalDateTime.now(),
                    importedBy       = userId,
                    totalRecords     = statement.transactions.size,
                    newRecords       = newCount,
                    duplicateRecords = duplicateCount
                )
            )
        }

        // Passo 8: resultado
        return OfxImportResult(
            newCount       = newCount,
            duplicateCount = duplicateCount,
            errorCount     = errorCount,
            errors         = errors,
            warnings       = warnings,
            candidates     = candidates
        )
    }

    private fun mapType(ofxTx: OfxTransaction): TransactionType = when (ofxTx.type) {
        OfxTrnType.DEP                  -> TransactionType.INCOME
        OfxTrnType.DEBIT                -> TransactionType.EXPENSE
        OfxTrnType.XFER, OfxTrnType.OTHER ->
            if (ofxTx.isInflow) TransactionType.INCOME else TransactionType.EXPENSE
    }
}
