package br.com.sisgfin.financial.projects

import br.com.sisgfin.core.domain.MutableEntityRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ProjectRepository : MutableEntityRepository<Project> {

    override fun findAll(): List<Project> = transaction {
        ProjectsTable.selectAll()
            .orderBy(ProjectsTable.name to SortOrder.ASC)
            .map { rowToProject(it) }
    }

    fun findAllActive(): List<Project> = transaction {
        ProjectsTable.selectAll()
            .where { ProjectsTable.isActive eq true }
            .orderBy(ProjectsTable.name to SortOrder.ASC)
            .map { rowToProject(it) }
    }

    override fun findById(id: Int): Project? = transaction {
        ProjectsTable.selectAll()
            .where { ProjectsTable.id eq id }
            .map { rowToProject(it) }
            .singleOrNull()
    }

    override fun insert(project: Project): Int = transaction {
        ProjectsTable.insert {
            it[code]        = project.code
            it[name]        = project.name
            it[description] = project.description
            it[status]      = project.status.name
            it[budget]      = project.budget?.value
            it[startDate]   = project.startDate
            it[expectedEnd] = project.expectedEnd
            it[actualEnd]   = project.actualEnd
            it[isActive]    = project.isActive
            it[createdAt]   = project.createdAt
            it[updatedAt]   = project.updatedAt
            it[createdBy]   = project.createdBy
        } get ProjectsTable.id
    }

    override fun update(project: Project) {
        transaction {
            ProjectsTable.update({ ProjectsTable.id eq project.id }) {
                it[code]        = project.code
                it[name]        = project.name
                it[description] = project.description
                it[status]      = project.status.name
                it[budget]      = project.budget?.value
                it[startDate]   = project.startDate
                it[expectedEnd] = project.expectedEnd
                it[actualEnd]   = project.actualEnd
                it[isActive]    = project.isActive
                it[updatedAt]   = LocalDateTime.now()
            }
        }
    }

    fun setActive(id: Int, active: Boolean) {
        transaction {
            ProjectsTable.update({ ProjectsTable.id eq id }) {
                it[isActive]  = active
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    fun hardDelete(id: Int) = transaction {
        ProjectsTable.deleteWhere { ProjectsTable.id eq id }
    }

    private fun rowToProject(row: ResultRow) = Project(
        id          = row[ProjectsTable.id],
        code        = row[ProjectsTable.code],
        name        = row[ProjectsTable.name],
        description = row[ProjectsTable.description],
        status      = ProjectStatus.valueOf(row[ProjectsTable.status]),
        budget      = row[ProjectsTable.budget]?.toMoney(),
        startDate   = row[ProjectsTable.startDate],
        expectedEnd = row[ProjectsTable.expectedEnd],
        actualEnd   = row[ProjectsTable.actualEnd],
        isActive    = row[ProjectsTable.isActive],
        createdAt   = row[ProjectsTable.createdAt],
        updatedAt   = row[ProjectsTable.updatedAt],
        createdBy   = row[ProjectsTable.createdBy]
    )
}
