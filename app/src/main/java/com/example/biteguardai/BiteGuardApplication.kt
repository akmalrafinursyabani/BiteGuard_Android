package com.example.biteguardai

import android.app.Application
import com.biteguard.core.data.di.coreDataModule
import com.biteguard.core.domain.di.coreDomainModule
import com.biteguard.feature.profile.di.featureProfileModule
import com.biteguard.feature.scanner.di.featureScannerModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { MainViewModel(profileRepository = get()) }
}

val allBiteGuardModules = listOf(
    coreDomainModule,
    coreDataModule,
    featureProfileModule,
    featureScannerModule,
    appModule
)

class BiteGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BiteGuardApplication)
            modules(allBiteGuardModules)
        }
    }
}
