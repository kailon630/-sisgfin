package br.com.sisgfin.api.routes

import br.com.sisgfin.SessionManager
import br.com.sisgfin.UserRepository
import br.com.sisgfin.api.*
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.*
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dtFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

fun Route.transactionRoutes(
    service: TransactionService,
    sessionManager: SessionManager,
    userRepository: UserRepository
) {
    route("/transactions") {

        get {
            val filter = call.request.queryParameters["filter"]
            val status = call.request.queryParameters["status"]
            val type   = call.request.queryParameters["type"]
            when {
                status != null -> runCatching { TransactionStatus.valueOf(status) }.getOrNull()
                    ?.let { service.applyListFilter(TransactionListFilter.ByStatus(it)) }
                type != null -> runCatching { TransactionType.valueOf(type) }.getOrNull()
                    ?.let { service.applyListFilter(TransactionListFilter.ByType(it)) }
                filter == "action_required" -> service.applyListFilter(TransactionListFilter.ActionRequired)
                filter == "overdue" -> service.applyListFilter(TransactionListFilter.Overdue)
                filter == "paid" -> service.applyListFilter(TransactionListFilter.Paid)
                else -> service.applyListFilter(TransactionListFilter.All)
            }
            call.respond(service.listAll().map { it.toDto() })
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val tx = service.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Transação não encontrada"))
            call.respond(tx.toDto())
        }

        post {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val body = runCatching { call.receive<CreateTransactionRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido: ${it.message}"))
            }
            val tx = runCatching {
                val type = TransactionType.valueOf(body.type)
                Transaction(
                    type = type,
                    status = TransactionStatus.PENDING,
                    description = body.description,
                    amount = Money(BigDecimal(body.amount)),
                    issueDate = LocalDateTime.parse(body.issueDate, dtFmt),
                    dueDate = LocalDateTime.parse(body.dueDate, dtFmt),
                    accountId = body.accountId,
                    supplierId = body.supplierId,
                    costCenterId = body.costCenterId,
                    categoryId = body.categoryId,
                    notes = body.notes,
                    documentType = body.documentType,
                    documentNumber = body.documentNumber,
                    installmentTotal = body.installmentTotal,
                    createdBy = userId
                )
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Dados inválidos: ${it.message}"))
            }
            val id = runCatching { sessionManager.withApiUser(user) { service.create(tx) } }.getOrElse {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao criar transação"))
            }
            call.respond(HttpStatusCode.Created, IdResponse(id))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val existing = service.findById(id)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Transação não encontrada"))
            val body = runCatching { call.receive<UpdateTransactionRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido"))
            }
            val updated = runCatching {
                existing.copy(
                    description = body.description,
                    amount = Money(BigDecimal(body.amount)),
                    issueDate = LocalDateTime.parse(body.issueDate, dtFmt),
                    dueDate = LocalDateTime.parse(body.dueDate, dtFmt),
                    accountId = body.accountId,
                    supplierId = body.supplierId,
                    costCenterId = body.costCenterId,
                    categoryId = body.categoryId,
                    notes = body.notes,
                    documentType = body.documentType,
                    documentNumber = body.documentNumber
                )
            }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Dados inválidos: ${it.message}"))
            }
            runCatching { sessionManager.withApiUser(user) { service.update(updated) } }.getOrElse {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao atualizar"))
            }
            call.respond(HttpStatusCode.OK, MessageResponse("Transação atualizada"))
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            runCatching { sessionManager.withApiUser(user) { service.cancel(id) } }.getOrElse {
                return@delete call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao cancelar"))
            }
            call.respond(HttpStatusCode.OK, MessageResponse("Transação cancelada"))
        }

        post("/{id}/pay") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val body = runCatching { call.receive<PaymentRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido"))
            }
            runCatching {
                sessionManager.withApiUser(user) {
                    service.recordPayment(
                        id,
                        LocalDateTime.parse(body.paymentDate, dtFmt),
                        Money(BigDecimal(body.paidAmount))
                    )
                }
            }.getOrElse {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao registrar pagamento"))
            }
            call.respond(HttpStatusCode.OK, MessageResponse("Pagamento registrado"))
        }

        post("/{id}/reverse") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val body = runCatching { call.receive<ReversalRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido"))
            }
            val reversalId = runCatching {
                sessionManager.withApiUser(user) { service.reverseTransaction(id, body.justification) }
            }.getOrElse {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao estornar"))
            }
            call.respond(HttpStatusCode.Created, IdResponse(reversalId))
        }

        get("/{id}/timeline") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val events = service.getTimeline(id).map {
                TimelineEventDto(
                    id          = it.id,
                    eventType   = it.eventType.name,
                    message     = it.message,
                    amount      = it.amountValue?.toString(),
                    statusFrom  = it.statusFrom?.name,
                    statusTo    = it.statusTo?.name,
                    performedBy = it.performedBy,
                    createdAt   = it.createdAt.format(dtFmt)
                )
            }
            call.respond(events)
        }
    }
}

private fun Transaction.toDto() = TransactionDto(
    id                  = id,
    type                = type.name,
    status              = status.name,
    description         = description,
    amount              = amount.toString(),
    issueDate           = issueDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    dueDate             = dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    paymentDate         = paymentDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    paidAmount          = paidAmount?.toString(),
    accountId           = accountId,
    supplierId          = supplierId,
    costCenterId        = costCenterId,
    categoryId          = categoryId,
    notes               = notes,
    documentType        = documentType,
    documentNumber      = documentNumber,
    installmentCurrent  = installmentCurrent,
    installmentTotal    = installmentTotal,
    employeeId          = employeeId,
    ofxFitId            = ofxFitId,
    reconciledWithFitId = reconciledWithFitId
)
