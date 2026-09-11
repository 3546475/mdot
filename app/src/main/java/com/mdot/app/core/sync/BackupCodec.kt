package com.mdot.app.core.sync

import androidx.room.withTransaction
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.database.CompAdjustmentEntity
import com.mdot.app.core.database.DailyRecordEntity
import com.mdot.app.core.database.ShiftEntity
import com.mdot.app.core.database.SiteAdvanceEntity
import com.mdot.app.core.database.SiteAttendanceEntity
import com.mdot.app.core.database.SitePieceWorkEntity
import com.mdot.app.core.database.SiteProjectEntity
import com.mdot.app.core.database.SiteSettlementEntity
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.HomeCardsConfig
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份包编解码（06 文档 §2）：
 * ZIP = manifest.json（摘要，另存 current.json 供恢复前轻量预览）+ data.json（全量数据）。
 * 明文 ZIP（用户已确认）；格式版本化，未知字段解析时忽略（向后兼容）。
 */
@Singleton
class BackupCodec @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsDataSource,
) {

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    /** 解析 current.json（恢复前轻量预览用） */
    fun parseManifest(text: String): ManifestDto =
        json.decodeFromString(ManifestDto.serializer(), text)

    // ---- 导出：库 → ZIP ----

    suspend fun export(manifestBase: ManifestBase): BackupPackage {
        val data = buildDataJson()
        // 条件 bump（12 文档 §3.4）：含工地数据的包标 v2——旧版 App 恢复会静默丢 site 数据，
        // 靠 manifest.formatVersion 拒绝；纯旧制度包保持 v1，旧版可继续恢复
        val formatVersion = if (data.hasSiteData()) FORMAT_VERSION_V2 else FORMAT_VERSION
        val manifest = ManifestDto(
            formatVersion = formatVersion,
            schemaVersion = SCHEMA_VERSION,
            createdAt = OffsetDateTime.now().format(DateTimeFormatter_ISO_OFFSET),
            recordCount = data.records.size,
            appVersionName = manifestBase.appVersionName,
            appVersionCode = manifestBase.appVersionCode,
            deviceModel = manifestBase.deviceModel,
        )
        val zip = zipOf(
            MANIFEST_ENTRY to json.encodeToString(ManifestDto.serializer(), manifest),
            DATA_ENTRY to json.encodeToString(BackupFile.serializer(), data),
        )
        return BackupPackage(manifest, data, zip)
    }

    private suspend fun buildDataJson(): BackupFile = BackupFile(
        schemaVersion = SCHEMA_VERSION,
        exportedAt = OffsetDateTime.now().format(DateTimeFormatter_ISO_OFFSET),
        siteProjects = db.siteProjectDao().getAll().map {
            SiteProjectDto(
                id = it.id, name = it.name, sort = it.sort, archived = it.archived,
                baseMinutes = it.baseMinutes, dailyRateCents = it.dailyRateCents,
                otMode = it.otMode, otBaseMinutes = it.otBaseMinutes, otHourlyCents = it.otHourlyCents,
                note = it.note, createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
        siteAttendance = db.siteAttendanceDao().getAll().map {
            SiteAttendanceDto(
                id = it.id, projectId = it.projectId, date = it.date.toString(),
                dayStatus = it.dayStatus, halfOfDay = it.halfOfDay,
                workMinutes = it.workMinutes, otMinutes = it.otMinutes,
                rateCents = it.rateCents, baseMinutes = it.baseMinutesSnapshot,
                otMode = it.otModeSnapshot, otBaseMinutes = it.otBaseMinutesSnapshot,
                otHourlyCents = it.otHourlyCentsSnapshot,
                workPayCents = it.workPayCents, otPayCents = it.otPayCents,
                note = it.note, photos = it.photos, settlementId = it.settlementId,
                createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
        siteAdvances = db.siteAdvanceDao().getAll().map {
            SiteAdvanceDto(
                id = it.id, projectId = it.projectId, date = it.date.toString(),
                amountCents = it.amountCents, purpose = it.purpose,
                note = it.note, photos = it.photos, settlementId = it.settlementId,
                createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
        sitePieceWorks = db.sitePieceWorkDao().getAll().map {
            SitePieceWorkDto(
                id = it.id, projectId = it.projectId, date = it.date.toString(),
                itemName = it.itemName, unit = it.unit, quantityMilli = it.quantityMilli,
                unitPriceCents = it.unitPriceCents, amountCents = it.amountCents,
                note = it.note, photos = it.photos, settlementId = it.settlementId,
                createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
        siteSettlements = db.siteSettlementDao().getAll().map {
            SiteSettlementDto(
                id = it.id, projectId = it.projectId,
                periodStart = it.periodStart.toString(), periodEnd = it.periodEnd.toString(),
                workPayCents = it.workPayCents, piecePayCents = it.piecePayCents,
                advanceTotalCents = it.advanceTotalCents, netCents = it.netCents,
                attCount = it.attCount, advanceCount = it.advanceCount,
                snapshotJson = it.snapshotJson, note = it.note, createdAt = it.createdAt,
                isPartial = it.isPartial,
            )
        },
        shifts = db.shiftDao().getAll().map {
            ShiftDto(it.id, it.name, it.sort, it.builtin, it.hidden, it.rest)
        },
        records = db.recordDao().getAll().map {
            RecordDto(
                date = it.date.toString(),
                type = it.type,
                shiftId = it.shiftId,
                shiftName = it.shiftName,
                durationMinutes = it.durationMinutes,
                tier = it.tier,
                tierSource = it.tierSource,
                toCompMinutes = it.toCompMinutes,
                leaveType = it.leaveType,
                note = it.note,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                workSystem = it.workSystem,
            )
        },
        compAdjustments = db.compAdjustmentDao().getAll().map {
            AdjustmentDto(it.date.toString(), it.deltaMinutes, it.note, it.createdAt)
        },
        settings = SettingsDto(
            salary = settings.salaryFlow.first(),
            cycleAnchorDay = settings.cycleAnchorDayFlow.first(),
            workdays = settings.workdaysFlow.first().map { it.name },
            appearance = settings.appearanceFlow.first(),
            bottomBar = settings.bottomBarFlow.first(),
            homeCards = settings.homeCardsFlow.first(),
        ),
    )

    // ---- 导入：ZIP → 库（事务；班次名快照兜底） ----

    /** 解包并校验结构（不动库），返回 data.json 与 manifest 供确认/预览 */
    fun unzip(bytes: ByteArray): ParsedBackup {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                entries[entry.name] = zin.readBytes().decodeToString()
                entry = zin.nextEntry
            }
        }
        val manifestText = entries[MANIFEST_ENTRY]
            ?: throw IllegalArgumentException("备份包缺少 manifest.json")
        val dataText = entries[DATA_ENTRY]
            ?: throw IllegalArgumentException("备份包缺少 data.json")
        val manifest = json.decodeFromString(ManifestDto.serializer(), manifestText)
        // 上限 = 当前支持的最高格式（v2 含工地表；0.6.3 E2E 修复：
        // 原写死 FORMAT_VERSION(1) 导致本应用自己导出的 v2 包恢复必被拒）
        if (manifest.formatVersion > FORMAT_VERSION_V2) {
            throw IllegalArgumentException("备份包格式过新（v${manifest.formatVersion}），请先升级 App")
        }
        val data = json.decodeFromString(BackupFile.serializer(), dataText)
        validateBackupWorkSystems(data)
        return ParsedBackup(manifest, data)
    }

    /** 事务内清空并重灌（06 文档 §5.2 第 4 步）；设置覆盖（凭据类键不在包内，天然不动） */
    suspend fun import(data: BackupFile) {
        db.withTransaction {
            db.recordDao().deleteAll()
            db.compAdjustmentDao().deleteAll()
            db.shiftDao().deleteAll()
            // 工地数据整包替换（12 文档 §3.4）：结算单先行（记录引用其 id）
            db.siteProjectDao().deleteAllForImport()
            db.siteAttendanceDao().deleteAllForImport()
            db.siteAdvanceDao().deleteAllForImport()
            db.sitePieceWorkDao().deleteAllForImport()
            db.siteSettlementDao().deleteAllForImport()

            db.siteProjectDao().insertAll(
                data.siteProjects.map { dto ->
                    SiteProjectEntity(
                        id = dto.id, name = dto.name, sort = dto.sort, archived = dto.archived,
                        baseMinutes = dto.baseMinutes, dailyRateCents = dto.dailyRateCents,
                        otMode = dto.otMode, otBaseMinutes = dto.otBaseMinutes,
                        otHourlyCents = dto.otHourlyCents, note = dto.note,
                        createdAt = dto.createdAt, updatedAt = dto.updatedAt,
                    )
                }
            )
            db.siteAttendanceDao().insertAll(
                data.siteAttendance.map { dto ->
                    SiteAttendanceEntity(
                        id = dto.id, projectId = dto.projectId,
                        date = java.time.LocalDate.parse(dto.date),
                        dayStatus = dto.dayStatus, halfOfDay = dto.halfOfDay,
                        workMinutes = dto.workMinutes, otMinutes = dto.otMinutes,
                        rateCents = dto.rateCents, baseMinutesSnapshot = dto.baseMinutes,
                        otModeSnapshot = dto.otMode, otBaseMinutesSnapshot = dto.otBaseMinutes,
                        otHourlyCentsSnapshot = dto.otHourlyCents,
                        workPayCents = dto.workPayCents, otPayCents = dto.otPayCents,
                        note = dto.note, photos = dto.photos, settlementId = dto.settlementId,
                        createdAt = dto.createdAt, updatedAt = dto.updatedAt,
                    )
                }
            )
            db.siteAdvanceDao().insertAll(
                data.siteAdvances.map { dto ->
                    SiteAdvanceEntity(
                        id = dto.id, projectId = dto.projectId,
                        date = java.time.LocalDate.parse(dto.date),
                        amountCents = dto.amountCents, purpose = dto.purpose,
                        note = dto.note, photos = dto.photos, settlementId = dto.settlementId,
                        createdAt = dto.createdAt, updatedAt = dto.updatedAt,
                    )
                }
            )
            db.sitePieceWorkDao().insertAll(
                data.sitePieceWorks.map { dto ->
                    SitePieceWorkEntity(
                        id = dto.id, projectId = dto.projectId,
                        date = java.time.LocalDate.parse(dto.date),
                        itemName = dto.itemName, unit = dto.unit,
                        quantityMilli = dto.quantityMilli, unitPriceCents = dto.unitPriceCents,
                        amountCents = dto.amountCents,
                        note = dto.note, photos = dto.photos, settlementId = dto.settlementId,
                        createdAt = dto.createdAt, updatedAt = dto.updatedAt,
                    )
                }
            )
            db.siteSettlementDao().insertAll(
                data.siteSettlements.map { dto ->
                    SiteSettlementEntity(
                        id = dto.id, projectId = dto.projectId,
                        periodStart = java.time.LocalDate.parse(dto.periodStart),
                        periodEnd = java.time.LocalDate.parse(dto.periodEnd),
                        workPayCents = dto.workPayCents, piecePayCents = dto.piecePayCents,
                        advanceTotalCents = dto.advanceTotalCents, netCents = dto.netCents,
                        attCount = dto.attCount, advanceCount = dto.advanceCount,
                        snapshotJson = dto.snapshotJson, note = dto.note, createdAt = dto.createdAt,
                        isPartial = dto.isPartial,
                    )
                }
            )

            // 班次按原 id 重建；无 id 的插为新增
            db.shiftDao().insertAll(
                data.shifts.map { dto ->
                    ShiftEntity(
                        id = dto.id ?: 0,
                        name = dto.name,
                        sort = dto.sort,
                        builtin = dto.builtin,
                        hidden = dto.hidden,
                        rest = dto.rest,
                    )
                }
            )
            // 导入后班次名 → id 映射（记录 shiftId 兜底回填用）
            val shiftIdByName = db.shiftDao().getAll().associateBy({ it.name }, { it.id })

            db.recordDao().insertAll(
                data.records.map { dto ->
                    val resolvedShiftId = dto.shiftId
                        ?: dto.shiftName?.let { shiftIdByName[it] }
                    DailyRecordEntity(
                        id = 0,
                        date = java.time.LocalDate.parse(dto.date),
                        type = dto.type,
                        workSystem = dto.workSystem,
                        shiftId = resolvedShiftId,
                        shiftName = dto.shiftName,
                        durationMinutes = dto.durationMinutes,
                        tier = dto.tier,
                        tierSource = dto.tierSource,
                        toCompMinutes = dto.toCompMinutes,
                        leaveType = dto.leaveType,
                        note = dto.note,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt,
                    )
                }
            )
            db.compAdjustmentDao().insertAll(
                data.compAdjustments.map { dto ->
                    CompAdjustmentEntity(
                        date = java.time.LocalDate.parse(dto.date),
                        deltaMinutes = dto.deltaMinutes,
                        note = dto.note,
                        createdAt = dto.createdAt,
                    )
                }
            )
        }
        // 设置覆盖（事务外，DataStore）
        val st = data.settings
        settings.setSalary(st.salary)
        settings.setCycleAnchorDay(st.cycleAnchorDay.coerceIn(1, 31))
        settings.setWorkdays(
            st.workdays.mapNotNull { runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
                .ifEmpty {
                    setOf(
                        java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
                        java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY,
                        java.time.DayOfWeek.FRIDAY,
                    )
                }
        )
        settings.setAppearance(st.appearance)
        settings.setBottomBar(st.bottomBar)
        // 首页卡片：旧备份无此键（null）→ 保留本机配置不覆盖
        st.homeCards?.let { settings.setHomeCards(it) }
        settings.touch()
    }

    // ---- ZIP 工具 ----

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, text) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    companion object {
        const val FORMAT_VERSION = 1
        /** v2 = 含工地 site_* 数据（12 文档 §3.4）；旧版恢复 v2 包会被 unzip 拒绝防丢钱账 */
        const val FORMAT_VERSION_V2 = 2
        const val SCHEMA_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATA_ENTRY = "data.json"
        private val DateTimeFormatter_ISO_OFFSET = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}

/** 生成 manifest 所需的宿主信息 */
data class ManifestBase(
    val appVersionName: String,
    val appVersionCode: Int,
    val deviceModel: String,
)

data class BackupPackage(
    val manifest: ManifestDto,
    val data: BackupFile,
    val zipBytes: ByteArray,
)

data class ParsedBackup(
    val manifest: ManifestDto,
    val data: BackupFile,
)

/**
 * 恢复防御（10 文档 §3.3，唯一跨版本风险点）：备份包 records 的 workSystem 是 String 直通字段，
 * 未知值若静默入库，读取时 WorkSystem.valueOf 会崩——这里在解包阶段显式拒绝并给出升级提示。
 * records 为空或全部属于已知制度（含 COMPREHENSIVE）即放行。
 */
internal fun validateBackupWorkSystems(data: BackupFile) {
    val known = WorkSystem.entries.mapTo(HashSet()) { it.name }
    val unknown = data.records.map { it.workSystem }
        .filterNot { it in known }
        .distinct()
    if (unknown.isNotEmpty()) {
        throw IllegalArgumentException(
            "备份包含当前版本不支持的工时制度数据（${unknown.joinToString("、")}），请升级 App 到最新版本后再恢复"
        )
    }
}

// ---- JSON DTO（字段名与 06 文档 §2.3 Schema 一致） ----

@Serializable
data class ManifestDto(
    val formatVersion: Int = 1,
    val schemaVersion: Int = 1,
    val createdAt: String = "",
    val recordCount: Int = 0,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val deviceModel: String = "",
)

@Serializable
data class BackupFile(
    val schemaVersion: Int = 1,
    val exportedAt: String = "",
    val shifts: List<ShiftDto> = emptyList(),
    val records: List<RecordDto> = emptyList(),
    val compAdjustments: List<AdjustmentDto> = emptyList(),
    val settings: SettingsDto = SettingsDto(),
    // ---- 工地记工（v2 格式，12 文档 §3.4；旧包缺省空列表天然兼容） ----
    val siteProjects: List<SiteProjectDto> = emptyList(),
    val siteAttendance: List<SiteAttendanceDto> = emptyList(),
    val siteAdvances: List<SiteAdvanceDto> = emptyList(),
    val sitePieceWorks: List<SitePieceWorkDto> = emptyList(),
    val siteSettlements: List<SiteSettlementDto> = emptyList(),
) {
    /** 是否携带工地数据（决定导出包 formatVersion 条件 bump） */
    fun hasSiteData(): Boolean =
        siteProjects.isNotEmpty() || siteAttendance.isNotEmpty() || siteAdvances.isNotEmpty() ||
            sitePieceWorks.isNotEmpty() || siteSettlements.isNotEmpty()
}

@Serializable
data class SiteProjectDto(
    val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    val archived: Boolean = false,
    val baseMinutes: Int = 480,
    val dailyRateCents: Long = 0,
    val otMode: String = "BY_DAY",
    val otBaseMinutes: Int = 360,
    val otHourlyCents: Long = 0,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SiteAttendanceDto(
    val id: Long = 0,
    val projectId: Long,
    val date: String,
    val dayStatus: String,
    val halfOfDay: String? = null,
    val workMinutes: Int = 0,
    val otMinutes: Int = 0,
    val rateCents: Long,
    val baseMinutes: Int,
    val otMode: String,
    val otBaseMinutes: Int,
    val otHourlyCents: Long,
    val workPayCents: Long = 0,
    val otPayCents: Long = 0,
    val note: String? = null,
    val photos: String? = null,
    val settlementId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SiteAdvanceDto(
    val id: Long = 0,
    val projectId: Long,
    val date: String,
    val amountCents: Long,
    val purpose: String = "OTHER",
    val note: String? = null,
    val photos: String? = null,
    val settlementId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SitePieceWorkDto(
    val id: Long = 0,
    val projectId: Long,
    val date: String,
    val itemName: String = "",
    val unit: String = "",
    val quantityMilli: Long = 0,
    val unitPriceCents: Long = 0,
    val amountCents: Long,
    val note: String? = null,
    val photos: String? = null,
    val settlementId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SiteSettlementDto(
    val id: Long = 0,
    val projectId: Long,
    val periodStart: String,
    val periodEnd: String,
    val workPayCents: Long,
    val piecePayCents: Long,
    val advanceTotalCents: Long,
    val netCents: Long,
    val attCount: Int,
    val advanceCount: Int,
    val snapshotJson: String,
    val note: String? = null,
    val createdAt: Long,
    val isPartial: Boolean = false,
)

@Serializable
data class ShiftDto(
    val id: Long? = null,
    val name: String,
    val sort: Int = 0,
    val builtin: Boolean = false,
    val hidden: Boolean = false,
    val rest: Boolean = false,
)

@Serializable
data class RecordDto(
    val date: String,
    val type: String,
    val shiftId: Long? = null,
    val shiftName: String? = null,
    val durationMinutes: Int,
    val tier: String? = null,
    val tierSource: String? = null,
    val toCompMinutes: Int = 0,
    val leaveType: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** 工时制度（STANDARD/HOURLY），旧备份缺省为 STANDARD */
    val workSystem: String = "STANDARD",
)

@Serializable
data class AdjustmentDto(
    val date: String,
    val deltaMinutes: Int,
    val note: String? = null,
    val createdAt: Long,
)

@Serializable
data class SettingsDto(
    val salary: SalaryConfig = SalaryConfig(),
    val cycleAnchorDay: Int = 1,
    val workdays: List<String> = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
    val appearance: AppearanceConfig = AppearanceConfig(),
    val bottomBar: BottomBarConfig = BottomBarConfig(),
    /** 首页卡片配置（v0.6.0）；旧备份缺省 null=未配置，恢复时保留本机 */
    val homeCards: HomeCardsConfig? = null,
)
