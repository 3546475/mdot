package com.mdot.app.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.mdot.app.BuildConfig
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.network.JsonFetcher
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.AppResult.Failure
import com.mdot.app.core.util.AppResult.Success
import com.mdot.app.core.util.rethrowIfCancellation
import com.mdot.app.di.ApplicationScope
import com.mdot.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用自更新：从 update.json（GitHub Pages 托管）拉取最新版本信息，
 * 按 versionCode 比对；下载与当前设备 ABI 匹配的 APK 并调起系统安装器。
 * update.json 结构由发布工作流（.github/workflows/release.yml）生成。
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataSource,
    private val jsonFetcher: JsonFetcher,
    private val client: OkHttpClient,
    private val cleartextGate: com.mdot.app.core.network.CleartextGate,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val TAG = "UpdateRepository"
    }

    /**
     * 检查更新：有新版本返回 [UpdateInfo]，已是最新返回 null；网络/解析失败返回 Failure。
     *
     * **成功即落盘**（v0.7.6 冷启动自动检查）：发现新版记「已知新版本」（关于页版本号红星 +
     * 底部悬浮提示的数据源），已是最新则清掉；同时刷新 `lastCheckAt`（24h 节流，
     * 见 [shouldAutoCheckUpdate]）。失败不落 → 下次冷启动立即重试。
     */
    suspend fun check(): AppResult<UpdateInfo?> = try {
        val url = settings.updateUrlFlow.first()
        cleartextGate.allowUrl(url) // 用户配置的更新源允许明文（13 文档 B2-04）
        val info = json.decodeFromString<UpdateInfo>(jsonFetcher.fetchText(url))
        if (info.versionCode > BuildConfig.VERSION_CODE) {
            settings.setUpdateKnown(info.versionCode, info.versionName)
            settings.setLastUpdateCheckAt(System.currentTimeMillis())
            Success(info)
        } else {
            settings.clearUpdateKnown()
            settings.setLastUpdateCheckAt(System.currentTimeMillis())
            Success(null)
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (e: Exception) {
        Failure(AppError.Storage(e.message ?: "检查更新失败"))
    }

    /** 按设备 ABI 选择下载地址：arm64 优先，其次 armv7，兜底双架构包。 */
    fun pickApkUrl(info: UpdateInfo): String? =
        pickApkUrl(Build.SUPPORTED_ABIS ?: emptyArray(), info.apkUrl)

    /**
     * 下载 APK 到缓存目录（同名已存在则直接复用），[onProgress] 回调 0–100。
     * 下载走独立调用（流式写盘），不复用 JSON 的重试策略。
     * 整包 sha256 校验（13 文档 B2-03）：update.json 带 sha256 时强校验，不匹配拒绝安装。
     * 下载中退出页面不中断：任务挂 [appScope]（13 文档 B5-01，进度经 [downloadProgress] StateFlow 可再订阅）。
     */
    private val downloadProgress = MutableStateFlow<Int?>(null)

    /** 当前后台下载进度（null=无下载进行中）。页面级 VM 订阅它恢复 UI 态。 */
    val downloadProgressFlow: kotlinx.coroutines.flow.StateFlow<Int?> = downloadProgress.asStateFlow()

    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): AppResult<File> {
        // 已有后台任务在跑：直接等它（页面重建场景）——否则并发两份写同一 .part
        if (downloadProgress.value != null) {
            downloadProgress.asStateFlow().first { it == null || it == 100 }
            return apkResultFor(info)
        }
        val progressSink = { p: Int ->
            downloadProgress.value = p
            onProgress(p)
        }
        val result = kotlinx.coroutines.CompletableDeferred<AppResult<File>>()
        val job = appScope.launch {
            try {
                result.complete(downloadInternal(info, progressSink))
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                result.complete(Failure(AppError.Storage(e.message ?: "下载失败")))
            } finally {
                downloadProgress.value = null
            }
        }
        return try {
            result.await()
        } catch (c: kotlinx.coroutines.CancellationException) {
            job.cancel() // 调用方取消 → 后台任务随停（不留半途 .part 的孤儿任务）
            throw c
        }
    }

    private fun apkResultFor(info: UpdateInfo): AppResult<File> {
        val f = apkFile(info)
        return if (f.exists() && f.length() > 0) Success(f)
        else Failure(AppError.Storage("下载已结束但缓存文件缺失，请重试"))
    }

    private suspend fun downloadInternal(info: UpdateInfo, onProgress: (Int) -> Unit): AppResult<File> =
        withContext(ioDispatcher) {
            val url = pickApkUrl(info)
                ?: return@withContext Failure(AppError.Storage("没有可用的下载地址"))
            cleartextGate.allowUrl(url) // APK 与 update.json 同源（或官方 release 域），同放行（13 文档 B2-04）
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // 顺带清理旧版本缓存（13 文档 B5-02）：只保留当前版本与目标版本的 APK
            val keepNames = setOf("mdot-${BuildConfig.VERSION_NAME}.apk", "mdot-${info.versionName}.apk")
            dir.listFiles()?.filter { it.name !in keepNames && it.name.endsWith(".apk") }?.forEach {
                runCatching { it.delete() }
            }
            val target = File(dir, "mdot-${info.versionName}.apk")
            // 只信任完整且有效的缓存文件；无效残留直接删除重下
            if (target.exists() && target.length() > 0 && isCompleteZip(target) && sha256Matches(target, info)) {
                onProgress(100)
                return@withContext Success(target)
            }
            if (target.exists()) target.delete()
            // 下载到临时文件，成功后原子改名：中断残留不会被误当成完整 APK 复用
            val part = File(dir, "${target.name}.part")
            part.delete()
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Failure(AppError.Storage("下载失败：HTTP ${resp.code}"))
                    }
                    val body = resp.body ?: return@withContext Failure(AppError.Storage("下载失败：空响应"))
                    val total = body.contentLength()
                    var sent = 0L
                    val md = if (info.hasSha256()) java.security.MessageDigest.getInstance("SHA-256") else null
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(part).use { out ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
                                md?.update(buf, 0, n)
                                sent += n
                                if (total > 0) {
                                    onProgress(((sent * 100) / total).toInt().coerceIn(0, 100))
                                }
                            }
                        }
                    }
                    if (total > 0 && sent != total) {
                        return@withContext Failure(AppError.Storage("下载失败：文件不完整"))
                    }
                    if (!isCompleteZip(part)) {
                        return@withContext Failure(AppError.Storage("下载失败：文件无效"))
                    }
                    // 端到端完整性（13 文档 B2-03）：update.json 带 sha256 时强校验，MITM 换包/降级包在此拦截
                    if (md != null) {
                        val actual = md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        val expected = info.expectedSha256()
                        if (actual != expected) {
                            part.delete()
                            return@withContext Failure(AppError.Storage("下载失败：文件校验不符（可能被篡改或源配置错误），已放弃安装"))
                        }
                    }
                    if (!part.renameTo(target)) {
                        return@withContext Failure(AppError.Storage("下载失败：缓存写入失败"))
                    }
                    onProgress(100)
                    Success(target)
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                part.delete()
                Failure(AppError.Storage(e.message ?: "下载失败"))
            }
        }

    /** 完整性校验：打开 ZIP 中央目录（截断/HTML 错误页均会失败），只读目录开销小。 */
    private fun isCompleteZip(f: File): Boolean =
        try {
            java.util.zip.ZipFile(f).use { true }
        } catch (_: Exception) {
            false
        }

    /** update.json 是否携带本次 ABI 包的 sha256（13 文档 B2-03；旧版 update.json 无此字段=不校验，向后兼容） */
    private fun UpdateInfo.hasSha256(): Boolean = expectedSha256() != null

    /** 当前 ABI 应校验的 sha256（按 pickApkUrl 同序取对应字段） */
    private fun UpdateInfo.expectedSha256(): String? {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        return when {
            abis.contains("arm64-v8a") -> apkSha256?.arm64V8a
            abis.contains("armeabi-v7a") -> apkSha256?.armeabiV7a
            else -> apkSha256?.universal
        } ?: apkSha256?.universal
    }

    /** 已缓存文件的 sha256 是否与 update.json 一致（无期望值=通过） */
    private fun sha256Matches(f: File, info: UpdateInfo): Boolean {
        val expected = info.expectedSha256() ?: return true
        val actual = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return actual == expected
    }

    /**
     * 用 PackageInstaller Session API 安装已下载的 APK。
     * 需要 Manifest 声明 REQUEST_INSTALL_PACKAGES（v0.5.7 曾误删致 SecurityException
     * 被吞、表现为「无法调起安装器」）；首次安装时系统弹确认界面，若用户未开
     * 「允许安装未知应用」开关会先引导授权。结果回调 [InstallResultReceiver]。
     */
    suspend fun install(file: File): Boolean = withContext(ioDispatcher) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                // FLAG_MUTABLE：系统安装器需向回传 intent 填充 EXTRA_STATUS，
                // immutable 会导致回调拿不到状态（Android 12+ 官方示例写法）
                val pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pendingIntent.intentSender)
            }
            true
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Session 安装调起失败", e)
            false
        }
    }

    /** 已下载 APK 的缓存路径（与 download 的落盘规则一致）。 */
    fun apkFile(info: UpdateInfo): File =
        File(context.cacheDir, "updates/mdot-${info.versionName}.apk")
}

