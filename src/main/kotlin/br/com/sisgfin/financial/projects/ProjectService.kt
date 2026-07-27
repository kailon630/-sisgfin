package br.com.sisgfin.financial.projects

import br.com.sisgfin.AuditLog
import br.com.sisgfin.AuditRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.core.domain.AuditedCrudService

class ProjectService(
    private val projectRepository: ProjectRepository,
    private val auditRepository: AuditRepository,
    private val session: SessionManager
) : AuditedCrudService<Project>(
    repository      = projectRepository,
    auditRepository = auditRepository,
    sessionManager  = session,
    entityType      = "PROJECT",
    displayName     = { it.name },
    withCreatedBy   = { item, userId -> item.copy(createdBy = userId) },
    withActiveFlag  = { item, active -> item.copy(isActive = active) },
    isActive        = { it.isActive }
)
