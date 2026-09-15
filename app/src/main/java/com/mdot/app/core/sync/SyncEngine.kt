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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
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
 * 备份 = 导出 ZIP（内含 manifest.json）→ 上传 current.zip（云端/本地各只有一个压缩包）→ 更新锚点。
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
    private val client: OkHttpClient,
    private val cleartextGate: com.mdot.app.core.network.CleartextGate,
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

    // ---- 配置（v0.5fix 多存储源：增/删/切换，切换前先测连接） ----

    /** 重载当前选中的存储源为活动 provider */
    suspend fun reloadConfiguration() {
        val sources = vault.loadSources()
        val webDav = sources.selectedWebDav()
        val s3 = sources.selectedS3()
        // 明文守门（13 文档 B2-04）：把用户显式配置的源 host 登记进 CleartextGate，
        // 拦截器只放行这些 host 的 http 请求——其余明文一律拒绝
        listOfNotNull(webDav?.creds?.baseUrl, s3?.creds?.endpoint).forEach { cleartextGate.allowUrl(it) }
        provider = when {
            webDav != null -> webDavProvider.configure(
                WebDavConfig(
                    baseUrl = webDav.creds.baseUrl,
                    username = webDav.creds.username,
                    password = webDav.creds.password,
                    trustSelfSigned = webDav.creds.trustSelfSigned,
                    certSha256 = webDav.creds.certSha256,
                    // TOFU 首连指纹落库（13 文档 B2-02）：TLS 握手线程同步回调，runBlocking 一次可接受
                    onCertPinned = { fp -> persistWebDavPin(webDav.id, fp) },
                )
            )

            s3 != null -> s3Provider.configure(
                S3Config(
                    endpoint = s3.creds.endpoint, bucket = s3.creds.bucket,
                    region = s3.creds.region,
                    accessKeyId = s3.creds.accessKeyId,
                    secretAccessKey = s3.creds.secretAccessKey,
                    pathPrefix = s3.creds.pathPrefix,
                    trustSelfSigned = s3.creds.trustSelfSigned,
                    certSha256 = s3.creds.certSha256,
                    onCertPinned = { fp -> persistS3Pin(s3.id, fp) },
                )
            )

            else -> null
        }
        _status.update {
            it.copy(configured = provider != null, providerKind = provider?.kind ?: ProviderKind.NONE)
        }
    }

    /** 新增 WebDAV 存储源（校验必填；仅入列表，不自动切换） */
    suspend fun addWebDavSource(name: String, creds: WebDavCreds): AppResult<WebDavSource> {
        if (creds.baseUrl.isBlank() || creds.username.isBlank()) {
            return Failure(AppError.Storage("请填写服务器地址与账号"))
        }
        val source = WebDavSource(
            id = "w-" + java.util.UUID.randomUUID(),
            name = name.ifBlank { "WebDAV 存储源" },
            creds = creds,
        )
        val sources = vault.loadSources()
        vault.saveSources(sources.copy(webdav = sources.webdav + source))
        return Success(source)
    }

    /** 新增 S3 存储源（校验必填；仅入列表，不自动切换） */
    suspend fun addS3Source(name: String, creds: S3Creds): AppResult<S3Source> {
        if (creds.endpoint.isBlank() || creds.bucket.isBlank() || creds.accessKeyId.isBlank()) {
            return Failure(AppError.Storage("请填写 Endpoint、Bucket 与 AccessKeyId"))
        }
        val source = S3Source(
            id = "s-" + java.util.UUID.randomUUID(),
            name = name.ifBlank { "S3 存储源" },
            creds = creds,
        )
        val sources = vault.loadSources()
        vault.saveSources(sources.copy(s3 = sources.s3 + source))
        return Success(source)
    }

    /** 更新 WebDAV 存储源配置；被编辑的是当前选中源时先测连接，通过才保存并重载（避免改坏在用配置） */
    suspend fun updateWebDavSource(id: String, name: String, creds: WebDavCreds): AppResult<Unit> {
        if (creds.baseUrl.isBlank() || creds.username.isBlank()) {
            return Failure(AppError.Storage("请填写服务器地址与账号"))
        }
        val sources = vault.loadSources()
        val idx = sources.webdav.indexOfFirst { it.id == id }
        if (idx < 0) return Failure(AppError.Storage("存储源不存在"))
        val updatedList = sources.webdav.toMutableList().also {
            it[idx] = it[idx].copy(name = name.ifBlank { it[idx].name }, creds = creds)
        }
        val updated = sources.copy(webdav = updatedList)
        if (sources.selectedId == id) {
            when (val r = testProvider(tempWebDavProvider(updatedList[idx]))) {
                is Failure -> return r
                is Success -> Unit
            }
        }
        vault.saveSources(updated)
        if (sources.selectedId == id) {
            resetSyncAnchors()
            reloadConfiguration()
        }
        return Success(Unit)
    }

    /** 更新 S3 存储源配置；被编辑的是当前选中源时先测连接，通过才保存并重载 */
    suspend fun updateS3Source(id: String, name: String, creds: S3Creds): AppResult<Unit> {
        if (creds.endpoint.isBlank() || creds.bucket.isBlank() || creds.accessKeyId.isBlank()) {
            return Failure(AppError.Storage("请填写 Endpoint、Bucket 与 AccessKeyId"))
        }
        val sources = vault.loadSources()
        val idx = sources.s3.indexOfFirst { it.id == id }
        if (idx < 0) return Failure(AppError.Storage("存储源不存在"))
        val updatedList = sources.s3.toMutableList().also {
            it[idx] = it[idx].copy(name = name.ifBlank { it[idx].name }, creds = creds)
        }
        val updated = sources.copy(s3 = updatedList)
        if (sources.selectedId == id) {
            when (val r = testProvider(tempS3Provider(updatedList[idx]))) {
                is Failure -> return r
                is Success -> Unit
            }
        }
        vault.saveSources(updated)
        if (sources.selectedId == id) {
            resetSyncAnchors()
            reloadConfiguration()
        }
        return Success(Unit)
    }

    /** 切换存储源：先测连接，通过才持久化选中并重置同步锚点 */
    suspend fun selectSource(id: String): AppResult<Unit> {
        val sources = vault.loadSources()
        val candidate = sources.webdav.firstOrNull { it.id == id } ?: sources.s3.firstOrNull { it.id == id }
            ?: return Failure(AppError.Storage("存储源不存在"))
        val testProvider = when {
            candidate is WebDavSource -> tempWebDavProvider(candidate)
            else -> tempS3Provider(candidate as S3Source)
        }
        when (val r = testProvider(testProvider)) {
            is Failure -> return r
            is Success -> Unit
        }
        vault.saveSources(sources.copy(selectedId = id))
        resetSyncAnchors()
        reloadConfiguration()
        return Success(Unit)
    }

    /** 删除存储源；删除的是当前选中源时同时断开 */
    suspend fun deleteSource(id: String): AppResult<Unit> {
        val sources = vault.loadSources()
        val webdav = sources.webdav.filterNot { it.id == id }
        val s3 = sources.s3.filterNot { it.id == id }
        if (webdav.size == sources.webdav.size && s3.size == sources.s3.size) {
            return Failure(AppError.Storage("存储源不存在"))
        }
        val selectedId = if (sources.selectedId == id) null else sources.selectedId
        vault.saveSources(sources.copy(webdav = webdav, s3 = s3, selectedId = selectedId))
        if (selectedId == null) {
            resetSyncAnchors()
            reloadConfiguration()
        }
        return Success(Unit)
    }

    /** 断开当前存储源（不删除配置，仅取消选中） */
    suspend fun disconnectSource(): AppResult<Unit> {
        val sources = vault.loadSources()
        if (sources.selectedId == null) return Success(Unit)
        vault.saveSources(sources.copy(selectedId = null))
        resetSyncAnchors()
        reloadConfiguration()
        return Success(Unit)
    }

    /** 用指定源配置测试连接（临时 provider，不影响当前活动源） */
    suspend fun testSource(id: String): AppResult<Unit> {
        val sources = vault.loadSources()
        val candidate = sources.webdav.firstOrNull { it.id == id } ?: sources.s3.firstOrNull { it.id == id }
            ?: return Failure(AppError.Storage("存储源不存在"))
        // 临时测试的源也可能是明文 http（用户正在新增/编辑）——同样登记
        when (candidate) {
            is WebDavSource -> cleartextGate.allowUrl(candidate.creds.baseUrl)
            is S3Source -> cleartextGate.allowUrl(candidate.creds.endpoint)
        }
        val p = when (candidate) {
            is WebDavSource -> tempWebDavProvider(candidate)
            else -> tempS3Provider(candidate as S3Source)
        }
        return testProvider(p)
    }

    private fun tempWebDavProvider(source: WebDavSource): StorageProvider =
        WebDavProvider(client, ioDispatcher).configure(
            WebDavConfig(
                baseUrl = source.creds.baseUrl,
                username = source.creds.username,
                password = source.creds.password,
                trustSelfSigned = source.creds.trustSelfSigned,
                certSha256 = source.creds.certSha256,
                // 临时测试连接：TOFU 指纹只校验不落库（正式保存走 updateWebDavSource 的重载链路）
                onCertPinned = null,
            )
        )

    private fun tempS3Provider(source: S3Source): StorageProvider =
        S3Provider(client, ioDispatcher).configure(
            S3Config(
                endpoint = source.creds.endpoint, bucket = source.creds.bucket,
                region = source.creds.region,
                accessKeyId = source.creds.accessKeyId,
                secretAccessKey = source.creds.secretAccessKey,
                pathPrefix = source.creds.pathPrefix,
                trustSelfSigned = source.creds.trustSelfSigned,
                certSha256 = source.creds.certSha256,
                onCertPinned = null,
            )
        )

    /** TOFU 指纹持久化（13 文档 B2-02）：写回该存储源的凭据密文。TLS 握手线程回调内 runBlocking（一次性） */
    private fun persistWebDavPin(id: String, fp: String) {
        runCatching {
            kotlinx.coroutines.runBlocking {
                val sources = vault.loadSources()
                val idx = sources.webdav.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    val updated = sources.webdav[idx].let { it.copy(creds = it.creds.copy(certSha256 = fp)) }
                    vault.saveSources(sources.copy(webdav = sources.webdav.toMutableList().also { it[idx] = updated }))
                }
            }
        }
    }

    private fun persistS3Pin(id: String, fp: String) {
        runCatching {
            kotlinx.coroutines.runBlocking {
                val sources = vault.loadSources()
                val idx = sources.s3.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    val updated = sources.s3[idx].let { it.copy(creds = it.creds.copy(certSha256 = fp)) }
                    vault.saveSources(sources.copy(s3 = sources.s3.toMutableList().also { it[idx] = updated }))
                }
            }
        }
    }

    /** 连接测试 = 建根目录 + 目录可达（与备份前置步骤一致）；异常归类为可读文案 */
    private suspend fun testProvider(p: StorageProvider): AppResult<Unit> = try {
        p.ensureBaseDir().getOrThrow()
        Success(Unit)
    } catch (e: Exception) {
        val reason = when (e) {
            is java.net.UnknownHostException -> "无法解析服务器地址，请检查地址是否正确"
            is java.net.ConnectException -> "无法连接到服务器，请检查地址与网络"
            is java.net.SocketTimeoutException -> "连接超时，请检查网络或服务器状态"
            is javax.net.ssl.SSLException -> "证书校验失败，可尝试开启「信任自签名证书」"
            is IllegalArgumentException -> "服务器地址格式不正确，需以 http:// 或 https:// 开头"
            else -> e.message?.takeIf { it.isNotBlank() } ?: "无法连接服务器，请检查配置"
        }
        Failure(AppError.Storage(reason))
    }

    /** 切换/断开存储源后重置同步锚点：不同源的备份历史与 ETag 相互独立 */
    private suspend fun resetSyncAnchors() {
        settings.setLastEtag(null)
        settings.setLastSyncedRemoteCreatedAt(null)
        settings.setLastBackupAt(0L)
    }

    // ---- 路径约定（05 文档 §4/§5） ----

    private fun rel(path: String): String =
        SyncPolicy.remotePath(provider?.kind ?: ProviderKind.NONE, path)

    private fun currentZip() = rel("backup/current.zip")
    private fun legacyCurrentJson() = rel("backup/current.json")
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

            // 清理旧版本残留：current.json 与 history/ 副本（云端目录只保留 current.zip，best-effort）
            runCatching {
                cleanupLegacy(p)
            }.onFailure { e ->
                android.util.Log.w("SyncEngine", "旧备份文件清理失败", e)
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
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c // 取消穿透（13 文档 B3-06）：被吞会跳过锚点写入，致下次 If-Match 412 死锁
        } catch (e: Exception) {
            fail(e.message ?: "备份失败")
            Failure(AppError.Storage(e.message ?: "备份失败"))
        }
    }

    private suspend fun cleanupLegacy(p: StorageProvider) {
        // 删除 current.json（manifest 已在 zip 内）
        p.delete(legacyCurrentJson())
        // 删除 history/ 全部旧副本
        val files = p.list(rel(historyDir())).getOrDefault(emptyList())
        files.forEach { meta ->
            val name = meta.path.substringAfterLast('/')
            p.delete("${rel(historyDir())}/$name")
        }
    }

    // ---- 恢复 ----

    /** 拉取远端摘要（恢复前确认卡用；下载整包解出 manifest）。
     *  快速路径（13 文档 B3-04②）：远端 ETag 与本地锚点一致 → 内容必同于上次已知，
     *  直接复用已存的 createdAt 摘要，免去整包下载（自动备份每次触发都调这里，大库慢 NAS 上分钟级）。 */
    suspend fun fetchRemoteSummary(): AppResult<RemoteSummary?> {
        val p = provider ?: return Failure(AppError.Storage("请先配置存储源"))
        return try {
            val lastEtag = settings.lastEtagFlow.first()
            if (lastEtag != null) {
                val meta = p.head(currentZip()).getOrThrow()
                if (meta != null && meta.etag == lastEtag) {
                    val cached = settings.lastSyncedRemoteCreatedAtFlow.first()?.let {
                        RemoteSummary(it, -1, "", "")
                    }
                    if (cached != null) {
                        _status.update { it.copy(remoteSummary = cached) }
                        return Success(cached)
                    }
                }
            }
            val (zipBytes, etag) = p.getWithEtag(currentZip()).getOrThrow()
            if (zipBytes == null) {
                _status.update { it.copy(remoteSummary = null) }
                Success(null)
            } else {
                if (etag != null) settings.setLastEtag(etag) // 顺带对齐锚点（B3-04①）
                val manifest = codec.unzip(zipBytes).manifest
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
            // 1. 恢复前本地快照（兜底可反悔；仅保留最近 1 份，13 文档 B3-12）
            runStep("生成本地快照") {
                val manifestBase = ManifestBase(
                    appVersionName(), appVersionCode(), Build.MODEL ?: "Unknown",
                )
                val pkg = codec.export(manifestBase)
                val dir = File(context.cacheDir, "backup").apply { mkdirs() }
                dir.listFiles { f -> f.name.startsWith("pre-restore-") }
                    ?.sortedByDescending { it.name }
                    ?.drop(1)
                    ?.forEach { runCatching { it.delete() } }
                File(dir, "pre-restore-${System.currentTimeMillis()}.zip")
                    .writeBytes(pkg.zipBytes)
            }
            // 2. 下载整包（回带 ETag）
            val (zipBytes, etag) = runStep("下载备份包") {
                p.getWithEtag(currentZip()).getOrThrow()
                    ?: throw IllegalArgumentException("云端没有备份包")
            }
            // 3. 校验结构（manifest 在包内）+ 4. 事务导入
            val parsed = runStep("校验结构") { codec.unzip(zipBytes!!) }
            runStep("导入数据") {
                codec.import(parsed.data)
            }
            // 5. 锚点同步（13 文档 B3-04①：createdAt 与 ETag 两个锚点都要跟上，
            //    否则恢复后首次 WebDAV 备份 If-Match 412 且无自愈入口）
            settings.setLastSyncedRemoteCreatedAt(parsed.manifest.createdAt)
            if (etag != null) settings.setLastEtag(etag)
            // 6. 显式全量重算：首页/统计等订阅 DataRevision 的界面立即重算（import 内的
            //    touch() 只负责"本地已脏"标记，不驱动 UI 重算）
            dataRevision.bump()
            _status.update {
                it.copy(phase = SyncPhase.IDLE, busy = false, lastBackupAt = System.currentTimeMillis())
            }
            Success(Unit)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
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
                // busy 排队而非丢弃（13 文档 B3-05）：手动备份/恢复进行中到达的变更触发
                // 若被消费丢弃，之后无新变更则永不自动备份——改为等待当前操作结束后补一轮
                var waits = 0
                while (_status.value.busy && waits < BUSY_WAIT_MAX_POLLS) {
                    delay(BUSY_WAIT_POLL_MS)
                    waits++
                }
                if (_status.value.busy) return@collect // 超时放弃，等下一次变更触发兜底

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
        const val AUTO_BACKUP_DEBOUNCE_MS = 30_000L
        /** busy 排队轮询（13 文档 B3-05）：2s 一次，最多等 5 分钟（大库恢复的量级），超时留给下次触发兜底 */
        const val BUSY_WAIT_POLL_MS = 2_000L
        const val BUSY_WAIT_MAX_POLLS = 150
    }
}
