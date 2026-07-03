package br.com.sisgfin.core.crud

import br.com.sisgfin.core.domain.Identifiable

interface CrudOperations<T : Identifiable> {
    fun listAll(): List<T>
    fun save(item: T)
    fun toggleActive(id: Int)
}
