package moe.chenxy.huaweipods.smartaudio

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class OfficialSmartAudioResourceSelection(
    val modelId: String,
    val subModelId: String,
    val archiveFileName: String,
    val declaredSizeKb: Int,
)

internal data class OfficialSmartAudioResourceOption(
    val modelId: String,
    val subModelId: String,
    val resourceDesc: String,
    val resourceIndex: Int?,
)

/** 华为智慧音频中国区公开设备资源的固定地址，禁止跟随配置中的任意 URL。 */
internal object OfficialSmartAudioResource {
    const val HOST = "smarthome-drcn.dbankcdn.cn"
    const val PATH_PREFIX = "/device/guide/AAM001/"
    const val MAX_CONFIG_BYTES = 512 * 1024
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L

    private val json = Json { ignoreUnknownKeys = true }
    private val subModelIdRegex = Regex("^[0-9A-F]{2}$")

    fun configUri(modelId: String): URI = resourceUri(modelId, "$modelId.json")

    fun archiveUri(selection: OfficialSmartAudioResourceSelection): URI = resourceUri(
        selection.modelId,
        selection.archiveFileName,
    )

    fun selectResource(
        configBytes: ByteArray,
        identity: SmartAudioResourceIdentity,
    ): OfficialSmartAudioResourceSelection {
        val root = parseConfig(configBytes, identity.modelId)
        val resource = root["resourceMap"]?.jsonObject?.get(identity.subModelId)?.jsonObject
            ?: error("资源配置不包含当前子型号 ${identity.subModelId}")
        return selectionFromResource(identity.modelId, identity.subModelId, resource)
    }

    /**
     * 枚举固定官方配置中的可选配色。只接受两位大写十六进制子型号，
     * 并重用下载时的文件名和大小校验；defaultSubModelId 不参与选择。
     */
    fun listOptions(
        configBytes: ByteArray,
        expectedModelId: String,
    ): List<OfficialSmartAudioResourceOption> {
        val root = parseConfig(configBytes, expectedModelId)
        val resourceMap = root["resourceMap"]?.jsonObject
            ?: error("资源配置缺少 resourceMap")
        return resourceMap.mapNotNull { (subModelId, element) ->
            if (!subModelIdRegex.matches(subModelId)) return@mapNotNull null
            val resource = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val description = resource["resourceDesc"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { value -> value.length in 1..80 && value.none(Char::isISOControl) }
                ?: return@mapNotNull null
            runCatching {
                selectionFromResource(expectedModelId, subModelId, resource)
            }.getOrNull() ?: return@mapNotNull null
            OfficialSmartAudioResourceOption(
                modelId = expectedModelId,
                subModelId = subModelId,
                resourceDesc = description,
                resourceIndex = resource["resourceIndex"]?.jsonPrimitive?.intOrNull,
            )
        }.sortedWith(
            compareBy<OfficialSmartAudioResourceOption> { it.resourceIndex ?: Int.MAX_VALUE }
                .thenBy { it.subModelId },
        )
    }

    private fun selectionFromResource(
        modelId: String,
        subModelId: String,
        resource: JsonObject,
    ): OfficialSmartAudioResourceSelection {
        require(subModelIdRegex.matches(subModelId)) { "子型号不合法" }
        resource["resourceKey"]?.jsonPrimitive?.contentOrNull?.let { resourceKey ->
            require(resourceKey == subModelId) { "资源键与子型号不匹配" }
        }
        val expectedArchiveName = "${modelId}_${subModelId}.zip"
        val archiveFileName = resource["resourceRemark"]?.jsonPrimitive?.contentOrNull
        require(archiveFileName == expectedArchiveName) { "资源文件名不符合官方规则" }
        val declaredSizeKb = resource["resourceFileSizeKb"]?.jsonPrimitive?.intOrNull
            ?: error("资源配置缺少文件大小")
        require(declaredSizeKb in 1..(MAX_ARCHIVE_BYTES / 1024L).toInt()) {
            "资源配置中的文件大小不合法"
        }
        return OfficialSmartAudioResourceSelection(
            modelId = modelId,
            subModelId = subModelId,
            archiveFileName = archiveFileName,
            declaredSizeKb = declaredSizeKb,
        )
    }

    private fun parseConfig(configBytes: ByteArray, expectedModelId: String): JsonObject {
        require(configBytes.isNotEmpty()) { "智慧音频资源配置为空" }
        require(configBytes.size <= MAX_CONFIG_BYTES) { "智慧音频资源配置过大" }
        require(expectedModelId.matches(Regex("^[0-9A-F]{6}$"))) { "机型 ID 不合法" }
        val root = json.parseToJsonElement(
            configBytes.toString(Charsets.UTF_8).trimStart('\uFEFF'),
        ).jsonObject
        require(root["modelId"]?.jsonPrimitive?.contentOrNull == expectedModelId) {
            "资源配置中的机型 ID 不匹配"
        }
        return root
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
