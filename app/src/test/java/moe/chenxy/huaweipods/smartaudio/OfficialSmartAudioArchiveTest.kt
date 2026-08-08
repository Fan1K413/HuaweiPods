package moe.chenxy.huaweipods.smartaudio

import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfficialSmartAudioArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `extracts a validated main image without inventing side images`() {
        val archive = zipOf(
            "000027_00/iv_device_logo.png" to validPng,
            "000027_00/readme.txt" to "official".toByteArray(),
        )
        val extracted = OfficialSmartAudioArchive.extract(
            archive,
            temporaryFolder.newFolder("out"),
        )

        assertTrue(extracted.box.isFile)
        assertEquals(validPng.toList(), extracted.box.readBytes().toList())
        assertNull(extracted.left)
        assertNull(extracted.right)
    }

    @Test
    fun `rejects traversal and malformed png files`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialSmartAudioArchive.extract(
                zipOf("../iv_device_logo.png" to validPng),
                temporaryFolder.newFolder("traversal"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OfficialSmartAudioArchive.extract(
                zipOf("iv_device_logo.png" to validPng.copyOfRange(0, validPng.size - 1)),
                temporaryFolder.newFolder("invalid"),
            )
        }
    }

    @Test
    fun `uses side images only as a complete pair`() {
        val oneSide = OfficialSmartAudioArchive.extract(
            zipOf(
                "iv_device_logo.png" to validPng,
                "find_left_icon.png" to validPng,
            ),
            temporaryFolder.newFolder("one-side"),
        )
        assertNull(oneSide.left)
        assertNull(oneSide.right)

        val pair = OfficialSmartAudioArchive.extract(
            zipOf(
                "iv_device_logo.png" to validPng,
                "find_left_icon.png" to validPng,
                "find_right_icon.png" to validPng,
            ),
            temporaryFolder.newFolder("pair"),
        )
        assertTrue(pair.left?.isFile == true)
        assertTrue(pair.right?.isFile == true)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val file = temporaryFolder.newFile("archive-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    private val validPng = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAANSURBVBhXY/jPwPAfAAUAAf+mXJtdAAAAAElFTkSuQmCC",
    )
}
