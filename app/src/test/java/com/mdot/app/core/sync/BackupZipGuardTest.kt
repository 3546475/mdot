package com.mdot.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.datastore.SettingsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 备份解包资源护栏单测（13 文档 B2-05）：
 * 包体/单条目/条目数超限时拒绝并给出可读错误，而非 OOM。
 * unzip 是纯解包+校验路径（不触库），依赖仅为满足构造类型。
 */
@RunWith(RobolectricTestRunner::class)
class BackupZipGuardTest {

    private lateinit var codec: BackupCodec

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val settings = SettingsDataSource(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { java.io.File(context.cacheDir, "t_${System.nanoTime()}.preferences_pb") },
            )
        )
        codec = BackupCodec(db = db, settings = settings)
    }

    @Test
    fun `包体超过64MB被拒`() {
        val huge = ByteArray(64 * 1024 * 1024 + 1) { 0 }
        try {
            codec.unzip(huge)
            fail("超限包应被拒")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("过大"))
        }
    }

    @Test
    fun `条目数超过16被拒`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            repeat(20) { i ->
                zos.putNextEntry(ZipEntry("junk$i.bin"))
                zos.write(ByteArray(16))
                zos.closeEntry()
            }
        }
        try {
            codec.unzip(out.toByteArray())
            fail("条目超限应被拒")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("条目数"))
        }
    }

    @Test
    fun `单条目超过32MB被拒`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("big.bin"))
            zos.write(ByteArray(32 * 1024 * 1024 + 1))
            zos.closeEntry()
        }
        try {
            codec.unzip(out.toByteArray())
            fail("单条目超限应被拒")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("过大"))
        }
    }

    @Test
    fun `正常结构的小包走格式校验而非护栏`() {
        // 无 manifest → 报"缺少 manifest.json"（护栏放行、结构校验接住）
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("whatever.bin"))
            zos.write(ByteArray(16))
            zos.closeEntry()
        }
        try {
            codec.unzip(out.toByteArray())
            fail("缺 manifest 应被拒")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("manifest"))
        }
    }
}
