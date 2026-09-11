package com.mdot.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库（schemaVersion = 3，08 文档；v3 起新增工地记工 site_* 5 表，12 文档 §3.2）。
 * SQLCipher 加密由 DatabaseModule 通过 openHelperFactory 注入。
 */
@Database(
    entities = [
        DailyRecordEntity::class,
        ShiftEntity::class,
        CompAdjustmentEntity::class,
        HolidayCacheEntity::class,
        SiteProjectEntity::class,
        SiteAttendanceEntity::class,
        SiteAdvanceEntity::class,
        SitePieceWorkEntity::class,
        SiteSettlementEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun shiftDao(): ShiftDao
    abstract fun compAdjustmentDao(): CompAdjustmentDao
    abstract fun holidayDao(): HolidayDao
    abstract fun siteProjectDao(): SiteProjectDao
    abstract fun siteAttendanceDao(): SiteAttendanceDao
    abstract fun siteAdvanceDao(): SiteAdvanceDao
    abstract fun sitePieceWorkDao(): SitePieceWorkDao
    abstract fun siteSettlementDao(): SiteSettlementDao

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

        /** v2→v3：新增工地记工 site_* 5 表（12 文档 §3.2；列定义须与 Entity 完全一致） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_project` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `name` TEXT NOT NULL,
                      `sort` INTEGER NOT NULL,
                      `archived` INTEGER NOT NULL,
                      `base_minutes` INTEGER NOT NULL DEFAULT 480,
                      `daily_rate_cents` INTEGER NOT NULL DEFAULT 0,
                      `ot_mode` TEXT NOT NULL DEFAULT 'BY_DAY',
                      `ot_base_minutes` INTEGER NOT NULL DEFAULT 360,
                      `ot_hourly_cents` INTEGER NOT NULL DEFAULT 0,
                      `note` TEXT,
                      `created_at` INTEGER NOT NULL,
                      `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_attendance` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `project_id` INTEGER NOT NULL,
                      `date` TEXT NOT NULL,
                      `day_status` TEXT NOT NULL,
                      `half_of_day` TEXT,
                      `work_minutes` INTEGER NOT NULL DEFAULT 0,
                      `ot_minutes` INTEGER NOT NULL DEFAULT 0,
                      `rate_cents` INTEGER NOT NULL,
                      `base_minutes_snapshot` INTEGER NOT NULL,
                      `ot_mode_snapshot` TEXT NOT NULL,
                      `ot_base_minutes_snapshot` INTEGER NOT NULL,
                      `ot_hourly_cents_snapshot` INTEGER NOT NULL,
                      `work_pay_cents` INTEGER NOT NULL DEFAULT 0,
                      `ot_pay_cents` INTEGER NOT NULL DEFAULT 0,
                      `note` TEXT,
                      `photos` TEXT,
                      `settlement_id` INTEGER,
                      `created_at` INTEGER NOT NULL,
                      `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_site_attendance_project_id_date` ON `site_attendance` (`project_id`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_attendance_date` ON `site_attendance` (`date`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_advance` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `project_id` INTEGER NOT NULL,
                      `date` TEXT NOT NULL,
                      `amount_cents` INTEGER NOT NULL,
                      `purpose` TEXT NOT NULL DEFAULT 'OTHER',
                      `note` TEXT,
                      `photos` TEXT,
                      `settlement_id` INTEGER,
                      `created_at` INTEGER NOT NULL,
                      `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_advance_project_id_date` ON `site_advance` (`project_id`, `date`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_piece_work` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `project_id` INTEGER NOT NULL,
                      `date` TEXT NOT NULL,
                      `item_name` TEXT NOT NULL DEFAULT '',
                      `unit` TEXT NOT NULL,
                      `quantity_milli` INTEGER NOT NULL DEFAULT 0,
                      `unit_price_cents` INTEGER NOT NULL DEFAULT 0,
                      `amount_cents` INTEGER NOT NULL,
                      `note` TEXT,
                      `photos` TEXT,
                      `settlement_id` INTEGER,
                      `created_at` INTEGER NOT NULL,
                      `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_piece_work_project_id_date` ON `site_piece_work` (`project_id`, `date`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_settlement` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `project_id` INTEGER NOT NULL,
                      `period_start` TEXT NOT NULL,
                      `period_end` TEXT NOT NULL,
                      `work_pay_cents` INTEGER NOT NULL,
                      `piece_pay_cents` INTEGER NOT NULL,
                      `advance_total_cents` INTEGER NOT NULL,
                      `net_cents` INTEGER NOT NULL,
                      `att_count` INTEGER NOT NULL,
                      `advance_count` INTEGER NOT NULL,
                      `snapshot_json` TEXT NOT NULL,
                      `note` TEXT,
                      `created_at` INTEGER NOT NULL,
                      `is_partial` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_settlement_project_id_period_end` ON `site_settlement` (`project_id`, `period_end`)")
            }
        }

        /** v3→v4：site_settlement 加 is_partial（部分结算单，从待结余额扣减、不锁记录） */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE site_settlement ADD COLUMN is_partial INTEGER NOT NULL DEFAULT 0")
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
