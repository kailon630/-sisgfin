package br.com.sisgfin.core.errors

import java.util.logging.Level
import java.util.logging.Logger

object AppLogger {
    private val logger: Logger = Logger.getLogger("SisgFin")

    fun error(error: AppError) {
        val level = when (error) {
            is AppError.Unexpected -> Level.SEVERE
            is AppError.Operational -> Level.WARNING
            is AppError.Validation -> Level.INFO
            is AppError.NotFound -> Level.FINE
            is AppError.Unauthorized -> Level.WARNING
        }
        logger.log(level, "[${error::class.simpleName}] ${error.userMessage}") { error.cause }
        error.technicalMessage?.let { logger.fine(it) }
    }

    fun info(message: String) {
        logger.info(message)
    }
}
