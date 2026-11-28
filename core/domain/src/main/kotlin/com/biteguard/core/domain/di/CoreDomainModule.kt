package com.biteguard.core.domain.di

import com.biteguard.core.domain.engine.OfflineAuditEngine
import com.biteguard.core.domain.engine.RuleBasedOfflineAuditEngine
import com.biteguard.core.domain.usecase.AuditLabelUseCase
import com.biteguard.core.domain.usecase.EvaluateOfflineLabelUseCase
import com.biteguard.core.domain.usecase.ExtractHighlightTermsUseCase
import com.biteguard.core.domain.usecase.FilterScanHistoryUseCase
import com.biteguard.core.domain.usecase.SubmitAuditFeedbackUseCase
import org.koin.dsl.module

val coreDomainModule = module {
    single<OfflineAuditEngine> { RuleBasedOfflineAuditEngine() }
    factory { AuditLabelUseCase(auditRepository = get()) }
    factory { EvaluateOfflineLabelUseCase(auditRepository = get()) }
    factory { SubmitAuditFeedbackUseCase(auditRepository = get()) }
    factory { ExtractHighlightTermsUseCase(offlineAuditEngine = get()) }
    factory { FilterScanHistoryUseCase() }
}
