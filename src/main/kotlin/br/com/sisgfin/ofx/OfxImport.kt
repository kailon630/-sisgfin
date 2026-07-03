package br.com.sisgfin.ofx

import java.time.LocalDate
import java.time.LocalDateTime

data class OfxImport(
    val id: Int = 0,
    val accountId: Int,
    val filename: String,
    val bankId: String,
    val acctId: String,
    val dtStart: LocalDate,
    val dtEnd: LocalDate,
    val importedAt: LocalDateTime,
    val importedBy: Int?,
    val totalRecords: Int,
    val newRecords: Int,
    val duplicateRecords: Int
)
