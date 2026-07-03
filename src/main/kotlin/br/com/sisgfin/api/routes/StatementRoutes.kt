package br.com.sisgfin.api.routes

import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.api.ErrorResponse
import br.com.sisgfin.api.StatementEntryDto
import br.com.sisgfin.api.StatementResponse
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionType
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Route.statementRoutes(
    transactionRepository: TransactionRepository,
    accountRepository: FinancialAccountRepository
) {
    get("/accounts/{id}/statement") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
        val account = accountRepository.findById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Conta não encontrada"))

        val from = call.request.queryParameters["from"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now().withDayOfMonth(1)
        val to = call.request.queryParameters["to"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

        val opening  = transactionRepository.openingBalance(account.initialBalance, id, from)
        val entries  = transactionRepository.findStatementEntries(accountId = id, from = from, to = to)
        val dtFmt    = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        var running  = opening
        var inflow   = Money.ZERO
        var outflow  = Money.ZERO

        val dtos = entries.map { tx ->
            val isCredit = tx.type == TransactionType.INCOME ||
                tx.type == TransactionType.REVERSAL ||
                (tx.type == TransactionType.TRANSFER && tx.parentTransactionId != null) ||
                (tx.type == TransactionType.ADJUSTMENT)
            if (isCredit) { running += tx.amount; inflow += tx.amount }
            else          { running -= tx.amount; outflow += tx.amount }
            StatementEntryDto(
                id             = tx.id,
                type           = tx.type.name,
                description    = tx.description,
                paymentDate    = tx.paymentDate?.format(dtFmt) ?: "",
                amount         = tx.amount.toString(),
                runningBalance = running.toString(),
                supplierId     = tx.supplierId,
                categoryId     = tx.categoryId,
                costCenterId   = tx.costCenterId,
                ofxFitId       = tx.ofxFitId
            )
        }

        call.respond(StatementResponse(
            accountId      = id,
            openingBalance = opening.toString(),
            entries        = dtos,
            totalInflow    = inflow.toString(),
            totalOutflow   = outflow.toString(),
            closingBalance = running.toString()
        ))
    }
}