@Serializable
data class UpdateInfo(
    val versionName: String = "",
    val versionCode: Int = 0,
    val tag: String = "",
    val releaseNotes: String = "",
    val apkUrl: ApkUrls = ApkUrls(),
    /** 三包 sha256（13 文档 B2-03 端到端完整性；旧 update.json 缺省=不校验，向后兼容） */
    val apkSha256: ApkSha256? = null,
)

@Serializable
data class ApkSha256(
    @SerialName("arm64-v8a") val arm64V8a: String? = null,
    @SerialName("armeabi-v7a") val armeabiV7a: String? = null,
    val universal: String? = null,
)

@Serializable
data class ApkUrls(
    @SerialName("arm64-v8a") val arm64V8a: String? = null,
    @SerialName("armeabi-v7a") val armeabiV7a: String? = null,
    val universal: String? = null,
)

/** 按设备 ABI 列表选择下载地址（纯函数）：arm64 优先，其次 armv7，兜底双架构包。 */
fun pickApkUrl(abis: Array<String>, urls: ApkUrls): String? = when {
    abis.contains("arm64-v8a") -> urls.arm64V8a
    abis.contains("armeabi-v7a") -> urls.armeabiV7a
    else -> urls.universal
} ?: urls.universal

/** 冷启动自动检查的节流间隔：距上次**成功**检查不足 24h 则跳过（纯函数，单测覆盖）。
 *  失败不落 lastCheckAt，故离线设备每个冷启动各试一次（单个静态 JSON，代价可忽略）。 */
const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

/** 是否该做冷启动自动检查（[lastCheckAt]=0 表示从未检查过 → 立即查；纯函数，单测覆盖）。
 *  [lastCheckAt] 落在未来（时钟回拨）视为异常 → 也查一次兜底，否则会永久跳过。 */
fun shouldAutoCheckUpdate(lastCheckAt: Long, now: Long): Boolean =
    lastCheckAt > now || now - lastCheckAt >= AUTO_CHECK_INTERVAL_MS
