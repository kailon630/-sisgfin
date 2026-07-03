package br.com.sisgfin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(private val auditRepository: AuditRepository) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isAuthenticated: Boolean
        get() = _currentUser.value != null

    fun login(user: User) {
        _currentUser.value = user
        auditRepository.insert(AuditLog(
            entityType = "USER",
            entityId = user.id,
            action = "LOGIN",
            performedBy = user.id
        ))
    }

    fun logout() {
        val user = _currentUser.value
        if (user != null) {
            auditRepository.insert(AuditLog(
                entityType = "USER",
                entityId = user.id,
                action = "LOGOUT",
                performedBy = user.id
            ))
        }
        _currentUser.value = null
    }

    fun hasPermission(permission: Permission): Boolean {
        val user = _currentUser.value ?: return false
        if (user.role == UserRole.ADMIN) return true
        // RN-12: OPERADOR só tem acesso básico; ConfirmPayment, UserManagement etc. são ADMIN
        return when (permission) {
            is Permission.BasicOperation -> true
            else -> false
        }
    }

    val isAdmin: Boolean get() = _currentUser.value?.role == UserRole.ADMIN

    /**
     * Executa [block] com [user] como usuário corrente e restaura o estado anterior ao final.
     * Usado pela API REST para propagar identidade sem interferir na sessão desktop.
     * Seguro para SQLite single-writer (Exposed transactions são síncronas).
     */
    fun <T> withApiUser(user: User, block: () -> T): T {
        val previous = _currentUser.value
        _currentUser.value = user
        return try { block() } finally { _currentUser.value = previous }
    }
}
