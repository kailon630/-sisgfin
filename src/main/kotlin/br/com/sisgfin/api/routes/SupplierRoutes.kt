package br.com.sisgfin.api.routes

import br.com.sisgfin.*
import br.com.sisgfin.api.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.supplierRoutes(
    service: SupplierService,
    sessionManager: SessionManager,
    userRepository: UserRepository
) {
    route("/suppliers") {

        get {
            call.respond(service.listAll().map { it.toDto() })
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val sup = service.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Fornecedor não encontrado"))
            call.respond(sup.toDto())
        }

        post {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val body = runCatching { call.receive<CreateSupplierRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido"))
            }
            val supplier = Supplier(
                document  = body.document,
                name      = body.name,
                tradeName = body.tradeName,
                email     = body.email,
                phone     = body.phone,
                pixKey    = body.pixKey,
                bank      = body.bank,
                agency    = body.agency,
                account   = body.account,
                notes     = body.notes
            )
            runCatching { sessionManager.withApiUser(user) { service.save(supplier) } }.getOrElse {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao criar fornecedor"))
            }
            call.respond(HttpStatusCode.Created, MessageResponse("Fornecedor criado"))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID inválido"))
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.getClaim("userId", Int::class)!!
            val user = userRepository.findById(userId)
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário inválido"))
            val existing = service.findById(id)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Fornecedor não encontrado"))
            val body = runCatching { call.receive<CreateSupplierRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo inválido"))
            }
            val updated = existing.copy(
                document  = body.document,
                name      = body.name,
                tradeName = body.tradeName,
                email     = body.email,
                phone     = body.phone,
                pixKey    = body.pixKey,
                bank      = body.bank,
                agency    = body.agency,
                account   = body.account,
                notes     = body.notes
            )
            runCatching { sessionManager.withApiUser(user) { service.save(updated) } }.getOrElse {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(it.message ?: "Erro ao atualizar"))
            }
            call.respond(HttpStatusCode.OK, MessageResponse("Fornecedor atualizado"))
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
            call.respond(HttpStatusCode.OK, MessageResponse("Fornecedor inativado"))
        }
    }
}

private fun Supplier.toDto() = SupplierDto(
    id        = id,
    document  = document,
    name      = name,
    tradeName = tradeName,
    email     = email,
    phone     = phone,
    pixKey    = pixKey,
    bank      = bank,
    agency    = agency,
    account   = account,
    notes     = notes,
    isActive  = isActive
)
