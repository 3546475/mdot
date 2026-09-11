package com.mdot.app.feature.site

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.onFailure
import com.mdot.app.core.util.onSuccess
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.SiteDayStatus
import com.mdot.app.domain.model.SiteProject
import com.mdot.app.domain.model.SiteOtMode
import com.mdot.app.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 工地记工页 UiState/VM（12 文档 F-S3/F-S5；页面版式对齐竞品截图：
 * 记账|记借支/结算 顶 Tab + 项目/日期行 + 点工/包工/工量子 Tab + 上班/加班 chips + 工钱行 + 备注/照片）。
 */
data class SiteRecordUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    /** 日期多选（D11）：非空时保存逐日批量建行 */
    val extraDates: List<LocalDate> = emptyList(),
    val projectId: Long = 0,
    val projectName: String = "",
    /** 上班：WORK（workMinutes>0）或 REST */
    val dayStatus: SiteDayStatus = SiteDayStatus.WORK,
    val halfOfDay: String? = null,
    val workMinutes: Int = 0,
    val otMinutes: Int = 0,
    /** 上班选中瓦片（互斥）：0=1个工 1=选工天 2=选小时（休息由 dayStatus 表达） */
    val workTile: Int = 0,
    /** 加班选中瓦片（互斥）：0=无加班 1=选工天 2=选小时 */
    val otTile: Int = 0,
    /** 记录级改价（元文本；空=用项目价） */
    val rateYuanText: String = "",
    val project: SiteProject? = null,
    /** 借支 */
    val advanceYuanText: String = "",
    val purpose: AdvancePurpose = AdvancePurpose.OTHER,
    val advanceNote: String = "",
    /** 结算 tab：本次结算金额（元文本） */
    val settleAmountText: String = "",
    /** 包工/工量（Phase 2）：量价齐自动算钱，否则直填金额 */
    val pieceName: String = "",
    val pieceQuantityText: String = "",
    val pieceUnit: String = "平方米",
    val piecePriceText: String = "",
    val pieceAmountText: String = "",
    val note: String = "",
    /** 照片本地路径（filesDir/site_photos 目录，备份包不含，D10） */
    val photos: List<String> = emptyList(),
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** 生效日价（分）：记录级改价优先 */
    val effectiveRateCents: Long
        get() = Money.parseYuanToCents(rateYuanText) ?: project?.dailyRateCents ?: 0

    fun standard(): SitePayCalculator.DayStandard? {
        val p = project ?: return null
        return SitePayCalculator.DayStandard(
            rateCents = effectiveRateCents, baseMinutes = p.baseMinutes,
            otMode = p.otMode, otBaseMinutes = p.otBaseMinutes, otHourlyCents = p.otHourlyCents,
        )
    }

    fun previewCents(): Long {
        val std = standard() ?: return 0
        return SitePayCalculator.workPayCents(std, workMinutes) + SitePayCalculator.otPayCents(std, otMinutes)
    }

    /** 数量 ×1000（毫单位，0.001 步进）；未填=0 */
    val pieceQuantityMilli: Long
        get() = ((pieceQuantityText.toDoubleOrNull() ?: 0.0) * 1000).toLong()

    /** 包工工钱（分）：手填工钱优先，量价齐自动算钱，否则 0 */
    fun piecePreviewCents(): Long = SitePayCalculator.pieceAmountCents(
        quantityMilli = pieceQuantityMilli,
        unitPriceCents = Money.parseYuanToCents(piecePriceText) ?: 0,
        directCents = Money.parseYuanToCents(pieceAmountText) ?: 0,
    )
}

