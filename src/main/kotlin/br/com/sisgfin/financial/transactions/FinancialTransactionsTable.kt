package br.com.sisgfin.financial.transactions

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object FinancialTransactionsTable : Table("financial_transactions") {
    val id = integer("id").autoIncrement()
    val type = varchar("type", 20)
    val status = varchar("status", 20)
    val description = varchar("description", 255)
    val amount = decimal("amount", 19, 2)
    val issueDate = datetime("issue_date")
    val dueDate = datetime("due_date")
    val paymentDate = datetime("payment_date").nullable()
    val paidAmount = decimal("paid_amount", 19, 2).nullable()
    val accountId = integer("account_id")
    val supplierId = integer("supplier_id").nullable()
    val costCenterId = integer("project_id").nullable()
    val notes = text("notes").nullable()
    val documentType = varchar("document_type", 20).nullable()
    val documentNumber = varchar("document_number", 50).nullable()
    val installmentCurrent = integer("installment_current").nullable()
    val installmentTotal = integer("installment_total").nullable()
    val categoryId = integer("category_id").nullable()
    val createdBy = integer("created_by").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    val isActive = bool("is_active").default(true)
    val parentTransactionId = integer("parent_transaction_id").nullable()
    val ledgerEntryId = integer("ledger_entry_id").nullable()
    val employeeId = integer("employee_id").nullable()
    val ofxFitId             = varchar("ofx_fitid", 64).nullable()
    val reconciledWithFitId  = varchar("reconciled_with_fitid", 64).nullable()
    val recurrenceTemplateId = integer("recurrence_template_id").nullable()
    val contractId           = integer("contract_id").nullable()
    val interestAmount       = decimal("interest_amount", 19, 2).nullable()
    val fineAmount           = decimal("fine_amount", 19, 2).nullable()

    override val primaryKey = PrimaryKey(id)
}
