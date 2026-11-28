package com.biteguard.feature.profile.di

import com.biteguard.feature.profile.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureProfileModule = module {
    viewModel {
        ProfileViewModel(
            profileRepository = get(),
            auditRepository = get(),
            filterScanHistoryUseCase = get(),
            submitAuditFeedbackUseCase = get(),
            extractHighlightTermsUseCase = get()
        )
    }
}
