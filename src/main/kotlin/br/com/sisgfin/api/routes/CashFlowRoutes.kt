package br.com.sisgfin.api.routes

import br.com.sisgfin.api.CashFlowProjectionDto
import br.com.sisgfin.api.DailyCashFlowDto
import br.com.sisgfin.cashflow.CashFlowService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.cashFlowRoutes(service: CashFlowService) {
    get("/cashflow") {
        val accountId = call.request.queryParameters["accountId"]?.toIntOrNull()
        val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 14
        val result = service.project(accountId, days.coerceIn(1, 90))
        call.respond(CashFlowProjectionDto(
            currentBalance        = result.currentBalance.toString(),
            overdueTotal          = result.overdueTotal.toString(),
            totalCommitted        = result.totalCommitted.toString(),
            projectedFinalBalance = result.projectedFinalBalance.toString(),
            windowDays            = days,
            entries               = result.entries.map { e ->
                DailyCashFlowDto(
                    date             = e.date.toString(),
                    totalOutflow     = e.totalOutflow.toString(),
                    totalInflow      = e.totalInflow.toString(),
                    projectedBalance = e.projectedBalance.toString(),
                    transactionCount = e.transactions.size
                )
            }
        ))
    }
}
