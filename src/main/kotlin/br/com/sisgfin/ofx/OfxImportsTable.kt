package br.com.sisgfin.ofx

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object OfxImportsTable : Table("ofx_imports") {
    val id               = integer("id").autoIncrement()
    val accountId        = integer("account_id")
    val filename         = varchar("filename", 255)
    val bankId           = varchar("bank_id", 50)
    val acctId           = varchar("acct_id", 50)
    val dtStart          = date("dt_start")
    val dtEnd            = date("dt_end")
    val importedAt       = datetime("imported_at")
    val importedBy       = integer("imported_by").nullable()
    val totalRecords     = integer("total_records")
    val newRecords       = integer("new_records")
    val duplicateRecords = integer("duplicate_records")

    override val primaryKey = PrimaryKey(id)
}
