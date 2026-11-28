package com.example.biteguardai

import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.audit.DefaultAuditRepository
import com.biteguard.core.data.db.AuditLogDataSource
import com.biteguard.core.data.db.InMemoryAuditLogDataSource
import com.biteguard.core.data.profile.InMemoryProfileRepository
import com.biteguard.core.domain.di.coreDomainModule
import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.domain.repository.ProfileRepository
import com.biteguard.feature.profile.ProfileViewModel
import com.biteguard.feature.profile.di.featureProfileModule
import com.biteguard.feature.scanner.ScannerViewModel
import com.biteguard.feature.scanner.di.featureScannerModule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.check.checkModules

/**
 * NFR-T4 & NFR-T6: Automated Koin dependency injection graph validation via `checkModules()`.
 */
class KoinModuleVerificationTest {

    @Test
    fun `verify Koin dependency graph across all modules`() {
        val testDataModule = module {
            single<ProfileRepository> { InMemoryProfileRepository() }
            single<AuditLogDataSource> { InMemoryAuditLogDataSource() }
            single<AiStreamDataSource> {
                object : AiStreamDataSource {
                    override val isApiKeyConfigured: Boolean = false
                    override fun streamLabelAudit(
                        rawExtractedText: String,
                        profile: com.biteguard.core.model.UserDietaryProfile,
                        imageJpegBytes: ByteArray?
                    ) = emptyFlow<String>()
                }
            }
            single<AuditRepository> {
                DefaultAuditRepository(
                    aiStreamDataSource = get(),
                    auditLogDataSource = get(),
                    offlineAuditEngine = get()
                )
            }
        }

        val app = startKoin {
            modules(
                listOf(
                    coreDomainModule,
                    testDataModule,
                    featureProfileModule,
                    featureScannerModule,
                    appModule
                )
            )
        }
        try {
            assertNotNull(app.koin.get<MainViewModel>())
            assertNotNull(app.koin.get<ProfileViewModel>())
            assertNotNull(app.koin.get<ScannerViewModel>())
            app.checkModules()
        } finally {
            stopKoin()
        }
    }
}
