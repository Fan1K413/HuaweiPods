package moe.chenxy.huaweipods.debugcapture

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

/** 对用户从智慧音频目录导出的图片资源包做只读校验，不解压其中内容。 */
internal object SmartAudioAssetArchive {
    const val MAX_ARCHIVE_BYTES = 128L * 1024L * 1024L
    private const val MAX_ENTRY_COUNT = 5_000
    private const val MAX_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L

    fun inspect(file: File): SmartAudioAssetArchiveInfo {
        require(file.isFile && file.length() > 0L) { "所选资源包为空" }
        require(file.length() <= MAX_ARCHIVE_BYTES) { "资源包超过 128 MiB" }

        var entryCount = 0
        var pngEntryCount = 0
        var declaredUncompressedBytes = 0L
        val mainImageEntries = mutableListOf<String>()
        val leftImageEntries = mutableListOf<String>()
        val rightImageEntries = mutableListOf<String>()
        ZipFile(file).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "资源包文件数量过多" }
                require(isSafeEntryName(entry.name)) { "资源包包含不安全路径" }
                if (entry.isDirectory) continue

                val entrySize = entry.size
                if (entrySize > 0L) {
                    require(entrySize <= MAX_UNCOMPRESSED_BYTES - declaredUncompressedBytes) {
                        "资源包解压后体积过大"
                    }
                    declaredUncompressedBytes += entrySize
                }
                val normalizedName = entry.name.replace('\\', '/')
                val fileName = normalizedName.substringAfterLast('/').lowercase(Locale.US)
                if (fileName.endsWith(".png")) {
                    pngEntryCount++
                    when {
                        fileName in MAIN_IMAGE_NAMES -> mainImageEntries += normalizedName
                        fileName in LEFT_IMAGE_NAMES -> leftImageEntries += normalizedName
                        fileName in RIGHT_IMAGE_NAMES -> rightImageEntries += normalizedName
                    }
                }
            }
        }

        require(entryCount > 0) { "资源包内没有文件" }
        require(pngEntryCount > 0) { "资源包内没有 PNG 图片" }
        require(mainImageEntries.isNotEmpty()) {
            "没有找到 iv_device_logo.png 或 device_icon.png 等机型主图"
        }
        return SmartAudioAssetArchiveInfo(
            bytes = file.length(),
            entryCount = entryCount,
            pngEntryCount = pngEntryCount,
            mainImageEntries = mainImageEntries.sorted(),
            leftImageEntries = leftImageEntries.sorted(),
            rightImageEntries = rightImageEntries.sorted(),
            sha256 = sha256(file),
        )
    }

    internal fun isSafeEntryName(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith('/') || DRIVE_PREFIX.matches(normalized)) {
            return false
        }
        return normalized.split('/').none { it == ".." }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:/.*")
    private val MAIN_IMAGE_NAMES = setOf(
        "iv_device_logo.png",
        "device_icon.png",
        "mediacontroller_icon.png",
        "ota_device_icon.png",
        "ota_device.png",
    )
    private val LEFT_IMAGE_NAMES = setOf(
        "find_left_icon.png",
        "find_left.png",
        "left_ear.png",
        "left_ear_icon.png",
    )
    private val RIGHT_IMAGE_NAMES = setOf(
        "find_right_icon.png",
        "find_right.png",
        "right_ear.png",
        "right_ear_icon.png",
    )
}

internal data class SmartAudioAssetArchiveInfo(
    val bytes: Long,
    val entryCount: Int,
    val pngEntryCount: Int,
    val mainImageEntries: List<String>,
    val leftImageEntries: List<String>,
    val rightImageEntries: List<String>,
    val sha256: String,
)
