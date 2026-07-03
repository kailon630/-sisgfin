package br.com.sisgfin.api

import br.com.sisgfin.*
import br.com.sisgfin.api.routes.*
import br.com.sisgfin.cashflow.CashFlowService
import br.com.sisgfin.CostCenterService
import br.com.sisgfin.financial.categories.ExpenseCategoryService
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.swagger.*
import kotlinx.serialization.json.Json

const val API_PORT = 8080

fun createKtorServer(
    authService: AuthService,
    transactionService: TransactionService,
    accountService: FinancialAccountService,
    supplierService: SupplierService,
    categoryService: ExpenseCategoryService,
    costCenterService: CostCenterService,
    cashFlowService: CashFlowService,
    transactionRepository: TransactionRepository,
    accountRepository: FinancialAccountRepository,
    userRepository: UserRepository,
    sessionManager: SessionManager
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, port = API_PORT) {

        // ── Plugins ───────────────────────────────────────────────────────────

        install(ContentNegotiation) {
            json(Json { prettyPrint = true; ignoreUnknownKeys = true })
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Erro interno: ${cause.message}")
                )
            }
        }

        install(Authentication) {
            jwt("jwt") {
                realm     = JWT_REALM
                verifier(
                    JWT.require(Algorithm.HMAC256(JWT_SECRET))
                        .withIssuer(JWT_ISSUER)
                        .withAudience(JWT_AUDIENCE)
                        .build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("userId").asInt() != null)
                        JWTPrincipal(credential.payload)
                    else null
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token inválido ou ausente"))
                }
            }
        }

        // ── Rotas ─────────────────────────────────────────────────────────────

        routing {
            // Swagger UI — http://localhost:8080/swagger
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

            route("/api") {
                // Auth (login é público, /me requer JWT)
                authRoutes(authService, userRepository)

                // Todos os demais endpoints requerem JWT
                authenticate("jwt") {
                    transactionRoutes(transactionService, sessionManager, userRepository)
                    accountRoutes(accountService, sessionManager, userRepository)
                    supplierRoutes(supplierService, sessionManager, userRepository)
                    referenceRoutes(categoryService, costCenterService)
                    cashFlowRoutes(cashFlowService)
                    statementRoutes(transactionRepository, accountRepository)
                }
            }
        }
    }
