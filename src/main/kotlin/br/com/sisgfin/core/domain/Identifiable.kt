package br.com.sisgfin.core.domain

interface Identifiable {
    val id: Int
}

interface Activatable : Identifiable {
    val isActive: Boolean
}
