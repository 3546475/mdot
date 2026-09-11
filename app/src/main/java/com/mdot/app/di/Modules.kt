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
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
    ): AppDatabase {
        if (!sqlcipherLoaded) {
            synchronized(this) {
                if (!sqlcipherLoaded) {
                    System.loadLibrary("sqlcipher")
                    sqlcipherLoaded = true
                }
            }
        }
        // 库密钥包裹在 DataStore，读取密钥时顺带拿到 raw key 标记（同库，一次 IO）
        val passphrase = runBlocking { keyManager.getOrCreatePassphrase() }
        var rawKey = runBlocking { settings.dbFastKdfFlow.first() }
        if (!rawKey) {
            // 旧库以 byte[]（PBKDF2）打开并 rekey 到 raw key；新装/空库直接置位。
            // 迁移失败返回 false → 回退 byte[] 打开，不丢数据。
            rawKey = SqlcipherFastKdf.ensureRawKey(context, AppDatabase.NAME, passphrase)
            if (rawKey) runBlocking { settings.setDbFastKdf(true) }
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SqlcipherFastKdf.factory(passphrase, rawKey))
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .addCallback(AppDatabase.SEED_CALLBACK)
            .build()
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

    /** 05 文档 §1：连接 10s / 读取 15s / 写入 30s */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
