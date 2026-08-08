package moe.chenxy.huaweipods.smartaudio

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class ExtractedSmartAudioImages(
    val box: File,
    val left: File?,
    val right: File?,
)

/** 只从经校验的官方 ZIP 中提取已知名称 PNG，不展开任意路径。 */
internal object OfficialSmartAudioArchive {
    private const val MAX_ENTRY_COUNT = 2_048
    private const val MAX_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L
    private const val MAX_PNG_BYTES = 16L * 1024L * 1024L
    private const val MAX_PNG_DIMENSION = 4_096
    private const val MAX_PNG_PIXELS = 16_777_216L

    private val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val drivePrefix = Regex("^[A-Za-z]:/.*")
    private val boxNames = listOf(
        "iv_device_logo.png",
        "device_icon.png",
        "mediacontroller_icon.png",
        "ota_device_icon.png",
        "ota_device.png",
    )
    private val leftNames = listOf(
        "find_left_icon.png",
        "find_left.png",
        "left_ear.png",
        "left_ear_icon.png",
    )
    private val rightNames = listOf(
        "find_right_icon.png",
        "find_right.png",
        "right_ear.png",
        "right_ear_icon.png",
    )

    fun extract(archiveFile: File, outputDirectory: File): ExtractedSmartAudioImages {
        require(archiveFile.isFile && archiveFile.length() > 0L) { "官方资源包为空" }
        require(archiveFile.length() <= OfficialSmartAudioResource.MAX_ARCHIVE_BYTES) {
            "官方资源包超过大小上限"
        }
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) { "无法创建图片临时目录" }

        ZipFile(archiveFile).use { archive ->
            val entries = mutableListOf<ZipEntry>()
            var declaredBytes = 0L
            val iterator = archive.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                require(entries.size < MAX_ENTRY_COUNT) { "官方资源包文件数量过多" }
                require(isSafeEntryName(entry.name)) { "官方资源包包含不安全路径" }
                if (!entry.isDirectory) {
                    require(entry.size in 0..MAX_UNCOMPRESSED_BYTES) { "官方资源包包含未知或过大文件" }
                    require(entry.size <= MAX_UNCOMPRESSED_BYTES - declaredBytes) {
                        "官方资源包解压后体积过大"
                    }
                    declaredBytes += entry.size
                }
                entries += entry
            }
            require(entries.isNotEmpty()) { "官方资源包内没有文件" }

            val boxEntry = selectEntry(entries, boxNames)
                ?: error("官方资源包内没有可用耳机主图")
            val leftEntry = selectEntry(entries, leftNames)
            val rightEntry = selectEntry(entries, rightNames)
            val sidePair = if (leftEntry != null && rightEntry != null) {
                leftEntry to rightEntry
            } else {
                null
            }
            return ExtractedSmartAudioImages(
                box = extractPng(archive, boxEntry, File(outputDirectory, "box.png")),
                left = sidePair?.first?.let {
                    extractPng(archive, it, File(outputDirectory, "left.png"))
                },
                right = sidePair?.second?.let {
                    extractPng(archive, it, File(outputDirectory, "right.png"))
                },
            )
        }
    }

    internal fun isSafeEntryName(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.isNotBlank() &&
            !normalized.startsWith('/') &&
            !drivePrefix.matches(normalized) &&
            normalized.split('/').none { it == ".." }
    }

    internal fun validatePng(bytes: ByteArray): Boolean = runCatching {
        require(bytes.size >= 8 + 12 + 13 + 12 && bytes.size <= MAX_PNG_BYTES)
        require(bytes.copyOfRange(0, pngSignature.size).contentEquals(pngSignature))
        var offset = pngSignature.size
        var sawHeader = false
        var sawImageData = false
        var sawEnd = false
        while (offset < bytes.size) {
            require(bytes.size - offset >= 12)
            val length = readUInt32(bytes, offset)
            require(length in 0..MAX_PNG_BYTES)
            val dataLength = length.toInt()
            val chunkEnd = offset + 12L + dataLength
            require(chunkEnd <= bytes.size)
            val typeOffset = offset + 4
            val type = bytes.copyOfRange(typeOffset, typeOffset + 4).toString(Charsets.US_ASCII)
            require(type.all { it in 'A'..'Z' || it in 'a'..'z' })
            val expectedCrc = readUInt32(bytes, typeOffset + 4 + dataLength)
            val crc = CRC32().apply { update(bytes, typeOffset, 4 + dataLength) }.value
            require(crc == expectedCrc)
            when (type) {
                "IHDR" -> {
                    require(!sawHeader && offset == pngSignature.size && dataLength == 13)
                    val width = readUInt32(bytes, typeOffset + 4)
                    val height = readUInt32(bytes, typeOffset + 8)
                    require(width in 1..MAX_PNG_DIMENSION.toLong())
                    require(height in 1..MAX_PNG_DIMENSION.toLong())
                    require(width * height <= MAX_PNG_PIXELS)
                    val bitDepth = bytes[typeOffset + 12].toInt() and 0xFF
                    val colorType = bytes[typeOffset + 13].toInt() and 0xFF
                    require(
                        when (colorType) {
                            0 -> bitDepth in setOf(1, 2, 4, 8, 16)
                            2, 4, 6 -> bitDepth in setOf(8, 16)
                            3 -> bitDepth in setOf(1, 2, 4, 8)
                            else -> false
                        },
                    )
                    require(bytes[typeOffset + 14].toInt() == 0)
                    require(bytes[typeOffset + 15].toInt() == 0)
                    require(bytes[typeOffset + 16].toInt() in 0..1)
                    sawHeader = true
                }
                "IDAT" -> {
                    require(sawHeader && !sawEnd)
                    sawImageData = true
                }
                "IEND" -> {
                    require(sawHeader && sawImageData && !sawEnd && dataLength == 0)
                    sawEnd = true
                    require(chunkEnd == bytes.size.toLong())
                }
            }
            offset = chunkEnd.toInt()
        }
        sawHeader && sawImageData && sawEnd
    }.getOrDefault(false)

    private fun selectEntry(entries: List<ZipEntry>, preferredNames: List<String>): ZipEntry? {
        val candidates = entries.filterNot(ZipEntry::isDirectory).mapNotNull { entry ->
            val fileName = entry.name.replace('\\', '/').substringAfterLast('/').lowercase(Locale.US)
            preferredNames.indexOf(fileName).takeIf { it >= 0 }?.let { priority ->
                Triple(entry, priority, entry.name.length)
            }
        }
        return candidates.minWithOrNull(
            compareBy<Triple<ZipEntry, Int, Int>> { it.second }
                .thenBy { it.third }
                .thenBy { it.first.name },
        )?.first
    }

    private fun extractPng(archive: ZipFile, entry: ZipEntry, destination: File): File {
        require(entry.size in 1..MAX_PNG_BYTES) { "官方 PNG 图片大小不合法" }
        val bytes = archive.getInputStream(entry).use { input ->
            input.readLimited(MAX_PNG_BYTES)
        }
        require(bytes.size.toLong() == entry.size) { "官方 PNG 图片读取不完整" }
        require(validatePng(bytes)) { "官方资源包包含无效 PNG 图片" }
        FileOutputStream(destination, false).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        return destination
    }

    private fun InputStream.readLimited(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "官方 PNG 图片超过大小上限" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL
}
