package br.com.sisgfin.core.result

import br.com.sisgfin.core.errors.AppError

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: AppError) : Result<Nothing>()
    data class Validation(val fieldErrors: Map<String, String>) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this !is Success

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorOrNull(): AppError? = when (this) {
        is Error -> error
        is Validation -> AppError.Validation(fieldErrors.values.joinToString("; "))
        is Success -> null
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Validation -> this
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (AppError) -> Unit): Result<T> {
        errorOrNull()?.let(action)
        return this
    }
}

inline fun <T> runCatchingResult(block: () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(br.com.sisgfin.core.errors.ErrorClassifier.classify(e))
    }

suspend inline fun <T> runCatchingResultIO(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(br.com.sisgfin.core.errors.ErrorClassifier.classify(e))
    }
