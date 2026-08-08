package moe.chenxy.huaweipods.debugcapture

/**
 * 规范化智慧音频当前设备总线提供的资源身份。
 *
 * 老型号（例如 FreeBuds 3）不一定通过现代 RFCOMM DeviceInfo 帧上报身份，
 * 但智慧音频自身仍会在当前设备总线中保存 modelId/subModelId。这里仅接受
 * 完整的官方内部编号，不从蓝牙显示名称猜测机型或配色。
 */
internal fun smartAudioCurrentDeviceIdentity(
    modelId: String?,
    subModelId: String?,
): SmartAudioDeviceIdentity? {
    val normalizedModelId = SmartAudioResourceLocator.normalizeModelId(modelId) ?: return null
    val rawSubModelId = subModelId?.trim()?.let { value ->
        if (value.length == 4 && value.startsWith("0x", ignoreCase = true)) {
            value.substring(2)
        } else {
            value
        }
    }
    val normalizedSubModelId = SmartAudioResourceLocator.normalizeSubModelId(
        rawSubModelId,
    ) ?: return null
    return SmartAudioDeviceIdentity(
        modelId = normalizedModelId,
        subModelId = normalizedSubModelId,
    )
}

/** 仅匹配智慧音频当前设备总线中会改变地址或资源身份的窄接口。 */
internal fun isSmartAudioCurrentDeviceIdentityMutation(
    methodName: String,
    parameterTypeNames: List<String>,
): Boolean = when (methodName) {
    "e1", "a1" -> parameterTypeNames == listOf("java.lang.String")
    "U0" -> parameterTypeNames == listOf(
        "com.huawei.audiodevicekit.audiobluetooth.layer.protocol.mbb.j",
    )
    else -> false
}
