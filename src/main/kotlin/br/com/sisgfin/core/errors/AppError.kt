package br.com.sisgfin.core.errors

sealed class AppError(
    val userMessage: String,
    val technicalMessage: String? = null,
    val cause: Throwable? = null
) {
    class Operational(
        userMessage: String,
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(userMessage, technicalMessage, cause)

    class Validation(
        message: String,
        val fields: Map<String, String> = emptyMap()
    ) : AppError(message, message)

    class NotFound(
        entity: String,
        id: Int? = null
    ) : AppError(
        userMessage = if (id != null) "$entity (id=$id) não encontrado." else "$entity não encontrado.",
        technicalMessage = "NOT_FOUND: $entity${id?.let { " id=$it" } ?: ""}"
    )

    class Unauthorized(
        message: String = "Você não tem permissão para esta operação."
    ) : AppError(message, "UNAUTHORIZED")

    class Unexpected(
        cause: Throwable
    ) : AppError(
        userMessage = "Ocorreu um erro inesperado. Tente novamente.",
        technicalMessage = cause.message,
        cause = cause
    )
}
