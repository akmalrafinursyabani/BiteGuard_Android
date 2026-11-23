package com.biteguard.feature.scanner.di

import com.biteguard.feature.scanner.ScannerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureScannerModule = module {
    viewModel {
        ScannerViewModel(
            auditRepository = get(),
            profileRepository = get()
        )
    }
}
