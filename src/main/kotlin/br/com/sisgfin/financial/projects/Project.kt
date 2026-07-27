package br.com.sisgfin.financial.projects

import br.com.sisgfin.core.domain.Activatable
import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.financial.money.Money
import java.time.LocalDate
import java.time.LocalDateTime

enum class ProjectStatus(val label: String) {
    PLANEJAMENTO("Planejamento"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDO("Concluído"),
    CANCELADO("Cancelado")
}

data class Project(
    override val id: Int = 0,
    val code: String,
    val name: String,
    val description: String? = null,
    val status: ProjectStatus = ProjectStatus.EM_ANDAMENTO,
    val budget: Money? = null,
    val startDate: LocalDate? = null,
    val expectedEnd: LocalDate? = null,
    val actualEnd: LocalDate? = null,
    override val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: Int? = null
) : Identifiable, Activatable
