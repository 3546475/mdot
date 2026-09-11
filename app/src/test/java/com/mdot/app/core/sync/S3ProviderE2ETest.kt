package com.mdot.app.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.SecureRandom

/**
 * S3Provider 真实服务端 E2E（0.6.3：S3/MinIO 全链路）。
 *
 * 凭据从 gitignored 的 `docs/test-s3.local.properties` 读取（仓库根或 app/ 工作目录），
 * 文件缺失时整个测试类自动跳过（不影响离线单测全绿）。运行前确保服务端局域网可达：
 * ```
 * ./gradlew.bat testDebugUnitTest --tests "*S3ProviderE2ETest*"
 * ```
 * 覆盖：ensureBaseDir 探测 / put / head / get 往返（含 1MB 大载荷）/ list / delete（含 404 幂等）/ 403 错误映射。
 * 全部对象写入独立 `mdot-e2e/<时间戳>/` 前缀（不碰真实备份目录 mdot/backup/），结束时尽力清理。
 */
class S3ProviderE2ETest {

    private val config: S3Config? by lazy {
        val candidates = listOf(File("docs/test-s3.local.properties"), File("../docs/test-s3.local.properties"))
        val file = candidates.firstOrNull { it.exists() } ?: return@lazy null
        runCatching {
            val p = java.util.Properties().apply { file.inputStream().use { load(it) } }
            S3Config(
                endpoint = p.getProperty("endpoint").trim(),
                bucket = p.getProperty("bucket").trim(),
                region = p.getProperty("region", "us-east-1").trim(),
                accessKeyId = p.getProperty("accessKey").trim(),
                secretAccessKey = p.getProperty("secretKey").trim(),
                trustSelfSigned = p.getProperty("trustSelfSigned", "false").trim().toBoolean(),
            )
        }.getOrNull()
    }

    private fun provider(cfg: S3Config): S3Provider =
        S3Provider(OkHttpClient(), Dispatchers.IO).configure(cfg)

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }

    @Test
    fun e2e_fullFlow() = runBlocking {
        val cfg = config; assumeTrue("需要 docs/test-s3.local.properties（见测试类注释）", cfg != null)
        // 独立前缀隔离：失败残留不污染真实备份目录
        val prefix = "mdot-e2e/${System.currentTimeMillis()}"
        val s3 = provider(cfg!!.copy(pathPrefix = prefix))
        val smallKey = "small.bin"
        val bigKey = "big-1mb.bin"
        val small = randomBytes(64 * 1024)
        val big = randomBytes(1024 * 1024 + 7)

        // 1. 连接检测（ListObjectsV2 max-keys=1，桶级路径）
        s3.ensureBaseDir().getOrThrow()

        // 2. put + head
        val put = s3.put(smallKey, small).getOrThrow()
        assertTrue("etag 应存在", !put.etag.isNullOrBlank())
        val meta = s3.head(smallKey).getOrThrow()
        assertTrue("head 应命中", meta != null)
        assertEquals(small.size.toLong(), meta!!.size)
        assertEquals(put.etag, meta.etag)

        // 3. get 往返（小 + 1MB 大载荷）
        assertArrayEquals(small, s3.get(smallKey).getOrThrow())
        s3.put(bigKey, big).getOrThrow()
        assertArrayEquals(big, s3.get(bigKey).getOrThrow())

        // 4. list：前缀下列出两个对象，路径含完整前缀
        val listed = s3.list("").getOrThrow()
        assertEquals(2, listed.size)
        assertTrue(listed.any { it.path.endsWith("/$smallKey") })
        assertTrue(listed.any { it.path.endsWith("/$bigKey") })

        // 5. get/head 404 → null
        assertNull(s3.get("missing.bin").getOrThrow())
        assertNull(s3.head("missing.bin").getOrThrow())

        // 6. delete + 404 幂等
        s3.delete(smallKey).getOrThrow()
        assertNull(s3.head(smallKey).getOrThrow())
        s3.delete(smallKey).getOrThrow() // 再次删除不报错

        // 清理剩余对象（best effort）
        s3.list("").getOrThrow().forEach { s3.delete(it.path) }
    }

    /** 403 错误映射：错误密钥 → 可读文案（而非裸 HTTP 403） */
    @Test
    fun e2e_wrongSecret_mapsToReadableError() = runBlocking {
        val cfg = config; assumeTrue("需要 docs/test-s3.local.properties（见测试类注释）", cfg != null)
        val bad = cfg!!.copy(secretAccessKey = cfg.secretAccessKey + "x")
        val result = provider(bad).ensureBaseDir()
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()!!.message.orEmpty()
        assertTrue("错误文案应可读，实际：$msg", msg.contains("密钥"))
    }
}
