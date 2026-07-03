package br.com.sisgfin.api.routes

import br.com.sisgfin.*
import br.com.sisgfin.api.*
import br.com.sisgfin.financial.money.Money
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal

fun Route.accountRoutes(
    service: FinancialAccountService,
    sessionManager: SessionManager,
    userRepository: UserRepository
) {
    route("/accounts") {

        get {
            call.respond(service.listAll().map { it.toDto() })
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val acc = service.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Conta não encontrada"))
            call.respond(acc.toDto())
        }

        post {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val body = runCatching { call.receive<CreateAccountRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido"))
            }
            val acc = runCatching {
                FinancialAccount(
                    name             = body.name,
                    bankName         = body.bankName,
                    agency           = body.agency,
                    accountNumber    = body.accountNumber,
                    accountType      = FinancialAccountType.valueOf(body.accountType),
                    initialBalance   = Money(BigDecimal(body.initialBalance)),
                    investmentBroker = body.investmentBroker
                )
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Dados inválidos: ${it.message}"))
            }
            val id = runCatching { sessionManager.withApiUser(user) { service.save(acc); 0 } }.getOrElse {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao salvar"))
            }
            call.respond(HttpStatusCode.Created, MessageResponse("Conta criada"))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val existing = service.findById(id)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Conta não encontrada"))
            val body = runCatching { call.receive<CreateAccountRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido"))
            }
            val updated = runCatching {
                existing.copy(
                    name             = body.name,
                    bankName         = body.bankName,
                    agency           = body.agency,
                    accountNumber    = body.accountNumber,
                    accountType      = FinancialAccountType.valueOf(body.accountType),
                    initialBalance   = Money(BigDecimal(body.initialBalance)),
                    investmentBroker = body.investmentBroker
                )
            }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Dados inválidos: ${it.message}"))
            }
            runCatching { sessionManager.withApiUser(user) { service.save(updated) } }.getOrElse {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao atualizar"))
            }
            call.respond(HttpStatusCode.OK, MessageResponse("Conta atualizada"))
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            runCatching { sessionManager.withApiUser(user) { service.toggleActive(id) } }.getOrElse {
                return@delete call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao inativar"))
            }
            call.respond(HttpStatusCode.OK, MessageResponse("Conta inativada"))
        }

        get("/{id}/balance") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val acc = service.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Conta não encontrada"))
            val balance = service.calculateBalance(id)
            call.respond(BalanceResponse(id, acc.name, balance.toString()))
        }
    }
}

private fun FinancialAccount.toDto() = AccountDto(
    id               = id,
    name             = name,
    bankName         = bankName,
    agency           = agency,
    accountNumber    = accountNumber,
    accountType      = accountType.name,
    initialBalance   = initialBalance.toString(),
    investmentBroker = investmentBroker,
    isActive         = isActive
)
