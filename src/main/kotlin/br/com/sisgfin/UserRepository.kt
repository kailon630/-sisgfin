package br.com.sisgfin

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class UserRepository {
    fun findByUsername(username: String): User? = transaction {
        Users.selectAll().where { Users.username eq username }.map { rowToUser(it) }.singleOrNull()
    }

    fun findById(id: Int): User? = transaction {
        Users.selectAll().where { Users.id eq id }.map { rowToUser(it) }.singleOrNull()
    }

    fun findAll(): List<User> = transaction {
        Users.selectAll().map { rowToUser(it) }
    }

    fun insert(user: User): Int = transaction {
        Users.insert {
            it[name] = user.name
            it[username] = user.username
            it[email] = user.email
            it[passwordHash] = user.passwordHash
            it[role] = user.role.name
            it[isActive] = user.isActive
            it[createdBy] = user.createdBy
        } get Users.id
    }

    fun update(user: User) = transaction {
        Users.update({ Users.id eq user.id }) {
            it[name] = user.name
            it[email] = user.email
            it[role] = user.role.name
            it[isActive] = user.isActive
            it[updatedAt] = LocalDateTime.now()
        }
    }

    fun updateLastLogin(id: Int) = transaction {
        Users.update({ Users.id eq id }) {
            it[lastLoginAt] = LocalDateTime.now()
        }
    }

    fun updatePassword(id: Int, newHash: String) = transaction {
        Users.update({ Users.id eq id }) {
            it[passwordHash] = newHash
            it[updatedAt] = LocalDateTime.now()
        }
    }

    fun countAdmins(): Long = transaction {
        Users.selectAll().where { (Users.role eq UserRole.ADMIN.name) and (Users.isActive eq true) }.count()
    }

    private fun rowToUser(row: ResultRow) = User(
        id = row[Users.id],
        name = row[Users.name],
        username = row[Users.username],
        email = row[Users.email],
        passwordHash = row[Users.passwordHash],
        role = UserRole.valueOf(row[Users.role]),
        isActive = row[Users.isActive],
        lastLoginAt = row[Users.lastLoginAt],
        createdAt = row[Users.createdAt],
        updatedAt = row[Users.updatedAt],
        createdBy = row[Users.createdBy]
    )
}

class AuditRepository {
    fun insert(log: AuditLog) = transaction {
        AuditLogs.insert {
            it[entityType] = log.entityType
            it[entityId] = log.entityId
            it[action] = log.action
            it[oldValue] = log.oldValue
            it[newValue] = log.newValue
            it[performedBy] = log.performedBy
        }
    }

    fun findByEntity(entityType: String, entityId: Int): List<AuditLog> = transaction {
        AuditLogs.selectAll()
            .where { (AuditLogs.entityType eq entityType) and (AuditLogs.entityId eq entityId) }
            .orderBy(AuditLogs.createdAt to SortOrder.DESC)
            .map { rowToAuditLog(it) }
    }

    private fun rowToAuditLog(row: ResultRow) = AuditLog(
        id = row[AuditLogs.id],
        entityType = row[AuditLogs.entityType],
        entityId = row[AuditLogs.entityId],
        action = row[AuditLogs.action],
        oldValue = row[AuditLogs.oldValue],
        newValue = row[AuditLogs.newValue],
        performedBy = row[AuditLogs.performedBy],
        createdAt = row[AuditLogs.createdAt]
    )
}
