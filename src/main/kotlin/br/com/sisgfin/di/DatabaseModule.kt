package br.com.sisgfin.di

import br.com.sisgfin.DatabaseFactory
import org.koin.dsl.module

val databaseModule = module {
    single { DatabaseFactory }
}
