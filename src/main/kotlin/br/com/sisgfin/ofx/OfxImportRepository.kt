package br.com.sisgfin.ofx

import br.com.sisgfin.financial.transactions.FinancialTransactionsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class OfxImportRepository {

    fun insert(ofxImport: OfxImport): Int = transaction {
        OfxImportsTable.insert {
            it[accountId]        = ofxImport.accountId
            it[filename]         = ofxImport.filename
            it[bankId]           = ofxImport.bankId
            it[acctId]           = ofxImport.acctId
            it[dtStart]          = ofxImport.dtStart
            it[dtEnd]            = ofxImport.dtEnd
            it[importedAt]       = ofxImport.importedAt
            it[importedBy]       = ofxImport.importedBy
            it[totalRecords]     = ofxImport.totalRecords
            it[newRecords]       = ofxImport.newRecords
            it[duplicateRecords] = ofxImport.duplicateRecords
        } get OfxImportsTable.id
    }

    /**
     * Verifica se um FITID já foi importado para uma conta específica.
     * Delega para [FinancialTransactionsTable] onde o índice único (account_id, ofx_fitid) vive.
     */
    fun existsByFitId(accountId: Int, fitId: String): Boolean = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.ofxFitId  eq fitId)
            }
            .limit(1)
            .count() > 0
    }

    fun countDuplicates(accountId: Int, fitIds: List<String>): Int = transaction {
        if (fitIds.isEmpty()) return@transaction 0
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.ofxFitId  inList fitIds)
            }
            .count()
            .toInt()
    }

    fun findByAccount(accountId: Int): List<OfxImport> = transaction {
        OfxImportsTable
            .selectAll()
            .where { OfxImportsTable.accountId eq accountId }
            .orderBy(OfxImportsTable.importedAt to SortOrder.DESC)
            .map { rowToImport(it) }
    }

    fun findAll(): List<OfxImport> = transaction {
        OfxImportsTable
            .selectAll()
            .orderBy(OfxImportsTable.importedAt to SortOrder.DESC)
            .map { rowToImport(it) }
    }

    private fun rowToImport(row: ResultRow) = OfxImport(
        id               = row[OfxImportsTable.id],
        accountId        = row[OfxImportsTable.accountId],
        filename         = row[OfxImportsTable.filename],
        bankId           = row[OfxImportsTable.bankId],
        acctId           = row[OfxImportsTable.acctId],
        dtStart          = row[OfxImportsTable.dtStart],
        dtEnd            = row[OfxImportsTable.dtEnd],
        importedAt       = row[OfxImportsTable.importedAt],
        importedBy       = row[OfxImportsTable.importedBy],
        totalRecords     = row[OfxImportsTable.totalRecords],
        newRecords       = row[OfxImportsTable.newRecords],
        duplicateRecords = row[OfxImportsTable.duplicateRecords]
    )
}
