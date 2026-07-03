package br.com.sisgfin

import org.mindrot.jbcrypt.BCrypt

class AuthService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository
) {
    fun authenticate(username: String, passwordRaw: String): User? {
        val user = userRepository.findByUsername(username) ?: return null
        
        if (!user.isActive) return null

        if (BCrypt.checkpw(passwordRaw, user.passwordHash)) {
            userRepository.updateLastLogin(user.id)
            return user
        }
        
        return null
    }
}

class UserManagementService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager
) {
    fun listAll(): List<User> = userRepository.findAll()

    fun createUser(user: User): Int {
        val admin = sessionManager.currentUser.value
        val id = userRepository.insert(user.copy(createdBy = admin?.id))
        auditRepository.insert(AuditLog(
            entityType = "USER",
            entityId = id,
            action = "CREATE_USER",
            newValue = user.username,
            performedBy = admin?.id
        ))
        return id
    }

    fun updateUser(user: User) {
        val admin = sessionManager.currentUser.value
        val oldUser = userRepository.findById(user.id) ?: return
        
        // Bloqueio do último admin
        if (oldUser.role == UserRole.ADMIN && user.role != UserRole.ADMIN && userRepository.countAdmins() <= 1) {
            throw IllegalStateException("Não é possível alterar o papel do último administrador ativo.")
        }
        
        if (oldUser.isActive && !user.isActive && userRepository.countAdmins() <= 1 && oldUser.role == UserRole.ADMIN) {
             throw IllegalStateException("Não é possível desativar o último administrador ativo.")
        }

        userRepository.update(user)
        auditRepository.insert(AuditLog(
            entityType = "USER",
            entityId = user.id,
            action = "UPDATE_USER",
            oldValue = "Role: ${oldUser.role}, Active: ${oldUser.isActive}",
            newValue = "Role: ${user.role}, Active: ${user.isActive}",
            performedBy = admin?.id
        ))
    }

    fun resetPassword(userId: Int, newPasswordRaw: String) {
        val admin = sessionManager.currentUser.value
        val hash = BCrypt.hashpw(newPasswordRaw, BCrypt.gensalt())
        userRepository.updatePassword(userId, hash)
        auditRepository.insert(AuditLog(
            entityType = "USER",
            entityId = userId,
            action = "RESET_PASSWORD",
            performedBy = admin?.id
        ))
    }
}
