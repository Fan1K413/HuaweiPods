package moe.chenxy.huaweipods.smartaudio

import java.util.Locale

internal data class SmartAudioResourceIdentity(
    val address: String,
    val modelId: String,
    val subModelId: String,
)

/** 只接受智慧音频当前设备总线给出的完整资源身份，不从蓝牙名称推断。 */
internal object SmartAudioResourceIdentityPolicy {
    private val addressRegex = Regex("^[0-9A-F]{2}(?::[0-9A-F]{2}){5}$")
    private val modelIdRegex = Regex("^[0-9A-F]{6}$")
    private val subModelIdRegex = Regex("^[0-9A-F]{2}$")

    fun normalize(
        address: String?,
        modelId: String?,
        subModelId: String?,
    ): SmartAudioResourceIdentity? {
        val normalizedAddress = address
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf(addressRegex::matches)
            ?: return null
        val normalizedModelId = modelId
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf(modelIdRegex::matches)
            ?: return null
        val normalizedSubModelId = subModelId
            ?.trim()
            ?.removePrefix("0x")
            ?.removePrefix("0X")
            ?.uppercase(Locale.US)
            ?.takeIf(subModelIdRegex::matches)
            ?: return null
        return SmartAudioResourceIdentity(
            address = normalizedAddress,
            modelId = normalizedModelId,
            subModelId = normalizedSubModelId,
        )
    }

    /** 智慧音频不同版本中会提交完整资源身份的窄接口。 */
    fun isIdentityMutation(
        methodName: String,
        parameterTypeNames: List<String>,
    ): Boolean = when (methodName) {
        "e1", "a1" -> parameterTypeNames == listOf("java.lang.String")
        "U0" -> parameterTypeNames == listOf(
            "com.huawei.audiodevicekit.audiobluetooth.layer.protocol.mbb.j",
        )
        else -> false
    }
}
