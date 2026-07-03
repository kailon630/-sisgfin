package br.com.sisgfin.api.routes

import br.com.sisgfin.AuthService
import br.com.sisgfin.UserRepository
import br.com.sisgfin.UserRole
import br.com.sisgfin.api.ErrorResponse
import br.com.sisgfin.api.LoginRequest
import br.com.sisgfin.api.LoginResponse
import br.com.sisgfin.api.UserDto
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

const val JWT_SECRET = "sisgfin-secret-change-in-production-2024"
const val JWT_ISSUER = "sisgfin"
const val JWT_AUDIENCE = "sisgfin-api"
const val JWT_REALM = "SisgFin API"

fun Route.authRoutes(authService: AuthService, userRepository: UserRepository) {
    route("/auth") {
        post("/login") {
            val body = runCatching { call.receive<LoginRequest>() }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo da requisição inválido"))
                return@post
            }
            val user = authService.authenticate(body.username, body.password)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Credenciais inválidas"))
                return@post
            }
            val token = JWT.create()
                .withIssuer(JWT_ISSUER)
                .withAudience(JWT_AUDIENCE)
                .withClaim("userId", user.id)
                .withClaim("role", user.role.name)
                .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
                .sign(Algorithm.HMAC256(JWT_SECRET))
            call.respond(LoginResponse(token, user.id, user.username, user.role.name))
        }

        authenticate("jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getClaim("userId", Int::class)!!
                val user = userRepository.findById(userId)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Usuário não encontrado"))
                    return@get
                }
                call.respond(UserDto(user.id, user.name, user.username, user.email, user.role.name, user.isActive))
            }
        }
    }
}
