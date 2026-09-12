package com.mdot.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.HomeCardsConfig
import com.mdot.app.domain.model.SalaryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore 封装（08 文档 §4 键清单）。
 * 结构化对象用 kotlinx.serialization 存 JSON 字符串。
 */
@Singleton
class SettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private inline fun <reified T> decode(text: String?, default: T): T =
        if (text == null) default else runCatching { json.decodeFromString<T>(text) }.getOrDefault(default)

    // ---- 工资 ----
    val salaryFlow: Flow<SalaryConfig> = dataStore.data.map { decode(it[SALARY], SalaryConfig()) }

    suspend fun setSalary(config: SalaryConfig) = dataStore.edit { it[SALARY] = json.encodeToString(config) }

    // ---- 考勤周期 ----
    val cycleAnchorDayFlow: Flow<Int> = dataStore.data.map { it[CYCLE_ANCHOR_DAY] ?: 1 }

    suspend fun setCycleAnchorDay(day: Int) {
        require(day in 1..31)
        dataStore.edit { it[CYCLE_ANCHOR_DAY] = day }
    }

    // ---- 工作日设定 ----
    val workdaysFlow: Flow<Set<DayOfWeek>> = dataStore.data.map { prefs ->
        val names = prefs[WORKDAYS]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: DEFAULT_WORKDAYS
        names.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
    }

    suspend fun setWorkdays(days: Set<DayOfWeek>) =
        dataStore.edit { it[WORKDAYS] = json.encodeToString(days.map { it.name }.sorted()) }

    // ---- 外观 ----
    val appearanceFlow: Flow<AppearanceConfig> =
        dataStore.data.map { decode(it[APPEARANCE], AppearanceConfig()) }

    suspend fun setAppearance(config: AppearanceConfig) =
        dataStore.edit { it[APPEARANCE] = json.encodeToString(config) }

    // ---- 底栏 ----
    val bottomBarFlow: Flow<BottomBarConfig> =
        dataStore.data.map { decode(it[BOTTOM_BAR], BottomBarConfig()) }

    suspend fun setBottomBar(config: BottomBarConfig) =
        dataStore.edit { it[BOTTOM_BAR] = json.encodeToString(config) }

    // ---- 首页卡片（v0.6.0 首页卡片可编辑；cards=null=未配置走默认） ----
    val homeCardsFlow: Flow<HomeCardsConfig> =
        dataStore.data.map { decode(it[HOME_CARDS], HomeCardsConfig()) }

    suspend fun setHomeCards(config: HomeCardsConfig) =
        dataStore.edit { it[HOME_CARDS] = json.encodeToString(config) }

    // ---- 引导 ----
    val firstLaunchDoneFlow: Flow<Boolean> = dataStore.data.map { it[FIRST_LAUNCH_DONE] ?: false }

    suspend fun setFirstLaunchDone() = dataStore.edit { it[FIRST_LAUNCH_DONE] = true }

    // ---- 数据源 URL ----
    val updateUrlFlow: Flow<String> = dataStore.data.map { it[UPDATE_URL] ?: DEFAULT_UPDATE_URL }
    val holidayUrlFlow: Flow<String> = dataStore.data.map { it[HOLIDAY_URL] ?: DEFAULT_HOLIDAY_URL }

    suspend fun setUpdateUrl(url: String) = dataStore.edit { it[UPDATE_URL] = url }
    suspend fun setHolidayUrl(url: String) = dataStore.edit { it[HOLIDAY_URL] = url }

    // ---- 更新与数据源：URL 多选一列表（JSON 数组存储；空 = 未配置，UI 以单值兜底） ----
    val updateUrlsFlow: Flow<List<String>> = dataStore.data.map { decode(it[UPDATE_URLS], emptyList()) }
    val holidayUrlsFlow: Flow<List<String>> = dataStore.data.map { decode(it[HOLIDAY_URLS], emptyList()) }
    suspend fun setUpdateUrls(urls: List<String>) =
        dataStore.edit { it[UPDATE_URLS] = json.encodeToString(urls.filter { u -> u.isNotBlank() }) }
    suspend fun setHolidayUrls(urls: List<String>) =
        dataStore.edit { it[HOLIDAY_URLS] = json.encodeToString(urls.filter { u -> u.isNotBlank() }) }

    /** 节假日库最近一次成功拉取时间（毫秒；自动刷新节流用，失败按 1 天短节流回退） */
    val holidayLastFetchAtFlow: Flow<Long> = dataStore.data.map { it[HOLIDAY_LAST_FETCH_AT] ?: 0L }

    suspend fun setHolidayLastFetchAt(ts: Long) = dataStore.edit { it[HOLIDAY_LAST_FETCH_AT] = ts }

    val lastUpdateCheckAtFlow: Flow<Long> = dataStore.data.map { it[LAST_UPDATE_CHECK_AT] ?: 0 }
    suspend fun setLastUpdateCheckAt(at: Long) = dataStore.edit { it[LAST_UPDATE_CHECK_AT] = at }

    // ---- 同步 ----
    val lastBackupAtFlow: Flow<Long> = dataStore.data.map { it[LAST_BACKUP_AT] ?: 0 }
    suspend fun setLastBackupAt(at: Long) = dataStore.edit { it[LAST_BACKUP_AT] = at }

    val autoBackupEnabledFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_BACKUP_ENABLED] ?: true }
    suspend fun setAutoBackupEnabled(enabled: Boolean) =
        dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled }

    val historyCopyEnabledFlow: Flow<Boolean> = dataStore.data.map { it[HISTORY_COPY_ENABLED] ?: true }
    suspend fun setHistoryCopyEnabled(enabled: Boolean) =
        dataStore.edit { it[HISTORY_COPY_ENABLED] = enabled }

    val lastLocalChangeAtFlow: Flow<Long> = dataStore.data.map { it[LAST_LOCAL_CHANGE_AT] ?: 0 }

    /** 任何业务写入后调用：本地脏标记 */
    suspend fun touch() = dataStore.edit { it[LAST_LOCAL_CHANGE_AT] = System.currentTimeMillis() }

    val lastSyncedRemoteCreatedAtFlow: Flow<String?> =
        dataStore.data.map { it[LAST_SYNCED_REMOTE_CREATED_AT] }

    suspend fun setLastSyncedRemoteCreatedAt(value: String?) =
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(LAST_SYNCED_REMOTE_CREATED_AT)
            else prefs[LAST_SYNCED_REMOTE_CREATED_AT] = value
        }

    // ---- 凭据密文（Keystore 加密后的 Base64，随包排除） ----
    val webdavCredCipherFlow: Flow<String?> = dataStore.data.map { it[WEBDAV_CRED_CIPHER] }
    val s3CredCipherFlow: Flow<String?> = dataStore.data.map { it[S3_CRED_CIPHER] }

    suspend fun setWebdavCredCipher(cipher: String?) = dataStore.edit { prefs ->
        if (cipher == null) prefs.remove(WEBDAV_CRED_CIPHER) else prefs[WEBDAV_CRED_CIPHER] = cipher
    }

    suspend fun setS3CredCipher(cipher: String?) = dataStore.edit { prefs ->
        if (cipher == null) prefs.remove(S3_CRED_CIPHER) else prefs[S3_CRED_CIPHER] = cipher
    }

    // ---- 同步小状态（ETag 等） ----
    val lastEtagFlow: Flow<String?> = dataStore.data.map { it[LAST_ETAG] }
    suspend fun setLastEtag(value: String?) = dataStore.edit { prefs ->
        if (value == null) prefs.remove(LAST_ETAG) else prefs[LAST_ETAG] = value
    }

    // ---- 我的页资料（昵称 / 自定义头像路径） ----
    val nicknameFlow: Flow<String> = dataStore.data.map { it[NICKNAME] ?: DEFAULT_NICKNAME }
    suspend fun setNickname(value: String) = dataStore.edit { prefs ->
        val v = value.trim()
        prefs[NICKNAME] = v.ifEmpty { DEFAULT_NICKNAME }
    }

    val avatarPathFlow: Flow<String?> = dataStore.data.map { it[AVATAR_PATH] }
    suspend fun setAvatarPath(value: String?) = dataStore.edit { prefs ->
        if (value == null) prefs.remove(AVATAR_PATH) else prefs[AVATAR_PATH] = value
    }

    // ---- 底栏仅图标模式 ----
    val bottomBarIconOnlyFlow: Flow<Boolean> = dataStore.data.map { it[BOTTOM_BAR_ICON_ONLY] ?: false }
    suspend fun setBottomBarIconOnly(value: Boolean) =
        dataStore.edit { it[BOTTOM_BAR_ICON_ONLY] = value }

    // ---- SQLCipher raw key 迁移标记（旧库完成一次 byte[]→raw key 的 rekey 后置 true；
    //       新装/空库直接置 true）。未置 true 时按 byte[]（PBKDF2）打开，可正常打开旧库。 ----
    val dbFastKdfFlow: Flow<Boolean> = dataStore.data.map { it[DB_FAST_KDF] ?: false }
    suspend fun setDbFastKdf(value: Boolean) = dataStore.edit { it[DB_FAST_KDF] = value }

    // ---- 内部 ----
    companion object {
        val DEFAULT_WORKDAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
        /** App 默认更新源 = CNB raw（国内可达性优先；GitHub Pages 作备用候选） */
        const val DEFAULT_UPDATE_URL =
            "https://cnb.cool/nulxiel/mdot/-/git/raw/main/update.json"
        /** 节假日库默认同走 CNB raw，GitHub Pages 作备用候选 */
        const val DEFAULT_HOLIDAY_URL =
            "https://cnb.cool/nulxiel/mdot/-/git/raw/main/holidays.json"

        /** 出厂内置候选源（更新 JSON / 节假日 JSON 各两条：CNB raw 默认 + GitHub Pages 备用，多选一切换用；
         *  两条必须不同——UrlPicker 按 url==selected 点亮，重复条目会同时高亮） */
        val DEFAULT_UPDATE_URLS = listOf(
            DEFAULT_UPDATE_URL,
            "https://3546475.github.io/mdot/update.json",
        )
        val DEFAULT_HOLIDAY_URLS = listOf(
            DEFAULT_HOLIDAY_URL,
            "https://3546475.github.io/mdot/holidays.json",
        )

        private val SALARY = stringPreferencesKey("salary_config")
        private val CYCLE_ANCHOR_DAY = intPreferencesKey("cycle_anchor_day")
        private val WORKDAYS = stringPreferencesKey("workdays")
        private val APPEARANCE = stringPreferencesKey("appearance")
        private val BOTTOM_BAR = stringPreferencesKey("bottom_bar_slots")
        private val HOME_CARDS = stringPreferencesKey("home_cards")
        private val FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
        private val UPDATE_URL = stringPreferencesKey("update_url")
        private val HOLIDAY_URL = stringPreferencesKey("holiday_url")
        private val UPDATE_URLS = stringPreferencesKey("update_urls")
        private val HOLIDAY_URLS = stringPreferencesKey("holiday_urls")
        private val HOLIDAY_LAST_FETCH_AT = longPreferencesKey("holiday_last_fetch_at")
        private val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        private val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val HISTORY_COPY_ENABLED = booleanPreferencesKey("history_copy_enabled")
        private val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        private val LAST_LOCAL_CHANGE_AT = longPreferencesKey("last_local_change_at")
        private val LAST_SYNCED_REMOTE_CREATED_AT = stringPreferencesKey("last_synced_remote_created_at")
        private val WEBDAV_CRED_CIPHER = stringPreferencesKey("webdav_cred_cipher")
        private val S3_CRED_CIPHER = stringPreferencesKey("s3_cred_cipher")
        private val LAST_ETAG = stringPreferencesKey("last_etag")
        private val DB_FAST_KDF = booleanPreferencesKey("db_fast_kdf")
        private val NICKNAME = stringPreferencesKey("nickname")
        private val AVATAR_PATH = stringPreferencesKey("avatar_path")
        private val BOTTOM_BAR_ICON_ONLY = booleanPreferencesKey("bottom_bar_icon_only")

        const val DEFAULT_NICKNAME = "即兴生长"
    }
}
