package com.mdot.app.core.sync

import android.content.Context
import android.os.Build
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.AppResult.Failure
import com.mdot.app.core.util.AppResult.Success
import com.mdot.app.di.ApplicationScope
import com.mdot.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncPhase { IDLE, UPLOADING, RESTORING }

data class RemoteSummary(
    val createdAt: String,
    val recordCount: Int,
    val deviceModel: String,
    val appVersionName: String,
)

data class SyncStatus(
    val phase: SyncPhase = SyncPhase.IDLE,
    val configured: Boolean = false,
    val providerKind: ProviderKind = ProviderKind.NONE,
    val lastBackupAt: Long = 0,
    val lastError: String? = null,
    val remoteSummary: RemoteSummary? = null,
    /** 双向改动：本地脏且远端有更新——自动备份被阻断，需用户定向 */
    val conflict: Boolean = false,
    val busy: Boolean = false,
)

/**
 * 同步引擎（06 文档）：备份/恢复式同步，单设备语义，不自动合并。
 * 备份 = 导出 ZIP → 上传 current.zip + current.json → 滚动 history → 更新锚点。
 * 自动备份 = 数据变更后 30s 防抖；远端有新包时阻断并提示冲突。
 */
@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val codec: BackupCodec,
    private val vault: CredentialVault,
    private val settings: SettingsDataSource,
    private val webDavProvider: WebDavProvider,
    private val s3Provider: S3Provider,
    private val dataRevision: com.mdot.app.core.repository.DataRevision,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var provider: StorageProvider? = null

    init {
        appScope.launch {
            reloadConfiguration()
            _status.update { it.copy(lastBackupAt = settings.lastBackupAtFlow.first()) }
        }
        appScope.launch { autoBackupLoop() }
    }

    // ---- 配置 ----

    suspend fun reloadConfiguration() {
        val webDav = vault.loadWebDav()
        val s3 = vault.loadS3()
        provider = when {
            webDav != null && webDav.baseUrl.isNotBlank() ->
                webDavProvider.configure(
                    WebDavConfig(
                        baseUrl = webDav.baseUrl,
                        username = webDav.username,
                        password = webDav.password,
                        trustSelfSigned = webDav.trustSelfSigned,
                    )
                )

            s3 != null && s3.endpoint.isNotBlank() ->
                s3Provider.configure(
                    S3Config(
                        endpoint = s3.endpoint, bucket = s3.bucket, region = s3.region,
                        accessKeyId = s3.accessKeyId, secretAccessKey = s3.secretAccessKey,
                        pathPrefix = s3.pathPrefix,
                        trustSelfSigned = s3.trustSelfSigned,
                    )
                )

            else -> null
        }
        _status.update { it.copy(configured = provider != null) }
    }

    suspend fun saveWebDavConfig(creds: WebDavCreds): AppResult<Unit> {
        if (creds.baseUrl.isBlank() || creds.username.isBlank()) {
            return Failure(AppError.InvalidName)
        }
        vault.saveWebDav(creds)
        reloadConfiguration()
        return Success(Unit)
    }

    suspend fun saveS3Config(creds: S3Creds): AppResult<Unit> {
        if (creds.endpoint.isBlank() || creds.bucket.isBlank() || creds.accessKeyId.isBlank()) {
            return Failure(AppError.InvalidName)
        }
        vault.saveS3(creds)
        reloadConfiguration()
        return Success(Unit)
    }

    suspend fun testConnection(): AppResult<Unit> {
        val p = provider ?: return Failure(AppError.Storage("请先选择并保存存储源"))
        return try {
            runStep("测试连接") { p.ensureBaseDir().getOrThrow() }
            Success(Unit)
        } catch (e: Exception) {
            Failure(AppError.Storage(e.message ?: "连接失败"))
        }
    }

    // ---- 路径约定（05 文档 §4/§5） ----

    private fun rel(path: String): String =
        SyncPolicy.remotePath(provider?.kind ?: ProviderKind.NONE, path)

    private fun currentZip() = rel("backup/current.zip")
    private fun currentJson() = rel("backup/current.json")
    private fun historyDir() = "backup/history"

    // ---- 备份 ----

    suspend fun backupNow(): AppResult<Unit> {
        val p = provider ?: return Failure(AppError.Storage("请先配置存储源"))
        _status.update { it.copy(phase = SyncPhase.UPLOADING, busy = true, lastError = null) }
        return try {
            val manifestBase = ManifestBase(
                appVersionName = appVersionName(),
                appVersionCode = appVersionCode(),
                deviceModel = Build.MODEL ?: "Unknown",
            )
            val pkg = codec.export(manifestBase)

            runStep("创建远端目录") { p.ensureBaseDir().getOrThrow() }
            android.util.Log.i("SyncEngine", "backup kind=${'$'}{p.kind} rel=${'$'}{currentZip()}")
            runStep("上传 current.zip") {
                val lastEtag = settings.lastEtagFlow.first()
                val put = p.put(currentZip(), pkg.zipBytes, ifMatch = lastEtag).getOrThrow()
                settings.setLastEtag(put.etag)
            }
            runStep("上传 current.json") {
                val manifestText = codec.json.encodeToString(ManifestDto.serializer(), pkg.manifest)
                p.put(currentJson(), manifestText.toByteArray(Charsets.UTF_8)).getOrThrow()
            }

            // history 副本与滚动清理（流量敏感可关，06 文档 §3）
            if (settings.historyCopyEnabledFlow.first()) {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val histDir = rel(historyDir())
                runCatching {
                    p.put("$histDir/backup-$stamp.zip", pkg.zipBytes).getOrThrow()
                    cleanupHistory(p)
                }.onFailure { e ->
                    android.util.Log.w("SyncEngine", "history 副本上传失败", e)
                }
            }

            settings.setLastSyncedRemoteCreatedAt(pkg.manifest.createdAt)
            val now = System.currentTimeMillis()
            settings.setLastBackupAt(now)
            _status.update {
                it.copy(
                    phase = SyncPhase.IDLE, busy = false,
                    lastBackupAt = now,
                    remoteSummary = null, conflict = false,
                )
            }
            Success(Unit)
        } catch (e: Exception) {
            fail(e.message ?: "备份失败")
            Failure(AppError.Storage(e.message ?: "备份失败"))
        }
    }

    private suspend fun cleanupHistory(p: StorageProvider) {
        // list 必须与 put/delete 同路径（含 rel 前缀），否则 PROPFIND 404 → 清理永不执行
        val files = p.list(rel(historyDir())).getOrDefault(emptyList())
        SyncPolicy.selectHistoryToDelete(files, MAX_HISTORY).forEach { name ->
            // list 返回的 path 可能是服务端完整 href，删除按文件名相对路径
            p.delete("${rel(historyDir())}/$name")
        }
    }

    // ---- 恢复 ----

    /** 拉取远端摘要（恢复前确认卡用；仅下载轻量 current.json） */
    suspend fun fetchRemoteSummary(): AppResult<RemoteSummary?> {
        val p = provider ?: return Failure(AppError.Storage("请先配置存储源"))
        return try {
            val bytes = p.get(currentJson()).getOrThrow()
            if (bytes == null) {
                _status.update { it.copy(remoteSummary = null) }
                Success(null)
            } else {
                val manifest = codec.parseManifest(bytes.decodeToString())
                val summary = RemoteSummary(
                    createdAt = manifest.createdAt,
                    recordCount = manifest.recordCount,
                    deviceModel = manifest.deviceModel,
                    appVersionName = manifest.appVersionName,
                )
                _status.update { it.copy(remoteSummary = summary) }
                Success(summary)
            }
        } catch (e: Exception) {
            Failure(AppError.Storage(e.message ?: "读取远端信息失败"))
        }
    }

    /** 恢复：本地快照 → 下载整包 → 校验 → 事务导入 → 锚点同步 */
    suspend fun restoreNow(): AppResult<Unit> {
        val p = provider ?: return Failure(AppError.Storage("请先配置存储源"))
        _status.update { it.copy(phase = SyncPhase.RESTORING, busy = true, lastError = null) }
        return try {
            // 1. 恢复前本地快照（兜底可反悔）
            runStep("生成本地快照") {
                val manifestBase = ManifestBase(
                    appVersionName(), appVersionCode(), Build.MODEL ?: "Unknown",
                )
                val pkg = codec.export(manifestBase)
                val dir = File(context.cacheDir, "backup").apply { mkdirs() }
                File(dir, "pre-restore-${System.currentTimeMillis()}.zip")
                    .writeBytes(pkg.zipBytes)
            }
            // 2. 下载整包
            val zipBytes = runStep("下载备份包") {
                p.get(currentZip()).getOrThrow()
                    ?: throw IllegalArgumentException("云端没有备份包")
            }
            // 3. 校验结构 + 4. 事务导入
            runStep("导入数据") {
                val parsed = codec.unzip(zipBytes)
                codec.import(parsed.data)
            }
            // 5. 锚点同步
            val manifestText = p.get(currentJson()).getOrThrow()?.decodeToString()
            manifestText?.let {
                settings.setLastSyncedRemoteCreatedAt(codec.parseManifest(it).createdAt)
            }
            // 6. 显式全量重算：首页/统计等订阅 DataRevision 的界面立即重算（import 内的
            //    touch() 只负责"本地已脏"标记，不驱动 UI 重算）
            dataRevision.bump()
            _status.update {
                it.copy(phase = SyncPhase.IDLE, busy = false, lastBackupAt = System.currentTimeMillis())
            }
            Success(Unit)
        } catch (e: Exception) {
            fail(e.message ?: "恢复失败")
            Failure(AppError.Storage(e.message ?: "恢复失败"))
        }
    }

    // ---- 自动备份（防抖 30s，06 文档 §4） ----

    private suspend fun autoBackupLoop() {
        SyncPolicy.lastChangeTrigger(settings.lastLocalChangeAtFlow, AUTO_BACKUP_DEBOUNCE_MS)
            .collect {
                val p = provider ?: return@collect
                if (!settings.autoBackupEnabledFlow.first()) return@collect
                if (_status.value.busy) return@collect

                // 三态判定：远端有新包且本地也脏 → 冲突，阻断自动上传（06 文档 §4）
                val summary = when (val r = fetchRemoteSummary()) {
                    is AppResult.Success -> r.data
                    is AppResult.Failure -> return@collect
                }
                val lastSynced = settings.lastSyncedRemoteCreatedAtFlow.first()
                if (summary != null && summary.createdAt != lastSynced) {
                    _status.update { it.copy(conflict = true, remoteSummary = summary) }
                    return@collect
                }
                backupNow()
            }
    }

    // ---- 工具 ----

    private suspend fun <T> runStep(step: String, block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw IllegalStateException("$step 失败：${e.message}", e)
    }

    private suspend fun fail(message: String) {
        _status.update { it.copy(phase = SyncPhase.IDLE, busy = false, lastError = message) }
    }

    private fun appVersionName(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"

    private fun appVersionCode(): Int =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).let {
                @Suppress("DEPRECATION")
                it.versionCode
            }
        }.getOrDefault(1)

    companion object {
        const val MAX_HISTORY = 5
        const val AUTO_BACKUP_DEBOUNCE_MS = 30_000L
    }
}
