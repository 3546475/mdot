package com.mdot.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
            try {
                val url = pickApkUrl(info)
                    ?: return@withContext Failure(AppError.Storage("没有可用的下载地址"))
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "mdot-${info.versionName}.apk")
                if (target.exists() && target.length() > 0) {
                    onProgress(100)
                    return@withContext Success(target)
                }
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Failure(AppError.Storage("下载失败：HTTP ${resp.code}"))
                    }
                    val body = resp.body ?: return@withContext Failure(AppError.Storage("下载失败：空响应"))
                    val total = body.contentLength()
                    var sent = 0L
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(target).use { out ->
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
                }
                onProgress(100)
                Success(target)
            } catch (e: Exception) {
                Failure(AppError.Storage(e.message ?: "下载失败"))
            }
        }

    /** 是否被允许安装未知来源应用（Android 8+ 需用户授权）。 */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 调起系统安装器安装已下载的 APK。 */
    fun install(file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    /** 跳转到「允许安装未知应用」系统设置页。 */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
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
