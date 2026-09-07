package com.mdot.app.core.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 更新逻辑纯函数测试：
 * - update.json 解析（字段名/SerialName 映射与发布工作流生成的结构一致）
 * - pickApkUrl：按设备 ABI 选包，arm64 优先、armv7 次之、其余兜底双架构包，缺地址回退
 */
class UpdateInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sampleJson = """
        {
          "versionName": "0.5.2",
          "versionCode": 19,
          "tag": "v0.5.2",
          "releaseNotes": "马的加班 v0.5.2 已发布。",
          "apkUrl": {
            "arm64-v8a": "https://example.com/v8a.apk",
            "armeabi-v7a": "https://example.com/v7a.apk",
            "universal": "https://example.com/dual.apk"
          }
        }
    """.trimIndent()

    @Test
    fun parses_update_json_with_abi_keys() {
        val info = json.decodeFromString<UpdateInfo>(sampleJson)
        assertEquals("0.5.2", info.versionName)
        assertEquals(19, info.versionCode)
        assertEquals("v0.5.2", info.tag)
        assertEquals("https://example.com/v8a.apk", info.apkUrl.arm64V8a)
        assertEquals("https://example.com/v7a.apk", info.apkUrl.armeabiV7a)
        assertEquals("https://example.com/dual.apk", info.apkUrl.universal)
    }

    @Test
    fun missing_abi_urls_default_to_null() {
        val info = json.decodeFromString<UpdateInfo>("""{"versionName":"1.0","versionCode":1}""")
        assertNull(info.apkUrl.arm64V8a)
        assertNull(info.apkUrl.armeabiV7a)
        assertNull(info.apkUrl.universal)
    }

    @Test
    fun arm64_device_prefers_v8a() {
        val info = json.decodeFromString<UpdateInfo>(sampleJson)
        val url = pickApkUrl(arrayOf("arm64-v8a", "armeabi-v7a"), info.apkUrl)
        assertEquals("https://example.com/v8a.apk", url)
    }

    @Test
    fun armv7_only_device_picks_v7a() {
        val info = json.decodeFromString<UpdateInfo>(sampleJson)
        val url = pickApkUrl(arrayOf("armeabi-v7a"), info.apkUrl)
        assertEquals("https://example.com/v7a.apk", url)
    }

    @Test
    fun x86_device_falls_back_to_universal() {
        val info = json.decodeFromString<UpdateInfo>(sampleJson)
        val url = pickApkUrl(arrayOf("x86_64"), info.apkUrl)
        assertEquals("https://example.com/dual.apk", url)
    }

    @Test
    fun preferred_abi_missing_url_falls_back_to_universal() {
        // arm64 设备但发布说明里缺 v8a 地址 → 回退双架构包
        val info = json.decodeFromString<UpdateInfo>(
            """{"apkUrl":{"armeabi-v7a":"https://example.com/v7a.apk","universal":"https://example.com/dual.apk"}}""",
        )
        val url = pickApkUrl(arrayOf("arm64-v8a"), info.apkUrl)
        assertEquals("https://example.com/dual.apk", url)
    }
}
