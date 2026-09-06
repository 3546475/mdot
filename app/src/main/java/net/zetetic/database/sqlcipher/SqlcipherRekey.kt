package net.zetetic.database.sqlcipher

/**
 * 与 sqlcipher-android 同包，以便在已打开的连接上执行真正的 rekey。
 *
 * 背景：库密钥为 32B 随机值，改用 raw key（`PRAGMA key/rekey = "x'hex'"`）直接作加密密钥，
 * 跳过 PBKDF2 派生。`PRAGMA rekey` 会逐页重加密并落盘，是官方支持的改密方式；
 * 而公开的 [SQLiteDatabase.changePassword] 只 reconfigure 连接池、不落盘重加密。
 */
object SqlcipherRekey {

    /** 在已打开连接上 rekey 到 raw key（与 hexKey 相同的 32B 密钥，逐页重加密落盘）。 */
    fun rekeyToRaw(connection: SQLiteConnection, passphrase: ByteArray) {
        val hex = passphrase.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        connection.executeRaw("PRAGMA rekey = \"x'$hex'\";", null, null)
    }
}
