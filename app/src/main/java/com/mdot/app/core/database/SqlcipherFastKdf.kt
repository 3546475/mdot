package com.mdot.app.core.database

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SqlcipherRekey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * SQLCipher 打开性能优化：本应用库密钥为 32B 随机值（256bit 高熵），
 * 本就不需要 PBKDF2 拉伸，故改用 raw key（`PRAGMA key = "x'hex'"` 字面量）直接作为加密密钥，
 * 开库零 KDF 派生开销（毫秒级），彻底消除默认 256000 次迭代带来的 ~0.5s 打开耗时。
 *
 * 迁移策略：旧库（byte[] 密钥走 PBKDF2 256000 派生）→ 以 byte[] 打开后
 * `PRAGMA rekey = "x'hex'"` 重加密为 raw key 落盘；之后所有打开均走 raw key。
 * 用 DataStore 标记区分是否已迁移。
 */
object SqlcipherFastKdf {
    private const val TAG = "SqlcipherFastKdf"

    private fun hexKey(passphrase: ByteArray): String =
        passphrase.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** 正常打开：postKey 在 key 之后用 raw key 覆盖，消除 PBKDF2 派生 */
    private fun rawKeyHook(passphrase: ByteArray): SQLiteDatabaseHook = object : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection) {}

        override fun postKey(connection: SQLiteConnection) {
            connection.executeRaw("PRAGMA key = \"x'${hexKey(passphrase)}'\";", null, null)
        }
    }

    /** 回退路径：byte[] 密钥（PBKDF2 默认 256000），迁移失败时保底可用 */
    private fun plainHook(): SQLiteDatabaseHook = object : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection) {}
        override fun postKey(connection: SQLiteConnection) {}
    }

    fun factory(passphrase: ByteArray, rawKey: Boolean): SupportOpenHelperFactory =
        SupportOpenHelperFactory(
            passphrase,
            if (rawKey) rawKeyHook(passphrase) else plainHook(),
            true,
        )

    /**
     * 确保库文件为 raw key（若需要则迁移）。
     * - 库文件不存在（新装）：直接 true，Room 以 raw key 创建。
     * - 已迁移：由调用方标记位规避，不会走到这里。
     * - 旧库（PBKDF2 256000）：以 byte[] 打开 → rekey 到 raw key → 关闭。
     *   迁移失败（异常）返回 false，Room 回退 byte[] 打开，保证不丢数据。
     */
    fun ensureRawKey(context: Context, dbName: String, passphrase: ByteArray): Boolean {
        return try {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                Log.i(TAG, "新库：以 raw key 创建")
                return true
            }
            val t0 = System.currentTimeMillis()
            migrateExisting(dbFile, passphrase)
            Log.i(TAG, "旧库已迁移到 raw key（${System.currentTimeMillis() - t0}ms）")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "raw key 迁移失败，回退 byte[] 密钥：${t.message}")
            false
        }
    }

    /** 用 byte[] 打开旧库，rekey 到 raw key 并关闭 */
    private fun migrateExisting(dbFile: File, passphrase: ByteArray) {
        var conn: SQLiteConnection? = null
        val hook = object : SQLiteDatabaseHook {
            override fun preKey(connection: SQLiteConnection) {}
            override fun postKey(connection: SQLiteConnection) {
                conn = connection
            }
        }
        val db = SQLiteDatabase.openOrCreateDatabase(
            dbFile.absolutePath, passphrase, null, null, hook,
        )
        try {
            SqlcipherRekey.rekeyToRaw(requireNotNull(conn), passphrase)
        } finally {
            db.close()
        }
    }
}
