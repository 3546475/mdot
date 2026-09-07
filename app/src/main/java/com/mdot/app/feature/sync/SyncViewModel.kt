package com.mdot.app.feature.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.BuildConfig
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.sync.BackupCodec
import com.mdot.app.core.sync.ManifestBase
import com.mdot.app.core.sync.ProviderKind
import com.mdot.app.core.sync.RemoteSummary
import com.mdot.app.core.sync.S3Creds
import com.mdot.app.core.sync.StorageSources
import com.mdot.app.core.sync.SyncEngine
import com.mdot.app.core.sync.SyncStatus
import com.mdot.app.core.sync.WebDavCreds
import com.mdot.app.core.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val loaded: Boolean = false,
    val status: SyncStatus = SyncStatus(),
    /** 存储源页当前浏览的类型（WebDAV / S3） */
    val kind: ProviderKind = ProviderKind.NONE,
    /** 全部存储源与当前选中 */
    val sources: StorageSources = StorageSources(),
    /** 弹窗表单（与 kind 对应） */
    val webdavForm: WebDavCreds = WebDavCreds(),
    val s3Form: S3Creds = S3Creds(),
    /** 新增存储源弹窗；null = 关闭 */
    val dialog: StorageSourceDialog? = null,
    /** 正在测试/切换连接的源 id */
    val testingId: String? = null,
    /** 新增保存中 */
    val adding: Boolean = false,
    /** 待确认删除的源 id */
    val pendingDeleteId: String? = null,
    /** 断开当前存储源确认 */
    val disconnectConfirm: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val confirmRestore: RemoteSummary? = null,
    val autoBackup: Boolean = true,
    val historyCopy: Boolean = true,
    /** 本地文件备份忙碌中 */
    val localBusy: Boolean = false,
    /** 待确认的本地文件恢复摘要 */
    val pendingLocal: LocalRestorePending? = null,
    /** 本地恢复完成，等待重启提示 */
    val restartRequired: Boolean = false,
)

/** 新增/编辑存储源弹窗状态；editingId != null 表示编辑已有源 */
data class StorageSourceDialog(
    val kind: ProviderKind,
    val name: String = "",
    val editingId: String? = null,
    val error: String? = null,
)

