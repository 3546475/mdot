package com.mdot.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 工地记工表族（12 文档 §3.2，schemaVersion 2→3 一次建全）。
 * 单价/标准/金额均带「保存时快照」，项目标准变更不动历史（D3-rev）；
 * settlement_id 非空 = 已结算归档，Repository 拒绝改删（D6）。
 */

/** 记工项目（单人多项目台账，≤15 个在 Repository 层限制） */
@Entity(tableName = "site_project")
data class SiteProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    /** 完工归档（有记录的项目不可删只可归档，D1-rev） */
    val archived: Boolean = false,
    /** 点工标准：上班 X 分钟 = 1 个工（默认 8h） */
    @ColumnInfo(name = "base_minutes", defaultValue = "480") val baseMinutes: Int = 480,
    /** 1 个工 = Y 元（分） */
    @ColumnInfo(name = "daily_rate_cents", defaultValue = "0") val dailyRateCents: Long = 0,
    /** BY_DAY 加班按工 | BY_HOUR 按小时算 */
    @ColumnInfo(name = "ot_mode", defaultValue = "BY_DAY") val otMode: String = "BY_DAY",
    /** BY_DAY：加班 Z 分钟 = 1 个加班工（默认 6h） */
    @ColumnInfo(name = "ot_base_minutes", defaultValue = "360") val otBaseMinutes: Int = 360,
    /** BY_HOUR：加班时薪（分/小时）；0 = 自动按 日价÷上班基准 折算 */
    @ColumnInfo(name = "ot_hourly_cents", defaultValue = "0") val otHourlyCents: Long = 0,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 出勤记录（每天每项目最多一条；上班与加班同行；REST=显式休息，工钱 0） */
@Entity(
    tableName = "site_attendance",
    indices = [
        Index(value = ["project_id", "date"], unique = true),
        Index(value = ["date"]),
    ],
)
data class SiteAttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val date: LocalDate,
    /** WORK | REST */
    @ColumnInfo(name = "day_status") val dayStatus: String,
    /** NULL | AM | PM（半天时指明上/下午，仅展示语义，D2-rev） */
    @ColumnInfo(name = "half_of_day") val halfOfDay: String? = null,
    /** 上班分钟（0.5h 步进，≤1440；工数 = ÷ base_minutes） */
    @ColumnInfo(name = "work_minutes", defaultValue = "0") val workMinutes: Int = 0,
    /** 加班分钟（0.5h 步进） */
    @ColumnInfo(name = "ot_minutes", defaultValue = "0") val otMinutes: Int = 0,
    // ---- 当日标准快照（保存时从项目拷贝；记录级可临时改价） ----
    @ColumnInfo(name = "rate_cents") val rateCents: Long,
    @ColumnInfo(name = "base_minutes_snapshot") val baseMinutesSnapshot: Int,
    @ColumnInfo(name = "ot_mode_snapshot") val otModeSnapshot: String,
    @ColumnInfo(name = "ot_base_minutes_snapshot") val otBaseMinutesSnapshot: Int,
    @ColumnInfo(name = "ot_hourly_cents_snapshot") val otHourlyCentsSnapshot: Long,
    // ---- 金额快照（保存时按上述快照算好；编辑时重算） ----
    @ColumnInfo(name = "work_pay_cents", defaultValue = "0") val workPayCents: Long = 0,
    @ColumnInfo(name = "ot_pay_cents", defaultValue = "0") val otPayCents: Long = 0,
    val note: String? = null,
    /** JSON 相对路径数组（照片留证 Phase 2 启用，D10-rev） */
    val photos: String? = null,
    @ColumnInfo(name = "settlement_id") val settlementId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 借支流水（金额记录，非时长；挂项目；purpose 12 类见 12 文档 D5-rev） */
@Entity(
    tableName = "site_advance",
    indices = [Index(value = ["project_id", "date"])],
)
data class SiteAdvanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val date: LocalDate,
    /** >0，分 */
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    /** WAGE|LIVING|LODGING|LODGING_ALLOW|MEALS|MEALS_ALLOW|REWARD|MATERIALS|TRANSPORT|PROJECT_PAYMENT|POCKET|OTHER */
    @ColumnInfo(name = "purpose", defaultValue = "OTHER") val purpose: String = "OTHER",
    val note: String? = null,
    val photos: String? = null,
    @ColumnInfo(name = "settlement_id") val settlementId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 包工/工量记录（Phase 2 启用 UI，表随迁移 2→3 一次建好；量价未知时金额直填） */
@Entity(
    tableName = "site_piece_work",
    indices = [Index(value = ["project_id", "date"])],
)
data class SitePieceWorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val date: LocalDate,
    /** 工作项快照（如"砌墙"；可空串=无名工量） */
    @ColumnInfo(name = "item_name", defaultValue = "") val itemName: String = "",
    /** 单位快照（m²/m³/件…可自定义） */
    val unit: String = "",
    /** 数量 ×1000（0.5m³=500；0=未填量直填金额） */
    @ColumnInfo(name = "quantity_milli", defaultValue = "0") val quantityMilli: Long = 0,
    /** 每单位单价快照（分；0=未填价直填金额） */
    @ColumnInfo(name = "unit_price_cents", defaultValue = "0") val unitPriceCents: Long = 0,
    /** 工钱（量价齐=量×价 HALF_UP；否则直填） */
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val note: String? = null,
    val photos: String? = null,
    @ColumnInfo(name = "settlement_id") val settlementId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 结算单（按项目；快照表：归档后金额以快照为准，撤销不删快照） */
@Entity(
    tableName = "site_settlement",
    indices = [Index(value = ["project_id", "period_end"])],
)
data class SiteSettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "period_start") val periodStart: LocalDate,
    @ColumnInfo(name = "period_end") val periodEnd: LocalDate,
    /** 点工工资（上班+加班，分） */
    @ColumnInfo(name = "work_pay_cents") val workPayCents: Long,
    /** 包工工资（Phase 2，分） */
    @ColumnInfo(name = "piece_pay_cents") val piecePayCents: Long,
    /** 借支合计（正数，分） */
    @ColumnInfo(name = "advance_total_cents") val advanceTotalCents: Long,
    /** 应结 = work + piece − advance（分，可为负） */
    @ColumnInfo(name = "net_cents") val netCents: Long,
    /** 区间出勤天数（快照核对用） */
    @ColumnInfo(name = "att_count") val attCount: Int,
    @ColumnInfo(name = "advance_count") val advanceCount: Int,
    /** 明细快照 JSON（逐日出勤 + 借支笔目） */
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** 部分结算单（本次结算金额，从待结余额扣减；不锁记录、无快照；撤销即删除） */
    @ColumnInfo(name = "is_partial", defaultValue = "0") val isPartial: Boolean = false,
)
