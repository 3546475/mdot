package com.mdot.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.database.CompAdjustmentDao
import com.mdot.app.core.database.DbKeyManager
import com.mdot.app.core.database.HolidayDao
import com.mdot.app.core.database.RecordDao
import com.mdot.app.core.database.ShiftDao
import com.mdot.app.core.database.SqlcipherFastKdf
import com.mdot.app.core.datastore.SettingsDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher io: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + io)
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("settings") },
            // 文件级损坏（断电半写/位腐）时重置为空配置而非每次读取抛 CorruptionException 崩溃
            // （13 文档 B2-01b）；密钥密文随之消失 → DatabaseModule 按"密文不在但库文件在"隔离重建
            corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler(
                produceNewData = { androidx.datastore.preferences.core.emptyPreferences() },
            ),
        )
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Volatile
    private var sqlcipherLoaded = false

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DbKeyManager,
        settings: SettingsDataSource,
        @ApplicationScope warmupScope: CoroutineScope,
    ): AppDatabase {
        if (!sqlcipherLoaded) {
            synchronized(this) {
                if (!sqlcipherLoaded) {
                    System.loadLibrary("sqlcipher")
                    sqlcipherLoaded = true
                }
            }
        }
        // 库密钥包裹在 DataStore；读取密钥时顺带拿到 raw key 标记（同库，一次 IO）
        val outcome = runBlocking { keyManager.getOrCreateDbKey() }
        val passphrase = outcome.passphrase
        var rawKey = runBlocking { settings.dbFastKdfFlow.first() }
        // 密钥不可用需隔离重建的两种情形（13 文档 B2-01 / B2-01b）：
        // ① reset：密文在但当前主密钥解不开（系统备份/OEM 迁移移来了旧密文）；
        // ② 密文不在但库文件还在：DataStore 曾被损坏重置（corruptionHandler 清空）而旧加密库留存——旧钥不可恢复
        val dbExistsBefore = context.getDatabasePath(AppDatabase.NAME).exists()
        if (outcome.reset || (dbExistsBefore && !outcome.cipherExisted)) {
            // 旧加密库用新钥必打不开，隔离重建空库（旧文件改名保留在 lost+found/，用户可从应用内备份恢复）
            quarantineOldDatabase(context, AppDatabase.NAME)
            rawKey = true // 空库直接以 raw key 创建；rekey 标记同步置位，避免旧标记残留走 byte[] 打开
            runBlocking { settings.setDbFastKdf(true) }
        }
        if (!rawKey) {
            // 旧库以 byte[]（PBKDF2）打开并 rekey 到 raw key；新装/空库直接置位。
            // 迁移失败返回 false → 回退 byte[] 打开，不丢数据。
            rawKey = SqlcipherFastKdf.ensureRawKey(context, AppDatabase.NAME, passphrase)
            if (rawKey) runBlocking { settings.setDbFastKdf(true) }
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SqlcipherFastKdf.factory(passphrase, rawKey))
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .addCallback(AppDatabase.SEED_CALLBACK)
            .build()
        // 首次注入即后台预热：SQLCipher 打开/KDF/建库都在 IO 线程完成，
        // 后续首个页面 VM 拿 DAO 时不再触发主线程阻塞（13 文档 B3-08）
        warmupScope.launch { runCatching { db.query("SELECT 1", null).use { it.moveToFirst() } } }
        return db
    }

    /** 密钥重置后隔离旧加密库：改名移入 databases/lost+found/（不删除，留人工恢复余地），同名旧库不再被打开 */
    private fun quarantineOldDatabase(context: Context, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return
        val dir = File(context.getDatabasePath(dbName).parentFile, "lost+found").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        listOf(dbFile, File(dbFile.path + "-journal"), File(dbFile.path + "-wal"), File(dbFile.path + "-shm"))
            .filter { it.exists() }
            .forEach { f ->
                runCatching {
                    if (!f.renameTo(File(dir, "${f.name}.$stamp"))) f.delete()
                }
            }
    }

    @Provides
    fun provideRecordDao(db: AppDatabase): RecordDao = db.recordDao()

    @Provides
    fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideCompAdjustmentDao(db: AppDatabase): CompAdjustmentDao = db.compAdjustmentDao()

    @Provides
    fun provideHolidayDao(db: AppDatabase): HolidayDao = db.holidayDao()

    @Provides
    fun provideSiteProjectDao(db: AppDatabase): com.mdot.app.core.database.SiteProjectDao = db.siteProjectDao()

    @Provides
    fun provideSiteAttendanceDao(db: AppDatabase): com.mdot.app.core.database.SiteAttendanceDao = db.siteAttendanceDao()

    @Provides
    fun provideSiteAdvanceDao(db: AppDatabase): com.mdot.app.core.database.SiteAdvanceDao = db.siteAdvanceDao()

    @Provides
    fun provideSitePieceWorkDao(db: AppDatabase): com.mdot.app.core.database.SitePieceWorkDao = db.sitePieceWorkDao()

    @Provides
    fun provideSiteSettlementDao(db: AppDatabase): com.mdot.app.core.database.SiteSettlementDao = db.siteSettlementDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** 05 文档 §1：连接 10s / 读取 15s / 写入 30s。
     *  明文 http 守门（13 文档 B2-04）：network_security_config 因用户自配 NAS 只能全局放行明文，
     *  代码层收窄——仅经 CleartextGate 登记过的 host（用户显式配置的存储源/数据源）允许 http。 */
    @Provides
    @Singleton
    fun provideOkHttpClient(cleartextGate: com.mdot.app.core.network.CleartextGate): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.scheme == "http" && !cleartextGate.isAllowed(req.url.host)) {
                    throw java.io.IOException(
                        "明文 http 仅允许用于已配置的存储源/数据源（${req.url.host} 未登记），请改用 https",
                    )
                }
                chain.proceed(req)
            }
            .build()
}
