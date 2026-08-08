package moe.chenxy.huaweipods.debugcapture

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

internal data class DownloadedSmartAudioResources(
    val candidate: SmartAudioResourceCandidate,
    val selection: SmartAudioResourceSelection,
    val configBytes: ByteArray,
    val archiveFile: File,
    val archiveInfo: SmartAudioAssetArchiveInfo,
)

/** 从白名单中的华为公开 CDN 下载当前机型资源；请求不携带账号、Cookie 或设备信息。 */
internal object SmartAudioResourceDownloader {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val TEMP_DIRECTORY = "smart-audio-resource-downloads"

    fun download(
        context: Context,
        candidate: SmartAudioResourceCandidate,
    ): DownloadedSmartAudioResources {
        val configBytes = downloadBytes(
            uri = candidate.origin.configUri(candidate.modelId),
            maxBytes = SmartAudioProductConfig.MAX_CONFIG_BYTES.toLong(),
            accept = "application/json",
        )
        val selection = SmartAudioProductConfig.selectResource(
            configBytes = configBytes,
            expectedModelId = candidate.modelId,
            observedSubModelId = candidate.subModelId,
        )
        val cacheDirectory = File(context.cacheDir, TEMP_DIRECTORY)
        check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
            "无法创建智慧音频资源临时目录"
        }
        val archiveFile = File.createTempFile(
            "${selection.modelId}_${selection.subModelId}_",
            ".zip",
            cacheDirectory,
        )
        try {
            downloadFile(
                uri = candidate.origin.archiveUri(
                    selection.modelId,
                    selection.archiveFileName,
                ),
                destination = archiveFile,
                maxBytes = SmartAudioAssetArchive.MAX_ARCHIVE_BYTES,
                accept = "application/zip, application/octet-stream",
            )
            check(
                SmartAudioProductConfig.isArchiveSizePlausible(
                    actualBytes = archiveFile.length(),
                    declaredSizeKb = selection.declaredSizeKb,
                ),
            ) { "下载的资源包大小与官方配置不符" }
            val archiveInfo = SmartAudioAssetArchive.inspect(archiveFile)
            return DownloadedSmartAudioResources(
                candidate = candidate,
                selection = selection,
                configBytes = configBytes,
                archiveFile = archiveFile,
                archiveInfo = archiveInfo,
            )
        } catch (throwable: Throwable) {
            archiveFile.delete()
            throw throwable
        }
    }

    private fun downloadBytes(uri: URI, maxBytes: Long, accept: String): ByteArray =
        openConnection(uri, accept).useConnection { connection ->
            val expectedBytes = connection.contentLengthLong
            check(expectedBytes <= maxBytes || expectedBytes < 0L) { "官方资源响应超过大小上限" }
            connection.inputStream.buffered().use { input ->
                val output = ByteArrayOutputStream(
                    expectedBytes.takeIf { it in 1..maxBytes }?.toInt() ?: DEFAULT_BUFFER_SIZE,
                )
                copyLimited(input, output, maxBytes)
                output.toByteArray()
            }
        }

    private fun downloadFile(
        uri: URI,
        destination: File,
        maxBytes: Long,
        accept: String,
    ) {
        openConnection(uri, accept).useConnection { connection ->
            val expectedBytes = connection.contentLengthLong
            check(expectedBytes <= maxBytes || expectedBytes < 0L) { "官方资源响应超过大小上限" }
            val actualBytes = connection.inputStream.buffered().use { input ->
                FileOutputStream(destination, false).buffered().use { output ->
                    copyLimited(input, output, maxBytes)
                }
            }
            check(expectedBytes < 0L || actualBytes == expectedBytes) { "官方资源响应长度不完整" }
        }
    }

    private fun openConnection(uri: URI, accept: String): HttpsURLConnection {
        val origin = SmartAudioResourceOrigin.fromHost(uri.host)
            ?: error("拒绝访问非官方智慧音频资源域名")
        check(uri.scheme == "https" && uri.userInfo == null && uri.port == -1) {
            "智慧音频资源地址不合法"
        }
        check(uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.startsWith(origin.pathPrefix)) {
            "智慧音频资源路径不合法"
        }
        val connection = uri.toURL().openConnection() as? HttpsURLConnection
            ?: error("智慧音频资源连接不是 HTTPS")
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("Accept", accept)
        val responseCode = connection.responseCode
        check(responseCode == HttpsURLConnection.HTTP_OK) {
            "官方资源下载失败（HTTP $responseCode）"
        }
        return connection
    }

    private inline fun <T> HttpsURLConnection.useConnection(
        block: (HttpsURLConnection) -> T,
    ): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private fun copyLimited(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        maxBytes: Long,
    ): Long {
        var written = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val remaining = maxBytes - written
            check(remaining > 0L || input.read() == -1) { "官方资源响应超过大小上限" }
            if (remaining <= 0L) break
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read
        }
        output.flush()
        return written
    }
}
