package br.com.sisgfin.engine

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

enum class EngineRunStatus { RUNNING, SUCCESS, PARTIAL_FAILURE, FAILED, SKIPPED_LOCKED }

data class EngineRun(
    val id: Int,
    val engine: String,
    val reference: String?,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
    val status: EngineRunStatus,
    val created: Int,
    val skipped: Int,
    val failed: Int,
    val error: String?
)

object EngineRunsTable : Table("engine_runs") {
    val id          = integer("id").autoIncrement()
    val engine      = varchar("engine", 30)
    val reference   = varchar("reference", 20).nullable()
    val startedAt   = datetime("started_at")
    val finishedAt  = datetime("finished_at").nullable()
    val status      = varchar("status", 20)
    val created     = integer("created").default(0)
    val skipped     = integer("skipped").default(0)
    val failed      = integer("failed").default(0)
    val error       = text("error").nullable()
    override val primaryKey = PrimaryKey(id)
}
