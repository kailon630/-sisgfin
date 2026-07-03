package br.com.sisgfin.core.domain

import br.com.sisgfin.AuditLog
import br.com.sisgfin.AuditRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.core.crud.CrudOperations
import br.com.sisgfin.core.domain.Identifiable

abstract class AuditedCrudService<T : Identifiable>(
    private val repository: MutableEntityRepository<T>,
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager,
    private val entityType: String,
    private val displayName: (T) -> String,
    private val withCreatedBy: (T, Int?) -> T,
    private val withActiveFlag: (T, Boolean) -> T,
    private val isActive: (T) -> Boolean
) : CrudOperations<T> {

    override fun listAll(): List<T> = repository.findAll()

    fun findById(id: Int): T? = repository.findById(id)

    override fun save(item: T) {
        val admin = sessionManager.currentUser.value
        if (item.id == 0) {
            val id = repository.insert(withCreatedBy(item, admin?.id))
            auditRepository.insert(
                AuditLog(
                    entityType = entityType,
                    entityId = id,
                    action = "CREATE",
                    newValue = displayName(item),
                    performedBy = admin?.id
                )
            )
        } else {
            repository.update(item)
            auditRepository.insert(
                AuditLog(
                    entityType = entityType,
                    entityId = item.id,
                    action = "UPDATE",
                    newValue = displayName(item),
                    performedBy = admin?.id
                )
            )
        }
    }

    override fun toggleActive(id: Int) {
        val entity = repository.findById(id) ?: return
        val admin = sessionManager.currentUser.value
        val active = isActive(entity)
        repository.update(withActiveFlag(entity, !active))
        auditRepository.insert(
            AuditLog(
                entityType = entityType,
                entityId = id,
                action = if (active) "DEACTIVATE" else "ACTIVATE",
                performedBy = admin?.id
            )
        )
    }
}
