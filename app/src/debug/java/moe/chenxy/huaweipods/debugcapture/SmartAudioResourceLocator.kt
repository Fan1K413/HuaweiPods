package moe.chenxy.huaweipods.debugcapture

import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 仅描述智慧音频公开 CDN 中可安全复现的设备资源位置。 */
internal enum class SmartAudioResourceOrigin(
    val wireName: String,
    val host: String,
    val pathPrefix: String,
) {
    CHINA(
        wireName = "cn",
        host = "smarthome-drcn.dbankcdn.cn",
        pathPrefix = "/device/guide/AAM001/",
    ),
    ASIA_AFRICA_LATIN(
        wireName = "dra",
        host = "contentcenter-dra.dbankcdn.cn",
        pathPrefix = "/pub_1/HW-SmartHome_oem_900_9/41/v3/aaa/device/guide/",
    ),
    EUROPE(
        wireName = "dre",
        host = "contentcenter-dre.dbankcdn.cn",
        pathPrefix = "/pub_1/HW-SmartHome_oem_900_9/b0/v3/eu/device/guide/",
    ),
    RUSSIA(
        wireName = "drru",
        host = "contentcenter-drru.dbankcdn.ru",
        pathPrefix = "/pub_1/HW-SmartHome_oem_900_9/43/v3/ru/device/guide/",
    );

    fun configUri(modelId: String): URI = resourceUri(modelId, "$modelId.json")

    fun archiveUri(modelId: String, fileName: String): URI = resourceUri(modelId, fileName)

    private fun resourceUri(modelId: String, fileName: String): URI = URI(
        "https",
        null,
        host,
        -1,
        "$pathPrefix$modelId/$fileName",
        null,
        null,
    )

    companion object {
        fun fromHost(host: String?): SmartAudioResourceOrigin? = entries.firstOrNull {
            it.host.equals(host, ignoreCase = true)
        }

        fun fromWireName(value: String?): SmartAudioResourceOrigin? = entries.firstOrNull {
            it.wireName == value
        }
    }
}

internal enum class SmartAudioObservedResourceKind {
    CONFIG,
    ARCHIVE,
}

internal data class SmartAudioObservedResource(
    val origin: SmartAudioResourceOrigin,
    val modelId: String,
    val subModelId: String?,
    val kind: SmartAudioObservedResourceKind,
    val observedAtEpochMs: Long = 0L,
)

internal data class SmartAudioResourceCandidate(
    val origin: SmartAudioResourceOrigin,
    val modelId: String,
    val subModelId: String?,
    val observedAtEpochMs: Long,
)

internal object SmartAudioResourceCandidatePolicy {
    fun conflicts(
        existing: SmartAudioResourceCandidate?,
        observed: SmartAudioResourceCandidate,
    ): Boolean = existing != null && (
        existing.modelId != observed.modelId ||
            (
                existing.subModelId != null &&
                    observed.subModelId != null &&
                    existing.subModelId != observed.subModelId
                )
        )

    fun mergeRouteWithKnownIdentity(
        existing: SmartAudioResourceCandidate?,
        observed: SmartAudioResourceCandidate,
    ): SmartAudioResourceCandidate = if (
        existing?.modelId == observed.modelId &&
        existing.subModelId != null &&
        observed.subModelId == null
    ) {
        observed.copy(subModelId = existing.subModelId)
    } else {
        observed
    }

    fun prefer(
        existing: SmartAudioResourceCandidate?,
        observed: SmartAudioResourceCandidate,
    ): SmartAudioResourceCandidate = when {
        existing == null -> observed
        observed.subModelId != null -> observed
        existing.subModelId != null -> existing
        else -> observed
    }
}

internal object SmartAudioResourceLocator {
    private const val MAX_OBSERVED_URL_CHARS = 2_048
    private const val COMMON_RESOURCE_MODEL_ID = "00000A"
    private val modelIdRegex = Regex("^[0-9A-F]{6}$")
    private val subModelIdRegex = Regex("^[0-9A-F]{2}$")

    fun parseObservedUrl(rawUrl: String?): SmartAudioObservedResource? {
        val value = rawUrl?.takeIf { it.length in 1..MAX_OBSERVED_URL_CHARS } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) {
            return null
        }
        val origin = SmartAudioResourceOrigin.fromHost(uri.host) ?: return null
        val rawPath = uri.rawPath ?: return null
        if (!rawPath.startsWith(origin.pathPrefix)) return null

        val relativePath = rawPath.removePrefix(origin.pathPrefix)
        val segments = relativePath.split('/')
        if (segments.size != 2) return null
        val modelId = normalizeModelId(segments[0]) ?: return null
        if (modelId == COMMON_RESOURCE_MODEL_ID) return null
        val fileName = segments[1]

