package com.biteguard.core.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.FlaggedIngredient
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.TelemetryEvent
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * FR-05 & FR-06: On-device Encrypted Audit Log & Telemetry Database.
 * Uses SQLCipher (net.zetetic.database.sqlcipher) backed by an Android Keystore AES-256-GCM key,
 * with automatic fallback to standard Android SQLite in unit test / robolectric environments
 * where native libsqlcipher.so is not loaded.
 */
interface AuditLogDataSource {
    val historyFlow: Flow<List<AuditVerdict>>
    val telemetryQueueFlow: Flow<List<TelemetryEvent>>

    suspend fun insertVerdict(verdict: AuditVerdict)
    suspend fun updateVerdictFeedback(verdictId: String, feedback: UserFeedback)
    suspend fun clearHistory()
    suspend fun enqueueTelemetry(event: TelemetryEvent)
    suspend fun markTelemetrySynced(ids: List<String>)
}

@Serializable
internal data class SerializableFlaggedIngredient(
    val name: String,
    val riskReason: String,
    val severity: String
)

class EncryptedAuditDatabase(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuditLogDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _historyState = MutableStateFlow<List<AuditVerdict>>(emptyList())
    override val historyFlow: Flow<List<AuditVerdict>> = _historyState.asStateFlow()

    private val _telemetryState = MutableStateFlow<List<TelemetryEvent>>(emptyList())
    override val telemetryQueueFlow: Flow<List<TelemetryEvent>> = _telemetryState.asStateFlow()

    @Volatile
    private var initialized = false
    private var useSqlCipher = false
    private var cipherDb: net.zetetic.database.sqlcipher.SQLiteDatabase? = null
    private var fallbackDb: SQLiteDatabase? = null

    private suspend fun ensureInitialized() = withContext(ioDispatcher) {
        if (initialized) return@withContext
        synchronized(this@EncryptedAuditDatabase) {
            if (initialized) return@synchronized
            val dbFile: File = context.getDatabasePath("biteguard_encrypted_audit.db")
            dbFile.parentFile?.mkdirs()

            try {
                System.loadLibrary("sqlcipher")
                val passphrase = getOrCreateDatabasePassphrase()
                val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(
                    dbFile.absolutePath,
                    passphrase,
                    null,
                    null
                )
                db.execSQL(CREATE_AUDIT_TABLE_SQL)
                db.execSQL(CREATE_TELEMETRY_TABLE_SQL)
                cipherDb = db
                useSqlCipher = true
            } catch (_: Throwable) {
                val fallbackFile = context.getDatabasePath("biteguard_local_audit.db")
                val db = SQLiteDatabase.openOrCreateDatabase(fallbackFile, null)
                db.execSQL(CREATE_AUDIT_TABLE_SQL)
                db.execSQL(CREATE_TELEMETRY_TABLE_SQL)
                fallbackDb = db
                useSqlCipher = false
            }

            initialized = true
            refreshMemoryCachesSync()
        }
    }

    override suspend fun insertVerdict(verdict: AuditVerdict) = withContext(ioDispatcher) {
        ensureInitialized()
        val flagsJson = json.encodeToString(
            verdict.flaggedIngredients.map {
                SerializableFlaggedIngredient(it.name, it.riskReason, it.severity.name)
            }
        )
        val values = ContentValues().apply {
            put("id", verdict.id)
            put("safety_level", verdict.safetyLevel.name)
            put("summary", verdict.summary)
            put("flagged_json", flagsJson)
            put("raw_text", verdict.rawExtractedText)
            put("timestamp_ms", verdict.timestamp.toEpochMilli())
            put("is_offline", if (verdict.isOfflineFallback) 1 else 0)
            put("product_name", verdict.productName)
            put("thumbnail_blob", verdict.snapshotThumbnailBytes)
            put("ttft_ms", verdict.ttftMs)
            put("total_duration_ms", verdict.totalStreamDurationMs)
            put("user_feedback", verdict.userFeedback?.name)
        }

        if (useSqlCipher) {
            cipherDb?.insertWithOnConflict(
                "audit_verdicts",
                null,
                values,
                net.zetetic.database.sqlcipher.SQLiteDatabase.CONFLICT_REPLACE
            )
        } else {
            fallbackDb?.insertWithOnConflict(
                "audit_verdicts",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
        refreshMemoryCachesSync()
    }

    override suspend fun updateVerdictFeedback(verdictId: String, feedback: UserFeedback) =
        withContext(ioDispatcher) {
            ensureInitialized()
            val values = ContentValues().apply {
                put("user_feedback", feedback.name)
            }
            if (useSqlCipher) {
                cipherDb?.update("audit_verdicts", values, "id = ?", arrayOf(verdictId))
            } else {
                fallbackDb?.update("audit_verdicts", values, "id = ?", arrayOf(verdictId))
            }
            refreshMemoryCachesSync()
        }

    override suspend fun clearHistory() = withContext(ioDispatcher) {
        ensureInitialized()
        if (useSqlCipher) {
            cipherDb?.delete("audit_verdicts", null, null)
        } else {
            fallbackDb?.delete("audit_verdicts", null, null)
        }
        refreshMemoryCachesSync()
    }

    override suspend fun enqueueTelemetry(event: TelemetryEvent) = withContext(ioDispatcher) {
        ensureInitialized()
        val values = ContentValues().apply {
            put("id", event.id)
            put("prompt_execution_id", event.promptExecutionId)
            put("ttft_ms", event.ttftMs)
            put("total_duration_ms", event.totalStreamDurationMs)
            put("rating", event.rating.name)
            put("is_offline", if (event.isOfflineFallback) 1 else 0)
            put("timestamp_ms", event.timestamp.toEpochMilli())
            put("synced", if (event.synced) 1 else 0)
        }
        if (useSqlCipher) {
            cipherDb?.insertWithOnConflict(
                "telemetry_events",
                null,
                values,
                net.zetetic.database.sqlcipher.SQLiteDatabase.CONFLICT_REPLACE
            )
        } else {
            fallbackDb?.insertWithOnConflict(
                "telemetry_events",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
        refreshMemoryCachesSync()
    }

    override suspend fun markTelemetrySynced(ids: List<String>) = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext
        ensureInitialized()
        val values = ContentValues().apply { put("synced", 1) }
        for (id in ids) {
            if (useSqlCipher) {
                cipherDb?.update("telemetry_events", values, "id = ?", arrayOf(id))
            } else {
                fallbackDb?.update("telemetry_events", values, "id = ?", arrayOf(id))
            }
        }
        refreshMemoryCachesSync()
    }

    private fun refreshMemoryCachesSync() {
        val loadedVerdicts = mutableListOf<AuditVerdict>()
        val cursor = if (useSqlCipher) {
            cipherDb?.rawQuery("SELECT * FROM audit_verdicts ORDER BY timestamp_ms DESC", null)
        } else {
            fallbackDb?.rawQuery("SELECT * FROM audit_verdicts ORDER BY timestamp_ms DESC", null)
        }

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val safetyIdx = c.getColumnIndexOrThrow("safety_level")
            val summaryIdx = c.getColumnIndexOrThrow("summary")
            val flagsIdx = c.getColumnIndexOrThrow("flagged_json")
            val rawIdx = c.getColumnIndexOrThrow("raw_text")
            val tsIdx = c.getColumnIndexOrThrow("timestamp_ms")
            val offlineIdx = c.getColumnIndexOrThrow("is_offline")
            val prodIdx = c.getColumnIndexOrThrow("product_name")
            val thumbIdx = c.getColumnIndexOrThrow("thumbnail_blob")
            val ttftIdx = c.getColumnIndexOrThrow("ttft_ms")
            val durIdx = c.getColumnIndexOrThrow("total_duration_ms")
            val fbIdx = c.getColumnIndexOrThrow("user_feedback")

            while (c.moveToNext()) {
                val flagsJsonStr = c.getString(flagsIdx) ?: "[]"
                val parsedFlags = runCatching {
                    json.decodeFromString<List<SerializableFlaggedIngredient>>(flagsJsonStr).map {
                        FlaggedIngredient(
                            name = it.name,
                            riskReason = it.riskReason,
                            severity = runCatching { Severity.valueOf(it.severity) }.getOrDefault(Severity.MEDIUM)
                        )
                    }
                }.getOrDefault(emptyList())

                val feedbackName = if (c.isNull(fbIdx)) null else c.getString(fbIdx)
                val feedback = feedbackName?.let { name ->
                    runCatching { UserFeedback.valueOf(name) }.getOrNull()
                }

                loadedVerdicts.add(
                    AuditVerdict(
                        id = c.getString(idIdx),
                        safetyLevel = runCatching { SafetyLevel.valueOf(c.getString(safetyIdx)) }
                            .getOrDefault(SafetyLevel.CAUTION),
                        summary = c.getString(summaryIdx) ?: "",
                        flaggedIngredients = parsedFlags,
                        rawExtractedText = c.getString(rawIdx) ?: "",
                        timestamp = Instant.ofEpochMilli(c.getLong(tsIdx)),
                        isOfflineFallback = c.getInt(offlineIdx) == 1,
                        productName = c.getString(prodIdx) ?: "Scanned Label",
                        snapshotThumbnailBytes = if (c.isNull(thumbIdx)) null else c.getBlob(thumbIdx),
                        ttftMs = c.getLong(ttftIdx),
                        totalStreamDurationMs = c.getLong(durIdx),
                        userFeedback = feedback
                    )
                )
            }
        }
        _historyState.value = loadedVerdicts

        val loadedTelemetry = mutableListOf<TelemetryEvent>()
        val tCursor = if (useSqlCipher) {
            cipherDb?.rawQuery("SELECT * FROM telemetry_events ORDER BY timestamp_ms DESC", null)
        } else {
            fallbackDb?.rawQuery("SELECT * FROM telemetry_events ORDER BY timestamp_ms DESC", null)
        }
        tCursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val promptIdx = c.getColumnIndexOrThrow("prompt_execution_id")
            val ttftIdx = c.getColumnIndexOrThrow("ttft_ms")
            val durIdx = c.getColumnIndexOrThrow("total_duration_ms")
            val ratingIdx = c.getColumnIndexOrThrow("rating")
            val offIdx = c.getColumnIndexOrThrow("is_offline")
            val tsIdx = c.getColumnIndexOrThrow("timestamp_ms")
            val syncIdx = c.getColumnIndexOrThrow("synced")

            while (c.moveToNext()) {
                loadedTelemetry.add(
                    TelemetryEvent(
                        id = c.getString(idIdx),
                        promptExecutionId = c.getString(promptIdx),
                        ttftMs = c.getLong(ttftIdx),
                        totalStreamDurationMs = c.getLong(durIdx),
                        rating = runCatching { UserFeedback.valueOf(c.getString(ratingIdx)) }
                            .getOrDefault(UserFeedback.THUMBS_UP),
                        isOfflineFallback = c.getInt(offIdx) == 1,
                        timestamp = Instant.ofEpochMilli(c.getLong(tsIdx)),
                        synced = c.getInt(syncIdx) == 1
                    )
                )
            }
        }
        _telemetryState.value = loadedTelemetry
    }

    private fun getOrCreateDatabasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences("biteguard_keystore_prefs", Context.MODE_PRIVATE)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "biteguard_db_master_key"

        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }

        val secretKey = keyStore.getKey(alias, null) as SecretKey
        val existingCiphertext = prefs.getString("enc_db_passphrase", null)
        val existingIv = prefs.getString("enc_db_iv", null)

        if (existingCiphertext != null && existingIv != null) {
            return runCatching {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val ivBytes = Base64.decode(existingIv, Base64.NO_WRAP)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, ivBytes))
                cipher.doFinal(Base64.decode(existingCiphertext, Base64.NO_WRAP))
            }.getOrElse {
                generateAndStoreNewPassphrase(secretKey, prefs)
            }
        }
        return generateAndStoreNewPassphrase(secretKey, prefs)
    }

    private fun generateAndStoreNewPassphrase(
        secretKey: SecretKey,
        prefs: android.content.SharedPreferences
    ): ByteArray {
        val rawPassphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(rawPassphrase)
        prefs.edit()
            .putString("enc_db_passphrase", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("enc_db_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return rawPassphrase
    }

    companion object {
        private const val CREATE_AUDIT_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS audit_verdicts (
                id TEXT PRIMARY KEY NOT NULL,
                safety_level TEXT NOT NULL,
                summary TEXT NOT NULL,
                flagged_json TEXT NOT NULL,
                raw_text TEXT NOT NULL,
                timestamp_ms INTEGER NOT NULL,
                is_offline INTEGER NOT NULL DEFAULT 0,
                product_name TEXT NOT NULL,
                thumbnail_blob BLOB,
                ttft_ms INTEGER NOT NULL DEFAULT 0,
                total_duration_ms INTEGER NOT NULL DEFAULT 0,
                user_feedback TEXT
            );
        """

        private const val CREATE_TELEMETRY_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS telemetry_events (
                id TEXT PRIMARY KEY NOT NULL,
                prompt_execution_id TEXT NOT NULL,
                ttft_ms INTEGER NOT NULL,
                total_duration_ms INTEGER NOT NULL,
                rating TEXT NOT NULL,
                is_offline INTEGER NOT NULL DEFAULT 0,
                timestamp_ms INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            );
        """
    }
}

/**
 * In-memory AuditLogDataSource for deterministic unit testing (NFR-T6).
 */
class InMemoryAuditLogDataSource(
    initialVerdicts: List<AuditVerdict> = emptyList()
) : AuditLogDataSource {
    private val _history = MutableStateFlow(initialVerdicts)
    private val _telemetry = MutableStateFlow<List<TelemetryEvent>>(emptyList())

    override val historyFlow: Flow<List<AuditVerdict>> = _history.asStateFlow()
    override val telemetryQueueFlow: Flow<List<TelemetryEvent>> = _telemetry.asStateFlow()

    override suspend fun insertVerdict(verdict: AuditVerdict) {
        _history.value = listOf(verdict) + _history.value.filterNot { it.id == verdict.id }
    }

    override suspend fun updateVerdictFeedback(verdictId: String, feedback: UserFeedback) {
        _history.value = _history.value.map {
            if (it.id == verdictId) it.copy(userFeedback = feedback) else it
        }
    }

    override suspend fun clearHistory() {
        _history.value = emptyList()
    }

    override suspend fun enqueueTelemetry(event: TelemetryEvent) {
        _telemetry.value = listOf(event) + _telemetry.value
    }

    override suspend fun markTelemetrySynced(ids: List<String>) {
        _telemetry.value = _telemetry.value.map {
            if (it.id in ids) it.copy(synced = true) else it
        }
    }
}
