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
    /** 表单当前选择的源类型 */
    val kind: ProviderKind = ProviderKind.NONE,
    val webdavForm: WebDavCreds = WebDavCreds(),
    val s3Form: S3Creds = S3Creds(),
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
            val webDav = vault.loadWebDav()
            val s3 = vault.loadS3()
            val kind = when {
                webDav != null && webDav.baseUrl.isNotBlank() -> ProviderKind.WEBDAV
                s3 != null && s3.endpoint.isNotBlank() -> ProviderKind.S3
                else -> ProviderKind.NONE
            }
            _state.update {
                it.copy(
                    loaded = true,
                    kind = kind,
                    webdavForm = webDav ?: WebDavCreds(),
                    s3Form = s3 ?: S3Creds(),
                )
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

    fun onAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settings.setAutoBackupEnabled(enabled)
    }

    fun onHistoryCopy(enabled: Boolean) = viewModelScope.launch {
        settings.setHistoryCopyEnabled(enabled)
    }

    /** 保存配置并测试连接 */
    fun saveAndTest() {
        val s = _state.value
        viewModelScope.launch {
            val result = when (s.kind) {
                ProviderKind.WEBDAV -> engine.saveWebDavConfig(s.webdavForm)
                ProviderKind.S3 -> engine.saveS3Config(s.s3Form)
                ProviderKind.NONE -> AppResult.Success(Unit)
            }
            if (result is AppResult.Failure) {
                message("保存失败：${result.error}", isError = true)
                return@launch
            }
            if (s.kind == ProviderKind.NONE) {
                // 清除凭据
                vault.clearWebDav()
                vault.clearS3()
                engine.reloadConfiguration()
                message("已断开存储源")
                return@launch
            }
            when (val test = engine.testConnection()) {
                is AppResult.Success -> message("连接成功，存储源已就绪")
                is AppResult.Failure -> message("连接失败：${test.error}", isError = true)
            }
        }
    }

    fun backupNow() = viewModelScope.launch {
        when (val r = engine.backupNow()) {
            is AppResult.Success -> message("备份完成")
            is AppResult.Failure -> message("备份失败：${r.error}", isError = true)
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

            is AppResult.Failure -> message("读取云端失败：${r.error}", isError = true)
        }
    }

    fun confirmRestore() = viewModelScope.launch {
        _state.update { it.copy(confirmRestore = null) }
        when (val r = engine.restoreNow()) {
            is AppResult.Success -> message("恢复完成，数据已还原")
            is AppResult.Failure -> message("恢复失败：${r.error}", isError = true)
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
        /** 本地备份默认文件名 */
        fun defaultLocalFileName(): String {
            val ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            return "mdot-backup-$ts.zip"
        }
    }
}