        if (fileName == "$modelId.json") {
            return SmartAudioObservedResource(
                origin = origin,
                modelId = modelId,
                subModelId = null,
                kind = SmartAudioObservedResourceKind.CONFIG,
            )
        }

        val archivePrefix = "${modelId}_"
        if (!fileName.startsWith(archivePrefix) || !fileName.endsWith(".zip")) return null
        val subModelId = normalizeSubModelId(
            fileName.substring(archivePrefix.length, fileName.length - ".zip".length),
        ) ?: return null
        return SmartAudioObservedResource(
            origin = origin,
            modelId = modelId,
            subModelId = subModelId,
            kind = SmartAudioObservedResourceKind.ARCHIVE,
        )
    }

    fun candidate(
        originWireName: String?,
        modelId: String?,
        subModelId: String?,
        observedAtEpochMs: Long,
    ): SmartAudioResourceCandidate? {
        val origin = SmartAudioResourceOrigin.fromWireName(originWireName) ?: return null
        val normalizedModelId = normalizeModelId(modelId) ?: return null
        if (normalizedModelId == COMMON_RESOURCE_MODEL_ID) return null
        val normalizedSubModelId = if (subModelId.isNullOrBlank()) {
            null
        } else {
            normalizeSubModelId(subModelId) ?: return null
        }
        if (observedAtEpochMs <= 0L) return null
        return SmartAudioResourceCandidate(
            origin = origin,
            modelId = normalizedModelId,
            subModelId = normalizedSubModelId,
            observedAtEpochMs = observedAtEpochMs,
        )
    }

    fun normalizeModelId(value: String?): String? = value
        ?.uppercase(Locale.US)
        ?.takeIf(modelIdRegex::matches)

    fun normalizeSubModelId(value: String?): String? = value
        ?.uppercase(Locale.US)
        ?.takeIf(subModelIdRegex::matches)
}

internal data class SmartAudioResourceSelection(
    val modelId: String,
    val subModelId: String,
    val archiveFileName: String,
    val declaredSizeKb: Int,
)

/** 解析 CDN 产品配置，只接受当前资源下载流程所需的四个字段。 */
internal object SmartAudioProductConfig {
    const val MAX_CONFIG_BYTES = 512 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    fun selectResource(
        configBytes: ByteArray,
        expectedModelId: String,
        observedSubModelId: String?,
    ): SmartAudioResourceSelection {
        require(configBytes.isNotEmpty()) { "智慧音频资源配置为空" }
        require(configBytes.size <= MAX_CONFIG_BYTES) { "智慧音频资源配置过大" }
        val normalizedModelId = requireNotNull(
            SmartAudioResourceLocator.normalizeModelId(expectedModelId),
        ) { "机型 ID 不合法" }
        val root = json.parseToJsonElement(
            configBytes.toString(Charsets.UTF_8).trimStart('\uFEFF'),
        ).jsonObject
        val actualModelId = SmartAudioResourceLocator.normalizeModelId(
            root["modelId"]?.jsonPrimitive?.contentOrNull,
        )
        require(actualModelId == normalizedModelId) { "资源配置中的机型 ID 不匹配" }

        val resourceMap = root["resourceMap"]?.jsonObject
            ?: error("资源配置缺少 resourceMap")
        val selectedSubModelId = requireNotNull(
            SmartAudioResourceLocator.normalizeSubModelId(observedSubModelId),
        ) { "未确认当前子型号，不能自动选择默认配色资源" }
        val resource = resourceMap[selectedSubModelId]?.jsonObject
            ?: error("资源配置不包含当前子型号 $selectedSubModelId")
        val expectedArchiveName = "${normalizedModelId}_${selectedSubModelId}.zip"
        val archiveFileName = resource["resourceRemark"]?.jsonPrimitive?.contentOrNull
        require(archiveFileName == expectedArchiveName) { "资源文件名不符合官方规则" }
        val declaredSizeKb = resource["resourceFileSizeKb"]?.jsonPrimitive?.intOrNull
            ?: error("资源配置缺少文件大小")
        require(declaredSizeKb in 1..(SmartAudioAssetArchive.MAX_ARCHIVE_BYTES / 1024L).toInt()) {
            "资源配置中的文件大小不合法"
        }
        return SmartAudioResourceSelection(
            modelId = normalizedModelId,
            subModelId = selectedSubModelId,
            archiveFileName = archiveFileName,
            declaredSizeKb = declaredSizeKb,
        )
    }

    fun isArchiveSizePlausible(actualBytes: Long, declaredSizeKb: Int): Boolean {
        if (actualBytes <= 0L || actualBytes > SmartAudioAssetArchive.MAX_ARCHIVE_BYTES) return false
        val actualSizeKb = (actualBytes + 1023L) / 1024L
        return kotlin.math.abs(actualSizeKb - declaredSizeKb.toLong()) <= 1L
    }
}
