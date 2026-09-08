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
import com.mdot.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val TAG = "UpdateRepository"
    }

    /** 检查更新：有新版本返回 [UpdateInfo]，已是最新返回 null；网络/解析失败返回 Failure。 */
    suspend fun check(): AppResult<UpdateInfo?> = try {
        val url = settings.updateUrlFlow.first()
        val info = json.decodeFromString<UpdateInfo>(jsonFetcher.fetchText(url))
        if (info.versionCode > BuildConfig.VERSION_CODE) Success(info) else Success(null)
    } catch (e: Exception) {
        Failure(AppError.Storage(e.message ?: "检查更新失败"))
    }

    /** 按设备 ABI 选择下载地址：arm64 优先，其次 armv7，兜底双架构包。 */
    fun pickApkUrl(info: UpdateInfo): String? =
        pickApkUrl(Build.SUPPORTED_ABIS ?: emptyArray(), info.apkUrl)

    /**
     * 下载 APK 到缓存目录（同名已存在则直接复用），[onProgress] 回调 0–100。
     * 下载走独立调用（流式写盘），不复用 JSON 的重试策略。
     */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): AppResult<File> =
        withContext(ioDispatcher) {
            val url = pickApkUrl(info)
                ?: return@withContext Failure(AppError.Storage("没有可用的下载地址"))
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "mdot-${info.versionName}.apk")
            // 只信任完整且有效的缓存文件；无效残留直接删除重下
            if (target.exists() && target.length() > 0 && isCompleteZip(target)) {
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
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(part).use { out ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
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
                    if (!part.renameTo(target)) {
                        return@withContext Failure(AppError.Storage("下载失败：缓存写入失败"))
                    }
                    onProgress(100)
                    Success(target)
                }
            } catch (e: Exception) {
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
