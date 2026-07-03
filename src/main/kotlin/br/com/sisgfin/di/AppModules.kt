package br.com.sisgfin.di

import br.com.sisgfin.NavigationState
import br.com.sisgfin.Screen
import org.koin.dsl.module

val appModule = module {
    single { NavigationState(Screen.Login) }
}

val appModules = listOf(
    databaseModule,
    repositoryModule,
    serviceModule,
    viewModelModule,
    appModule
)
