package com.example.biteguardai

import android.app.Application
import com.biteguard.core.data.di.coreDataModule
import com.biteguard.feature.profile.di.featureProfileModule
import com.biteguard.feature.scanner.di.featureScannerModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

val allBiteGuardModules = listOf(
    coreDataModule,
    featureProfileModule,
    featureScannerModule
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
