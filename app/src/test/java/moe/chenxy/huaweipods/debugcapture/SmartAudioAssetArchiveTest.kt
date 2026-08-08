package moe.chenxy.huaweipods.debugcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SmartAudioAssetArchiveTest {
    @Test
    fun inspect_acceptsArchiveContainingPngAssets() {
        val archive = createArchive(
            "000141_01/iv_device_logo.png" to byteArrayOf(1, 2, 3),
            "000141_01/config.json" to "{}".toByteArray(),
        )

        val result = SmartAudioAssetArchive.inspect(archive)

        assertEquals(2, result.entryCount)
        assertEquals(1, result.pngEntryCount)
        assertEquals(listOf("000141_01/iv_device_logo.png"), result.mainImageEntries)
        assertTrue(result.leftImageEntries.isEmpty())
        assertTrue(result.rightImageEntries.isEmpty())
        assertEquals(64, result.sha256.length)
        assertTrue(result.bytes > 0L)
    }

    @Test
    fun inspect_classifiesDedicatedLeftAndRightImages() {
        val archive = createArchive(
            "000141_01/iv_device_logo.png" to byteArrayOf(1),
            "000141_01/find_left_icon.png" to byteArrayOf(2),
            "000141_01/find_right_icon.png" to byteArrayOf(3),
        )

        val result = SmartAudioAssetArchive.inspect(archive)

        assertEquals(listOf("000141_01/iv_device_logo.png"), result.mainImageEntries)
        assertEquals(listOf("000141_01/find_left_icon.png"), result.leftImageEntries)
        assertEquals(listOf("000141_01/find_right_icon.png"), result.rightImageEntries)
    }

    @Test
    fun inspect_rejectsArchiveWithoutPngAssets() {
        val archive = createArchive("000141_01/config.json" to "{}".toByteArray())

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SmartAudioAssetArchive.inspect(archive)
        }

        assertTrue(failure.message.orEmpty().contains("PNG"))
    }

    @Test
    fun inspect_rejectsUnrelatedPngArchive() {
        val archive = createArchive("screenshots/random.png" to byteArrayOf(1))

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SmartAudioAssetArchive.inspect(archive)
        }

        assertTrue(failure.message.orEmpty().contains("机型主图"))
    }

    @Test
    fun inspect_rejectsTraversalEntry() {
        val archive = createArchive("../iv_device_logo.png" to byteArrayOf(1))

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SmartAudioAssetArchive.inspect(archive)
        }

        assertTrue(failure.message.orEmpty().contains("不安全路径"))
    }

    private fun createArchive(vararg entries: Pair<String, ByteArray>): File {
        val directory = Files.createTempDirectory("huaweipods-assets-test").toFile()
        val archive = File(directory, "assets.zip")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        archive.deleteOnExit()
        directory.deleteOnExit()
        return archive
    }
}
