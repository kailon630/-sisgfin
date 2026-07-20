package br.com.sisgfin.api

import kotlinx.serialization.Serializable

// ── Comum ─────────────────────────────────────────────────────────────────────

@Serializable data class ErrorResponse(val error: String)
@Serializable data class IdResponse(val id: Int)
@Serializable data class MessageResponse(val message: String)

// ── Auth ──────────────────────────────────────────────────────────────────────

@Serializable data class LoginRequest(val username: String, val password: String)

@Serializable data class LoginResponse(
    val token: String,
    val userId: Int,
    val username: String,
    val role: String
)

@Serializable data class UserDto(
    val id: Int,
    val name: String,
    val username: String,
    val email: String,
    val role: String,
    val isActive: Boolean
)

// ── Transactions ──────────────────────────────────────────────────────────────

@Serializable data class TransactionDto(
    val id: Int,
    val type: String,
    val status: String,
    val description: String,
    val amount: String,
    val issueDate: String,
    val dueDate: String,
    val paymentDate: String?,
    val paidAmount: String?,
    val accountId: Int,
    val supplierId: Int?,
    val costCenterId: Int?,
    val categoryId: Int?,
    val notes: String?,
    val documentType: String?,
    val documentNumber: String?,
    val installmentCurrent: Int?,
    val installmentTotal: Int?,
    val employeeId: Int?,
    val ofxFitId: String?,
    val reconciledWithFitId: String?
)

@Serializable data class CreateTransactionRequest(
    val type: String,
    val description: String,
    val amount: String,
    val issueDate: String,
    val dueDate: String,
    val accountId: Int,
    val supplierId: Int? = null,
    val costCenterId: Int? = null,
    val categoryId: Int? = null,
    val notes: String? = null,
    val documentType: String? = null,
    val documentNumber: String? = null,
    val installmentTotal: Int? = null
)

@Serializable data class UpdateTransactionRequest(
    val description: String,
    val amount: String,
    val issueDate: String,
    val dueDate: String,
    val accountId: Int,
    val supplierId: Int? = null,
    val costCenterId: Int? = null,
    val categoryId: Int? = null,
    val notes: String? = null,
    val documentType: String? = null,
    val documentNumber: String? = null
)

@Serializable data class PaymentRequest(
    val paymentDate: String,
    val paidAmount: String,
    val interestAmount: String? = null,
    val fineAmount: String? = null
)

@Serializable data class ReversalRequest(val justification: String)

@Serializable data class TimelineEventDto(
    val id: Int,
    val eventType: String,
    val message: String,
    val amount: String?,
    val statusFrom: String?,
    val statusTo: String?,
    val performedBy: Int?,
    val createdAt: String
)

// ── Financial Accounts ────────────────────────────────────────────────────────

@Serializable data class AccountDto(
    val id: Int,
    val name: String,
    val bankName: String?,
    val agency: String?,
    val accountNumber: String?,
    val accountType: String,
    val initialBalance: String,
    val investmentBroker: String?,
    val isActive: Boolean
)

@Serializable data class CreateAccountRequest(
    val name: String,
    val bankName: String? = null,
    val agency: String? = null,
    val accountNumber: String? = null,
    val accountType: String = "BANK",
    val initialBalance: String = "0.00",
    val investmentBroker: String? = null
)

@Serializable data class BalanceResponse(
    val accountId: Int,
    val accountName: String,
    val balance: String
)

// ── Suppliers ─────────────────────────────────────────────────────────────────

@Serializable data class SupplierDto(
    val id: Int,
    val document: String,
    val name: String,
    val tradeName: String?,
    val email: String?,
    val phone: String?,
    val pixKey: String?,
    val bank: String?,
    val agency: String?,
    val account: String?,
    val notes: String?,
    val isActive: Boolean
)

@Serializable data class CreateSupplierRequest(
    val document: String,
    val name: String,
    val tradeName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val pixKey: String? = null,
    val bank: String? = null,
    val agency: String? = null,
    val account: String? = null,
    val notes: String? = null
)

// ── Categories ────────────────────────────────────────────────────────────────

@Serializable data class CategoryDto(
    val id: Int,
    val code: String,
    val name: String,
    val displayName: String,
    val groupCode: String?,
    val groupName: String?,
    val isIncome: Boolean,
    val isActive: Boolean
)

// ── Cost Centers ──────────────────────────────────────────────────────────────

@Serializable data class CostCenterDto(
    val id: Int,
    val code: String,
    val name: String,
    val description: String?,
    val startDate: String?,
    val endDate: String?,
    val isActive: Boolean,
    val isEncerrado: Boolean
)

// ── Cash Flow ─────────────────────────────────────────────────────────────────

@Serializable data class DailyCashFlowDto(
    val date: String,
    val totalOutflow: String,
    val totalInflow: String,
    val projectedBalance: String,
    val transactionCount: Int
)

@Serializable data class CashFlowProjectionDto(
    val currentBalance: String,
    val overdueTotal: String,
    val totalCommitted: String,
    val projectedFinalBalance: String,
    val windowDays: Int,
    val entries: List<DailyCashFlowDto>
)

// ── Statement ─────────────────────────────────────────────────────────────────

@Serializable data class StatementEntryDto(
    val id: Int,
    val type: String,
    val description: String,
    val paymentDate: String,
    val amount: String,
    val runningBalance: String,
    val supplierId: Int?,
    val categoryId: Int?,
    val costCenterId: Int?,
    val ofxFitId: String?
)

@Serializable data class StatementResponse(
    val accountId: Int,
    val openingBalance: String,
    val entries: List<StatementEntryDto>,
    val totalInflow: String,
    val totalOutflow: String,
    val closingBalance: String
)
