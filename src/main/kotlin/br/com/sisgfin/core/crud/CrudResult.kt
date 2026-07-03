package br.com.sisgfin.core.crud

import br.com.sisgfin.core.result.Result

typealias CrudResult<T> = Result<T>

sealed class CrudOperation {
    data object Load : CrudOperation()
    data object Save : CrudOperation()
    data object Delete : CrudOperation()
    data object Toggle : CrudOperation()
}
