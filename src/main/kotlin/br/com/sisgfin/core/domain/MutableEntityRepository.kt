package br.com.sisgfin.core.domain

interface MutableEntityRepository<T : Identifiable> {
    fun findAll(): List<T>
    fun findById(id: Int): T?
    fun insert(entity: T): Int
    fun update(entity: T)
}