/** 本地备份文件恢复前确认摘要 */
data class LocalRestorePending(
    val recordCount: Int,
    val createdAt: String,
    val appVersion: String,
    val bytes: ByteArray,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val engine: SyncEngine,
    private val vault: com.mdot.app.core.sync.CredentialVault,
    private val settings: SettingsDataSource,
    @ApplicationContext private val context: Context,
    private val codec: BackupCodec,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sources = vault.loadSources()
            val kind = when {
                sources.selectedWebDav() != null -> ProviderKind.WEBDAV
                sources.selectedS3() != null -> ProviderKind.S3
                sources.webdav.isNotEmpty() -> ProviderKind.WEBDAV
                sources.s3.isNotEmpty() -> ProviderKind.S3
                else -> ProviderKind.WEBDAV
            }
            _state.update {
                it.copy(loaded = true, kind = kind, sources = sources)
            }
        }
        viewModelScope.launch {
            engine.status.collect { st -> _state.update { it.copy(status = st) } }
        }
        viewModelScope.launch {
            settings.autoBackupEnabledFlow.collect { v -> _state.update { it.copy(autoBackup = v) } }
        }
        viewModelScope.launch {
            settings.historyCopyEnabledFlow.collect { v -> _state.update { it.copy(historyCopy = v) } }
        }
    }

    fun onKind(kind: ProviderKind) = _state.update { it.copy(kind = kind, message = null) }

    fun onWebdavForm(creds: WebDavCreds) = _state.update { it.copy(webdavForm = creds) }

    fun onS3Form(creds: S3Creds) = _state.update { it.copy(s3Form = creds) }

    // ---- 多存储源（v0.5fix） ----

    /** 打开新增弹窗：表单重置为空 */
    fun openAddDialog() = _state.update {
        it.copy(
            dialog = StorageSourceDialog(kind = it.kind),
            webdavForm = WebDavCreds(),
            s3Form = S3Creds(),
            message = null,
        )
    }


    /** 打开编辑弹窗：按目标源预填表单 */
    fun openEditDialog(id: String) {
        val s = _state.value
        val webdav = s.sources.webdav.firstOrNull { it.id == id }
        val s3 = s.sources.s3.firstOrNull { it.id == id }
        _state.update {
            when {
                webdav != null -> it.copy(
                    dialog = StorageSourceDialog(kind = ProviderKind.WEBDAV, name = webdav.name, editingId = id),
                    webdavForm = webdav.creds,
                    message = null,
                )

                s3 != null -> it.copy(
                    dialog = StorageSourceDialog(kind = ProviderKind.S3, name = s3.name, editingId = id),
                    s3Form = s3.creds,
                    message = null,
                )

                else -> it
            }
        }
    }

    fun onDialogName(name: String) = _state.update {
        it.copy(dialog = it.dialog?.copy(name = name))
    }

    fun closeAddDialog() = _state.update { it.copy(dialog = null) }

    /** 保存（新增或编辑）存储源 → 新增自动测试并切换；编辑选中源时先测连接通过才保存 */
    fun confirmAdd() {
        val s = _state.value
        val dialog = s.dialog ?: return
        viewModelScope.launch {
            _state.update { it.copy(adding = true, message = null, isError = false) }
            val editingId = dialog.editingId
            when (dialog.kind) {
                ProviderKind.WEBDAV -> when (editingId) {
                    null -> when (val r = engine.addWebDavSource(dialog.name, s.webdavForm)) {
                        is AppResult.Failure -> failDialog(r.error, "保存")
                        is AppResult.Success -> finishAdd(r.data.id)
                    }

                    else -> when (val r = engine.updateWebDavSource(editingId, dialog.name, s.webdavForm)) {
                        is AppResult.Failure -> failDialog(r.error, "更新")
                        is AppResult.Success -> finishEdit(editingId)
                    }
                }

                ProviderKind.S3 -> when (editingId) {
                    null -> when (val r = engine.addS3Source(dialog.name, s.s3Form)) {
                        is AppResult.Failure -> failDialog(r.error, "保存")
                        is AppResult.Success -> finishAdd(r.data.id)
                    }

                    else -> when (val r = engine.updateS3Source(editingId, dialog.name, s.s3Form)) {
                        is AppResult.Failure -> failDialog(r.error, "更新")
                        is AppResult.Success -> finishEdit(editingId)
                    }
                }

                ProviderKind.NONE -> failDialog(com.mdot.app.core.util.AppError.InvalidName, "保存")
            }
        }
    }

    private suspend fun failDialog(error: com.mdot.app.core.util.AppError, action: String) {
        val msg = "${action}失败：${friendlyText(error)}"
        _state.update {
            it.copy(
                adding = false,
                dialog = it.dialog?.copy(error = msg),
                message = null,
                isError = true,
            )
        }
    }

    private fun finishEdit(id: String) {
        _state.update { it.copy(adding = false, dialog = null) }
        viewModelScope.launch {
            refreshSources()
            val name = findSourceName(id) ?: ""
            message("已更新「$name」配置")
        }
    }

    private fun finishAdd(id: String) {
        _state.update { it.copy(adding = false, dialog = null) }
        activateSource(id, alreadyAdded = true)
    }

    /** 点击列表条目：先测连接，通过才切换 */
    fun onSelectSource(id: String) {
        val s = _state.value
        if (id == s.sources.selectedId) return
        activateSource(id)
    }

    private fun activateSource(id: String, alreadyAdded: Boolean = false) {
        val s = _state.value
        if (s.testingId != null) return
        _state.update { it.copy(testingId = id, message = null, isError = false) }
        viewModelScope.launch {
            val result = engine.selectSource(id)
            val name = findSourceName(id) ?: ""
            _state.update { it.copy(testingId = null) }
            when (result) {
                is AppResult.Success -> {
                    refreshSources()
                    message(if (alreadyAdded) "已保存并切换到「$name」" else "已切换到「$name」")
                }

                is AppResult.Failure -> {
                    refreshSources()
                    message(
                        if (alreadyAdded) "已保存，但连接失败：${friendlyText(result.error)}（未切换）"
                        else "连接失败：${friendlyText(result.error)}，未切换",
                        isError = true,
                    )
                }
            }
        }
    }

    fun requestDelete(id: String) = _state.update { it.copy(pendingDeleteId = id) }

    fun cancelDelete() = _state.update { it.copy(pendingDeleteId = null) }

    fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        viewModelScope.launch {
            when (val r = engine.deleteSource(id)) {
                is AppResult.Success -> {
                    _state.update { it.copy(pendingDeleteId = null) }
                    refreshSources()
                    message("已删除存储源")
                }

                is AppResult.Failure -> {
                    _state.update { it.copy(pendingDeleteId = null) }
                    message("删除失败：${friendlyText(r.error)}", isError = true)
                }
            }
        }
    }

    fun requestDisconnect() = _state.update { it.copy(disconnectConfirm = true) }

    fun cancelDisconnect() = _state.update { it.copy(disconnectConfirm = false) }

    fun confirmDisconnect() = viewModelScope.launch {
        _state.update { it.copy(disconnectConfirm = false) }
        when (val r = engine.disconnectSource()) {
            is AppResult.Success -> {
                refreshSources()
                message("已断开存储源")
            }

            is AppResult.Failure -> message("断开失败：${friendlyText(r.error)}", isError = true)
        }
    }

    private suspend fun refreshSources() {
        _state.update { it.copy(sources = vault.loadSources()) }
    }

    private fun findSourceName(id: String): String? {
        val s = _state.value
        return s.sources.webdav.firstOrNull { it.id == id }?.name
            ?: s.sources.s3.firstOrNull { it.id == id }?.name
    }

    fun onAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settings.setAutoBackupEnabled(enabled)
    }

    fun onHistoryCopy(enabled: Boolean) = viewModelScope.launch {
        settings.setHistoryCopyEnabled(enabled)
    }

    /** 备份/恢复（同步备份页） */

    fun backupNow() = viewModelScope.launch {
        when (val r = engine.backupNow()) {
            is AppResult.Success -> message("备份完成")
            is AppResult.Failure -> message("备份失败：${friendlyText(r.error)}", isError = true)
        }
    }

    /** 准备恢复：拉取远端摘要出确认卡 */
    fun prepareRestore() = viewModelScope.launch {
        when (val r = engine.fetchRemoteSummary()) {
            is AppResult.Success -> {
                if (r.data == null) {
                    message("云端还没有备份包", isError = true)
                } else {
                    _state.update { it.copy(confirmRestore = r.data, message = null) }
                }
            }

            is AppResult.Failure -> message("读取云端失败：${friendlyText(r.error)}", isError = true)
        }
    }

    fun confirmRestore() = viewModelScope.launch {
        _state.update { it.copy(confirmRestore = null) }
        when (val r = engine.restoreNow()) {
            is AppResult.Success -> message("恢复完成，数据已还原")
            is AppResult.Failure -> message("恢复失败：${friendlyText(r.error)}", isError = true)
        }
    }

    fun cancelRestore() = _state.update { it.copy(confirmRestore = null) }

    // ---- 本地文件备份/恢复（不依赖网盘，经系统文件选择器） ----

    private fun deviceModel(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** 导出全量备份 ZIP 到用户选择的位置 */
    fun exportLocalFile(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(localBusy = true, message = null, isError = false) }
        runCatching {
            val pkg = codec.export(
                ManifestBase(
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    deviceModel = deviceModel(),
                )
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(pkg.zipBytes)
                out.flush()
            } ?: error("无法写入所选位置")
            pkg.manifest.recordCount
        }.onSuccess { recordCount ->
            _state.update {
                it.copy(localBusy = false, message = "备份文件已保存（共 $recordCount 条记录）")
            }
        }.onFailure { e ->
            _state.update {
                it.copy(
                    localBusy = false,
                    message = "导出失败：${e.message ?: "未知错误"}",
                    isError = true,
                )
            }
        }
    }

    /** 读取用户选择的备份文件，校验后进入确认卡 */
    fun prepareLocalRestore(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(localBusy = true, message = null, isError = false) }
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取所选文件")
            val parsed = codec.unzip(bytes)
            LocalRestorePending(
                recordCount = parsed.manifest.recordCount,
                createdAt = parsed.manifest.createdAt,
                appVersion = parsed.manifest.appVersionName.ifBlank { "未知版本" },
                bytes = bytes,
            )
        }.onSuccess { pending ->
            _state.update { it.copy(localBusy = false, pendingLocal = pending) }
        }.onFailure { e ->
            _state.update {
                it.copy(
                    localBusy = false,
                    message = "读取备份失败：${e.message ?: "文件格式不正确"}",
                    isError = true,
                )
            }
        }
    }

    fun cancelLocalRestore() = _state.update { it.copy(pendingLocal = null) }

    /** 确认本地文件恢复：导入后提示重启 */
    fun confirmLocalRestore() = viewModelScope.launch {
        val pending = _state.value.pendingLocal ?: return@launch
        _state.update { it.copy(localBusy = true) }
        runCatching {
            val parsed = codec.unzip(pending.bytes)
            codec.import(parsed.data)
        }.onSuccess {
            _state.update {
                it.copy(
                    localBusy = false,
                    pendingLocal = null,
                    restartRequired = true,
                    message = "恢复完成，重启应用后生效",
                )
            }
        }.onFailure { e ->
            _state.update {
                it.copy(
                    localBusy = false,
                    message = "恢复失败：${e.message ?: "未知错误"}",
                    isError = true,
                )
            }
        }
    }

    fun dismissRestart() = _state.update { it.copy(restartRequired = false) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun message(text: String, isError: Boolean = false) =
        _state.update { it.copy(message = text, isError = isError) }

    companion object {
        /** AppError → 用户可读文案：Storage 只取 reason，避免 data class toString（Storage(reason=…)）泄漏到界面 */
        fun friendlyText(error: com.mdot.app.core.util.AppError): String = when (error) {
            is com.mdot.app.core.util.AppError.Storage -> error.reason
            com.mdot.app.core.util.AppError.InvalidName -> "名称不能为空或过长"
            com.mdot.app.core.util.AppError.Unexpected -> "发生未知错误，请重试"
            else -> error.toString()
        }

        /** 本地备份默认文件名 */
        fun defaultLocalFileName(): String {
            val ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            return "mdot-backup-$ts.zip"
        }
    }
}
