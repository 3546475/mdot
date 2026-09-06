package com.mdot.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库（schemaVersion = 2，08 文档）。
 * SQLCipher 加密由 DatabaseModule 通过 openHelperFactory 注入。
 */
@Database(
    entities = [
        DailyRecordEntity::class,
        ShiftEntity::class,
        CompAdjustmentEntity::class,
        HolidayCacheEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun shiftDao(): ShiftDao
    abstract fun compAdjustmentDao(): CompAdjustmentDao
    abstract fun holidayDao(): HolidayDao

    companion object {
        const val NAME = "mdot.db"

        /** v1→v2：daily_record 表添加 work_system 列，唯一索引改为 (date,type,work_system) */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_record ADD COLUMN work_system TEXT NOT NULL DEFAULT 'STANDARD'")
                // SQLite 不支持直接修改索引，先删后建
                db.execSQL("DROP INDEX IF EXISTS index_daily_record_date_type")
                db.execSQL("CREATE UNIQUE INDEX index_daily_record_date_type_work_system ON daily_record (date, type, work_system)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_record_work_system ON daily_record (work_system)")
            }
        }

        /** 预置班次种入（08 文档 §3.2），幂等由 onCreate 只跑一次保证 */
        val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """
                    INSERT INTO shift(name, sort, builtin, hidden, rest) VALUES
                     ('白班',0,1,0,0),
                     ('夜班',1,1,0,0),
                     ('休息',2,1,0,1),
                     ('早班',3,1,0,0),
                     ('中班',4,1,0,0),
                     ('晚班',5,1,0,0)
                    """.trimIndent()
                )
            }
        }
    }
}
