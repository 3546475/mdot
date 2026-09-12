package com.mdot.app.feature.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.SiteDayStatus
import com.mdot.app.domain.model.SitePieceWork
import com.mdot.app.domain.model.WorkSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** 「我的」页数据摘要（自然年累计） */
data class ProfileSummary(
    /** 本年加班总时长（分钟） */
    val yearOtMinutes: Int = 0,
    /** 本年有记录的天数 */
    val yearDays: Int = 0,
    /** 调休余额（分钟，可负） */
    val compBalanceMinutes: Int = 0,
)

/** 「我的」页工地记工摘要（自然年累计；SITE 模式统计卡用） */
data class SiteProfileSummary(
    /** 本年出勤天数（WORK 状态日期去重；同日多项目计 1 天） */
    val yearWorkDays: Int = 0,
    /** 完工（归档）项目数 */
    val completedProjects: Int = 0,
    /** 本年工钱合计（点工+加班+包工，分） */
    val yearPayCents: Long = 0,
)

/** 工地记工摘要聚合（纯函数，供单测）：工天=WORK 日去重；工钱=出勤快照金额+包工金额 */
internal fun computeSiteProfileSummary(
    attendance: List<SiteAttendance>,
    piece: List<SitePieceWork>,
    completedProjects: Int,
): SiteProfileSummary = SiteProfileSummary(
    yearWorkDays = attendance.filter { it.dayStatus == SiteDayStatus.WORK.name }.map { it.date }.distinct().size,
    completedProjects = completedProjects,
    yearPayCents = attendance.sumOf { it.workPayCents + it.otPayCents } + piece.sumOf { it.amountCents },
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    recordRepo: RecordRepository,
    private val settings: SettingsDataSource,
    siteRepo: SiteRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val summary: StateFlow<ProfileSummary> = combine(
        recordRepo.observeRange(
            LocalDate.of(today.year, 1, 1),
            today,
        ),
        recordRepo.observeCompBalance(),
    ) { records, balance ->
        ProfileSummary(
            yearOtMinutes = records.filter { it.type == RecordType.OT }.sumOf { it.durationMinutes },
            yearDays = records.map { it.date }.distinct().size,
            compBalanceMinutes = balance,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileSummary())

    /** 当前工时制度（统计卡按模式切换数据源） */
    val workSystem: StateFlow<WorkSystem> = settings.salaryFlow
        .map { it.workSystem }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkSystem.STANDARD)

    /** 工地记工摘要（仅 SITE 模式展示；出勤+包工自然年累计，项目数为累计完工） */
    val siteSummary: StateFlow<SiteProfileSummary> = combine(
        siteRepo.observeAttendanceAllProjects(LocalDate.of(today.year, 1, 1), today),
        siteRepo.observePieceAllProjects(LocalDate.of(today.year, 1, 1), today),
        siteRepo.observeArchivedProjects(),
    ) { attendance, piece, archived ->
        computeSiteProfileSummary(attendance, piece, archived.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SiteProfileSummary())

    val nickname: StateFlow<String> = settings.nicknameFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsDataSource.DEFAULT_NICKNAME)

    val avatarPath: StateFlow<String?> = settings.avatarPathFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 修改昵称（空则回落默认） */
    fun setNickname(text: String) = viewModelScope.launch {
        settings.setNickname(text.trim().take(24))
    }

    /** 预生成裁剪输出目标文件（时间戳命名，避免路径复用导致的缓存）并返回 uri；确认后写库 */
    private var pendingAvatarPath: String? = null
    fun prepareAvatarTarget(): Uri {
        val dir = File(context.filesDir, "profile").apply { mkdirs() }
        val target = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        pendingAvatarPath = target.absolutePath
        // CanHub 保存仅允许 content://，用 FileProvider 暴露
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", target,
        )
    }

    /** 裁剪库写盘成功后：清理旧头像并登记新路径 */
    fun confirmAvatarSaved() {
        val path = pendingAvatarPath ?: return
        pendingAvatarPath = null
        runCatching {
            val target = File(path)
            File(target.parentFile ?: return, "").let { dir ->
                dir.listFiles { f -> f.name.startsWith("avatar_") && f.name != target.name }
                    ?.forEach { runCatching { it.delete() } }
            }
        }
        viewModelScope.launch { settings.setAvatarPath(path) }
    }

    /** 恢复默认头像（删除自定义副本） */
    fun resetAvatar() = viewModelScope.launch {
        runCatching {
            File(context.filesDir, "profile").listFiles()?.forEach { runCatching { it.delete() } }
        }
        settings.setAvatarPath(null)
    }
}
