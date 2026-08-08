package moe.chenxy.huaweipods.smartaudio

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class OfficialSmartAudioResourceSelection(
    val modelId: String,
    val subModelId: String,
    val archiveFileName: String,
    val declaredSizeKb: Int,
)

/** 华为智慧音频中国区公开设备资源的固定地址，禁止跟随配置中的任意 URL。 */
internal object OfficialSmartAudioResource {
    const val HOST = "smarthome-drcn.dbankcdn.cn"
    const val PATH_PREFIX = "/device/guide/AAM001/"
    const val MAX_CONFIG_BYTES = 512 * 1024
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L

    private val json = Json { ignoreUnknownKeys = true }

    fun configUri(modelId: String): URI = resourceUri(modelId, "$modelId.json")

    fun archiveUri(selection: OfficialSmartAudioResourceSelection): URI = resourceUri(
        selection.modelId,
        selection.archiveFileName,
    )

    fun selectResource(
        configBytes: ByteArray,
        identity: SmartAudioResourceIdentity,
    ): OfficialSmartAudioResourceSelection {
        require(configBytes.isNotEmpty()) { "智慧音频资源配置为空" }
        require(configBytes.size <= MAX_CONFIG_BYTES) { "智慧音频资源配置过大" }
        val root = json.parseToJsonElement(
            configBytes.toString(Charsets.UTF_8).trimStart('\uFEFF'),
        ).jsonObject
        require(root["modelId"]?.jsonPrimitive?.contentOrNull == identity.modelId) {
            "资源配置中的机型 ID 不匹配"
        }
        val resource = root["resourceMap"]?.jsonObject?.get(identity.subModelId)?.jsonObject
            ?: error("资源配置不包含当前子型号 ${identity.subModelId}")
        val expectedArchiveName = "${identity.modelId}_${identity.subModelId}.zip"
        val archiveFileName = resource["resourceRemark"]?.jsonPrimitive?.contentOrNull
        require(archiveFileName == expectedArchiveName) { "资源文件名不符合官方规则" }
        val declaredSizeKb = resource["resourceFileSizeKb"]?.jsonPrimitive?.intOrNull
            ?: error("资源配置缺少文件大小")
        require(declaredSizeKb in 1..(MAX_ARCHIVE_BYTES / 1024L).toInt()) {
            "资源配置中的文件大小不合法"
        }
        return OfficialSmartAudioResourceSelection(
            modelId = identity.modelId,
            subModelId = identity.subModelId,
            archiveFileName = archiveFileName,
            declaredSizeKb = declaredSizeKb,
        )
    }

    fun isArchiveSizePlausible(actualBytes: Long, declaredSizeKb: Int): Boolean {
        if (actualBytes <= 0L || actualBytes > MAX_ARCHIVE_BYTES) return false
        val actualSizeKb = (actualBytes + 1023L) / 1024L
        return kotlin.math.abs(actualSizeKb - declaredSizeKb.toLong()) <= 1L
    }

    fun isAllowedUri(uri: URI): Boolean =
        uri.scheme == "https" &&
            uri.host == HOST &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawPath.startsWith(PATH_PREFIX)

    private fun resourceUri(modelId: String, fileName: String): URI {
        require(modelId.matches(Regex("^[0-9A-F]{6}$"))) { "机型 ID 不合法" }
        require(fileName.matches(Regex("^[0-9A-F]{6}(?:_[0-9A-F]{2})?\\.(?:json|zip)$"))) {
            "资源文件名不合法"
        }
        return URI("https", null, HOST, -1, "$PATH_PREFIX$modelId/$fileName", null, null)
    }
}
