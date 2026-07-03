package br.com.sisgfin.core.errors

import org.jetbrains.exposed.exceptions.ExposedSQLException

class ErrorClassifier {
    companion object {
        fun classify(throwable: Throwable): AppError = when (throwable) {
            is IllegalArgumentException -> AppError.Validation(
                throwable.message ?: "Dados inválidos."
            )
            is IllegalStateException -> AppError.Operational(
                throwable.message ?: "Operação não permitida no estado atual."
            )
            is NoSuchElementException -> AppError.NotFound("Registro")
            is ExposedSQLException -> AppError.Validation(dbMessage(throwable))
            else -> AppError.Unexpected(throwable)
        }

        private fun dbMessage(e: ExposedSQLException): String {
            val msg = e.message?.lowercase() ?: ""
            return when {
                "unique" in msg -> "Já existe um registro com este código ou valor. Verifique os dados e tente novamente."
                "not null" in msg -> "Um campo obrigatório está vazio."
                "foreign key" in msg -> "Referência inválida: verifique os vínculos do registro."
                else -> "Erro ao salvar no banco de dados. Verifique os dados informados."
            }
        }
    }
}
