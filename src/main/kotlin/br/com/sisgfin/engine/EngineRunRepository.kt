package br.com.sisgfin.engine

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class EngineRunRepository {

    fun startRun(engine: String, reference: String?): Int = transaction {
        EngineRunsTable.insert {
            it[EngineRunsTable.engine]    = engine
            it[EngineRunsTable.reference] = reference
            it[EngineRunsTable.startedAt] = LocalDateTime.now()
            it[EngineRunsTable.status]    = EngineRunStatus.RUNNING.name
        } get EngineRunsTable.id
    }

    fun finishSuccess(id: Int, created: Int, skipped: Int) = transaction {
        EngineRunsTable.update({ EngineRunsTable.id eq id }) {
            it[EngineRunsTable.finishedAt] = LocalDateTime.now()
            it[EngineRunsTable.status]     = EngineRunStatus.SUCCESS.name
            it[EngineRunsTable.created]    = created
            it[EngineRunsTable.skipped]    = skipped
        }
    }

    fun finishPartial(id: Int, created: Int, skipped: Int, failed: Int, error: String) = transaction {
        EngineRunsTable.update({ EngineRunsTable.id eq id }) {
            it[EngineRunsTable.finishedAt] = LocalDateTime.now()
            it[EngineRunsTable.status]     = EngineRunStatus.PARTIAL_FAILURE.name
            it[EngineRunsTable.created]    = created
            it[EngineRunsTable.skipped]    = skipped
            it[EngineRunsTable.failed]     = failed
            it[EngineRunsTable.error]      = error
        }
    }

    fun finishFailed(id: Int, error: String) = transaction {
        EngineRunsTable.update({ EngineRunsTable.id eq id }) {
            it[EngineRunsTable.finishedAt] = LocalDateTime.now()
            it[EngineRunsTable.status]     = EngineRunStatus.FAILED.name
            it[EngineRunsTable.error]      = error
        }
    }

    fun finishSkipped(id: Int) = transaction {
        EngineRunsTable.update({ EngineRunsTable.id eq id }) {
            it[EngineRunsTable.finishedAt] = LocalDateTime.now()
            it[EngineRunsTable.status]     = EngineRunStatus.SKIPPED_LOCKED.name
        }
    }

    fun findById(id: Int): EngineRun? = transaction {
        EngineRunsTable.selectAll()
            .where { EngineRunsTable.id eq id }
            .firstOrNull()
            ?.toEngineRun()
    }

    fun findLastByEngine(engine: String): EngineRun? = transaction {
        EngineRunsTable.selectAll()
            .where {
                (EngineRunsTable.engine eq engine) and
                (EngineRunsTable.status neq EngineRunStatus.RUNNING.name)
            }
            .orderBy(EngineRunsTable.startedAt to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toEngineRun()
    }

    fun findLastSuccessByEngineAndReference(engine: String, reference: String): EngineRun? = transaction {
        EngineRunsTable.selectAll()
            .where {
                (EngineRunsTable.engine eq engine) and
                (EngineRunsTable.reference eq reference) and
                (EngineRunsTable.status eq EngineRunStatus.SUCCESS.name)
            }
            .orderBy(EngineRunsTable.startedAt to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toEngineRun()
    }

    private fun ResultRow.toEngineRun() = EngineRun(
        id         = this[EngineRunsTable.id],
        engine     = this[EngineRunsTable.engine],
        reference  = this[EngineRunsTable.reference],
        startedAt  = this[EngineRunsTable.startedAt],
        finishedAt = this[EngineRunsTable.finishedAt],
        status     = EngineRunStatus.valueOf(this[EngineRunsTable.status]),
        created    = this[EngineRunsTable.created],
        skipped    = this[EngineRunsTable.skipped],
        failed     = this[EngineRunsTable.failed],
        error      = this[EngineRunsTable.error]
    )
}
