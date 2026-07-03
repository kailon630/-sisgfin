package br.com.sisgfin

import br.com.sisgfin.core.domain.Identifiable
import java.time.LocalDateTime

enum class UserRole {
    ADMIN,
    OPERADOR
}

data class User(
    override val id: Int = 0,
    val name: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole = UserRole.OPERADOR,
    val isActive: Boolean = true,
    val lastLoginAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: Int? = null
) : Identifiable

data class AuditLog(
    val id: Int = 0,
    val entityType: String,
    val entityId: Int,
    val action: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val performedBy: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

sealed class Permission {
    object All : Permission()
    object BasicOperation : Permission()
    // RN-12: confirmar/estornar pagamentos requer ADMIN
    object ConfirmPayment : Permission()
    object UserManagement : Permission()
    object ViewReports : Permission()
    object Configuration : Permission()
}
