package com.biteguard.core.data.di

import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.ai.GeminiStreamDataSource
import com.biteguard.core.data.audit.DefaultAuditRepository
import com.biteguard.core.data.db.AuditLogDataSource
import com.biteguard.core.data.db.EncryptedAuditDatabase
import com.biteguard.core.data.profile.DataStoreProfileRepository
import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.domain.repository.ProfileRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreDataModule = module {
    single<ProfileRepository> { DataStoreProfileRepository(androidContext()) }
    single<AuditLogDataSource> { EncryptedAuditDatabase(androidContext()) }
    single<AiStreamDataSource> { GeminiStreamDataSource() }
    single<AuditRepository> {
        DefaultAuditRepository(
            aiStreamDataSource = get(),
            auditLogDataSource = get(),
            offlineAuditEngine = get()
        )
    }
}