@HiltViewModel
class SiteRecordViewModel @Inject constructor(
    private val siteRepo: SiteRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(SiteRecordUiState())
    val state: StateFlow<SiteRecordUiState> = _state.asStateFlow()

    /** 可选项目（当前项目选择器） */
    val activeProjects: StateFlow<List<SiteProject>> = siteRepo.observeActiveProjects()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    init {
        // 项目切换联动：记工页 → 项目管理页「选择模式」切换当前项目后返回，按新项目刷新上下文
        // （open() 在小页面重建时也会重读 currentProjectId，这里保证 VM 存活时同样生效）
        viewModelScope.launch {
            settings.salaryFlow
                .map { it.siteCurrentProjectId }
                .distinctUntilChanged()
                .collect { pid -> if (pid != 0L) applyCurrentProject(pid) }
        }
    }

    /** 当前项目 id → 项目上下文；未加载完成或项目未变化时不动作（避免与 open() 竞态） */
    private suspend fun applyCurrentProject(id: Long) {
        val s = _state.value
        if (s.loading || id == s.projectId) return
        val p = siteRepo.getProject(id) ?: return
        val prevBase = s.project?.baseMinutes
        _state.update {
            it.copy(
                projectId = id, projectName = p.name, project = p,
                workMinutes = if (it.dayStatus == SiteDayStatus.WORK)
                    (it.workMinutes.takeIf { m -> m > 0 && m != prevBase } ?: p.baseMinutes)
                else it.workMinutes,
                error = null,
            )
        }
    }

    fun open(date: LocalDate) {
        viewModelScope.launch {
            val pid = siteRepo.currentProjectId()
            val project = siteRepo.getProject(pid)
            val existing = siteRepo.observeAttendance(pid, date, date).first().firstOrNull()
            val baseMinutes = project?.baseMinutes ?: 480
            val wm = existing?.workMinutes ?: baseMinutes
            _state.update {
                SiteRecordUiState(
                    loading = false, date = date, projectId = pid,
                    projectName = project?.name.orEmpty(), project = project,
                    // 编辑回显：已有出勤载入，否则默认 1 个工；按分钟数推断选中瓦片
                    dayStatus = existing?.let { SiteDayStatus.valueOf(it.dayStatus) } ?: SiteDayStatus.WORK,
                    workTile = when {
                        existing?.halfOfDay != null -> 2
                        wm == baseMinutes -> 0
                        wm % baseMinutes == 0 -> 1
                        else -> 2
                    },
                    halfOfDay = existing?.halfOfDay,
                    workMinutes = wm,
                    otMinutes = existing?.otMinutes ?: 0,
                    otTile = run {
                        val ot = existing?.otMinutes ?: 0
                        val otBase = project?.otBaseMinutes ?: 360
                        when {
                            ot <= 0 -> 0
                            ot % otBase == 0 -> 1
                            else -> 2
                        }
                    },
                    rateYuanText = existing?.let {
                        if (it.rateCents != (project?.dailyRateCents ?: 0L))
                            com.mdot.app.domain.util.Money.yuanText(it.rateCents).replace(",", "")
                        else ""
                    } ?: "",
                    note = existing?.note ?: "",
                    photos = existing?.photos.parsePhotos(),
                )
            }
        }
    }

    fun onDayStatus(v: SiteDayStatus) = _state.update {
        val backToWork = v == SiteDayStatus.WORK && it.workMinutes == 0 && it.otMinutes == 0
        it.copy(
            dayStatus = v, error = null,
            // 休息：上班/加班清零 → 工钱预览归 0；回上班：恢复默认 1 个工
            workMinutes = when {
                v == SiteDayStatus.REST -> 0
                backToWork -> it.project?.baseMinutes ?: 480
                else -> it.workMinutes
            },
            otMinutes = if (v == SiteDayStatus.REST) 0 else it.otMinutes,
            // 休息时加班选中态一并复位（上班选中态保留，回上班后仍可看到此前选择）
            otTile = if (v == SiteDayStatus.REST) 0 else it.otTile,
            workTile = if (backToWork) 0 else it.workTile,
        )
    }
    /** 工钱弹窗快速改点工标准：写回项目并刷新本地标准（工钱预览立即重算） */
    fun applyStandard(
        baseHours: String,
        rateYuan: String,
        mode: SiteOtMode,
        otBaseHours: String,
        otHourlyYuan: String,
    ) {
        val p = _state.value.project ?: return
        val baseMinutes = ((baseHours.toDoubleOrNull() ?: 8.0) * 60).toInt().coerceIn(60, 1440)
        val rate = Money.parseYuanToCents(rateYuan) ?: 0
        val otBaseMinutes = ((otBaseHours.toDoubleOrNull() ?: 6.0) * 60).toInt().coerceIn(60, 1440)
        val otHourly = Money.parseYuanToCents(otHourlyYuan) ?: 0
        val updated = p.copy(
            baseMinutes = baseMinutes,
            dailyRateCents = rate,
            otMode = mode.name,
            otBaseMinutes = otBaseMinutes,
            otHourlyCents = otHourly,
        )
        _state.update { it.copy(project = updated, error = null) }
        viewModelScope.launch { siteRepo.updateProject(updated) }
    }

    fun onHalfOfDay(v: String?) = _state.update { it.copy(halfOfDay = v, error = null) }
    fun onWorkMinutes(v: Int) = _state.update {
        it.copy(workMinutes = v.coerceIn(0, 1440), workTile = 0, dayStatus = SiteDayStatus.WORK, error = null)
    }
    /** 选工天弹窗确认：互斥选中「选工天」瓦片并回显工数 */
    fun pickWorkDays(minutes: Int) = _state.update {
        it.copy(workMinutes = minutes.coerceIn(0, 1440), workTile = 1, dayStatus = SiteDayStatus.WORK, error = null)
    }
    /** 选小时弹窗确认：互斥选中「选小时」瓦片并回显小时数 */
    fun pickWorkHours(minutes: Int) = _state.update {
        it.copy(workMinutes = minutes.coerceIn(0, 1440), workTile = 2, dayStatus = SiteDayStatus.WORK, error = null)
    }
    fun onOtMinutes(v: Int) = _state.update { it.copy(otMinutes = v.coerceIn(0, 1440), otTile = 0, error = null) }
    /** 加班选工天弹窗确认：互斥选中「选工天」瓦片并回显工数 */
    fun pickOtDays(minutes: Int) = _state.update {
        it.copy(otMinutes = minutes.coerceIn(0, 1440), otTile = 1, error = null)
    }
    /** 加班选小时弹窗确认：互斥选中「选小时」瓦片并回显小时数 */
    fun pickOtHours(minutes: Int) = _state.update {
        it.copy(otMinutes = minutes.coerceIn(0, 1440), otTile = 2, error = null)
    }
    fun onRate(v: String) = _state.update {
        it.copy(rateYuanText = v.filter { c -> c.isDigit() || c == '.' }, error = null)
    }
    fun onNote(v: String) = _state.update { it.copy(note = v.take(100)) }
    fun onDate(v: LocalDate) = _state.update { it.copy(date = v, error = null) }
    fun onToggleExtraDate(v: LocalDate) = _state.update {
        it.copy(extraDates = if (v in it.extraDates) it.extraDates - v else (it.extraDates + v).sorted())
    }
    fun clearExtraDates() = _state.update { it.copy(extraDates = emptyList()) }
    fun addPhotos(paths: List<String>) = _state.update { it.copy(photos = (it.photos + paths).take(9)) }
    fun removePhoto(path: String) = _state.update { it.copy(photos = it.photos - path) }

    fun onAdvanceAmount(v: String) = _state.update {
        it.copy(advanceYuanText = v.filter { c -> c.isDigit() || c == '.' }, error = null)
    }
    fun onPurpose(v: AdvancePurpose) = _state.update { it.copy(purpose = v, error = null) }
    fun onAdvanceNote(v: String) = _state.update { it.copy(advanceNote = v.take(100)) }

    fun onSettleAmount(v: String) = _state.update {
        it.copy(settleAmountText = v.filter { c -> c.isDigit() || c == '.' }, error = null)
    }

    fun onPieceName(v: String) = _state.update { it.copy(pieceName = v.take(30), error = null) }
    fun onPieceQuantity(v: String) = _state.update {
        it.copy(pieceQuantityText = v.filter { c -> c.isDigit() || c == '.' }, error = null)
    }
    fun onPieceUnit(v: String) = _state.update { it.copy(pieceUnit = v) }
    fun onPiecePrice(v: String) = _state.update {
        it.copy(piecePriceText = v.filter { c -> c.isDigit() || c == '.' }, error = null)
    }
    fun onPieceAmount(v: String) = _state.update {
        it.copy(pieceAmountText = v.filter { c -> c.isDigit() || c == '.' }, error = null)
    }

    /** 保存包工/工量；keepOpen=保存并再记一笔（保留工作项与单位，清量价金额） */
    fun savePieceWork(keepOpen: Boolean, onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            siteRepo.savePieceWork(
                projectId = s.projectId, date = s.date,
                itemName = s.pieceName, unit = s.pieceUnit,
                quantityMilli = s.pieceQuantityMilli,
                unitPriceCents = Money.parseYuanToCents(s.piecePriceText) ?: 0,
                directAmountCents = Money.parseYuanToCents(s.pieceAmountText) ?: 0,
                note = s.note.ifBlank { null },
                photos = s.photos.toPhotosJson(),
            ).onSuccess {
                if (keepOpen) {
                    _state.update {
                        it.copy(
                            saved = false, pieceQuantityText = "", piecePriceText = "", pieceAmountText = "",
                            note = "", photos = emptyList(), error = null,
                        )
                    }
                } else {
                    _state.update { it.copy(saved = true) }
                    onDone()
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toSiteErrorText()) }
            }
        }
    }

    /** 保存出工（含多选日期批量建行）；keepOpen=保存并再记一笔 */
    fun saveAttendance(keepOpen: Boolean, onDone: () -> Unit) {
        val s = _state.value
        if (s.dayStatus == SiteDayStatus.WORK && s.workMinutes == 0 && s.otMinutes == 0) {
            _state.update { it.copy(error = "出勤或加班至少填一项") }
            return
        }
        val dates = (listOf(s.date) + s.extraDates).distinct().sorted()
        viewModelScope.launch {
            val p = s.project ?: return@launch
            var failure: com.mdot.app.core.util.AppError? = null
            for (d in dates) {
                val r = siteRepo.saveAttendance(
                    com.mdot.app.domain.model.SiteAttendance(
                        projectId = s.projectId, date = d.toString(),
                        dayStatus = s.dayStatus.name, halfOfDay = s.halfOfDay,
                        workMinutes = s.workMinutes, otMinutes = s.otMinutes,
                        rateCents = s.effectiveRateCents, baseMinutes = p.baseMinutes,
                        otMode = p.otMode, otBaseMinutes = p.otBaseMinutes, otHourlyCents = p.otHourlyCents,
                        note = s.note.ifBlank { null },
                        photos = s.photos.toPhotosJson(),
                        createdAt = 0, updatedAt = 0,
                    )
                )
                if (r is AppResult.Failure) { failure = r.error; break }
            }
            failure?.let { e ->
                _state.update { it.copy(error = e.toSiteErrorText()) }
                return@launch
            }
            if (keepOpen) {
                // 再记一笔：重置表单，保留项目上下文
                _state.update {
                    it.copy(
                        saved = false, date = LocalDate.now(), extraDates = emptyList(),
                        dayStatus = SiteDayStatus.WORK, halfOfDay = null, workTile = 0, otTile = 0,
                        workMinutes = p.baseMinutes, otMinutes = 0,
                        rateYuanText = "", note = "", photos = emptyList(), error = null,
                    )
                }
            } else {
                _state.update { it.copy(saved = true) }
                onDone()
            }
        }
    }

    /** 结算 tab 保存（本次结算金额）：像借支一样从待结余额中拿走一笔；keepOpen=保存并再记一笔 */
    fun saveSettlementAmount(keepOpen: Boolean, onDone: () -> Unit) {
        val s = _state.value
        val cents = Money.parseYuanToCents(s.settleAmountText) ?: 0
        if (cents <= 0) {
            _state.update { it.copy(error = "结算金额需大于 0") }
            return
        }
        viewModelScope.launch {
            siteRepo.settlePartial(s.projectId, cents)
                .onSuccess {
                    if (keepOpen) {
                        _state.update { it.copy(settleAmountText = "", saved = false, error = null) }
                    } else {
                        _state.update { it.copy(saved = true) }
                        onDone()
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toSiteErrorText()) }
                }
        }
    }

    fun saveAdvance(keepOpen: Boolean, onDone: () -> Unit) {
        val s = _state.value
        val cents = Money.parseYuanToCents(s.advanceYuanText) ?: 0
        if (cents <= 0) {
            _state.update { it.copy(error = "借支金额需大于 0") }
            return
        }
        viewModelScope.launch {
            siteRepo.saveAdvance(s.projectId, s.date, cents, s.purpose, s.advanceNote.ifBlank { null }, s.photos.toPhotosJson())
                .onSuccess {
                    if (keepOpen) {
                        _state.update {
                            it.copy(advanceYuanText = "", advanceNote = "", photos = emptyList(), saved = false)
                        }
                    } else {
                        _state.update { it.copy(saved = true) }
                        onDone()
                    }
                }.onFailure { e ->
                    _state.update { it.copy(error = e.toSiteErrorText()) }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

/** 照片路径列表 → 存储串（SOH 分隔，避免序列化依赖） */
internal fun List<String>.toPhotosJson(): String? =
    if (isEmpty()) null else joinToString("\u0001")

internal fun com.mdot.app.core.util.AppError.toSiteErrorText(): String = when (this) {
    is com.mdot.app.core.util.AppError.InvalidMessage -> message
    com.mdot.app.core.util.AppError.InvalidDuration -> "时长需在 0–24 小时之间"
    else -> "操作失败，请重试"
}

internal fun String?.parsePhotos(): List<String> =
    this?.takeIf { it.isNotBlank() }?.split('\u0001') ?: emptyList()
