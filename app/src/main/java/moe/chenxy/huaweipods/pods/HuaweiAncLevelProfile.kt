package moe.chenxy.huaweipods.pods

/**
 * 同一 ANC 协议值在不同耳机上可能代表不同语义，所有档位转换必须携带机型路由。
 */
internal data class HuaweiAncLevelOption(
    val level: HuaweiAncLevel,
    val protocolValue: Int,
    val miuiValue: Int,
)

private val fourLevelAncOptions = listOf(
    HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x01, miuiValue = 0x03),
    HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x00, miuiValue = 0x01),
    HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x02, miuiValue = 0x00),
    HuaweiAncLevelOption(HuaweiAncLevel.DEEP, protocolValue = 0x03, miuiValue = 0x02),
)

private val freeBuds5AncOptions = listOf(
    HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x03, miuiValue = 0x03),
    HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
    HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
)

internal val HuaweiDeviceRoute.ancLevelOptions: List<HuaweiAncLevelOption>
    get() = when (this) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5 -> freeBuds5AncOptions
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        -> fourLevelAncOptions
        else -> emptyList()
    }

internal val HuaweiDeviceRoute.defaultAncSubMode: Int?
    get() = ancLevelOptions.firstOrNull { it.level == HuaweiAncLevel.ADAPTIVE }?.protocolValue

internal fun HuaweiDeviceRoute.ancLevelOptionForProtocolValue(value: Int): HuaweiAncLevelOption? =
    ancLevelOptions.firstOrNull { it.protocolValue == value }

internal fun HuaweiDeviceRoute.ancSubModeForMiuiLevel(value: Int): Int? =
    ancLevelOptions.firstOrNull { it.miuiValue == value }?.protocolValue

internal fun HuaweiDeviceRoute.miuiLevelForAncSubMode(value: Int): Int? =
    ancLevelOptionForProtocolValue(value)?.miuiValue

internal fun HuaweiDeviceRoute.supportsAncSubMode(value: Int): Boolean =
    ancLevelOptionForProtocolValue(value) != null

/** 丢弃与当前机型能力不一致的回读，避免把其他机型档位或通透状态写入会话。 */
internal fun HuaweiDeviceRoute.validateAncState(state: HuaweiAncState): HuaweiAncState? = when {
    !supportsAnc -> null
    state.mode == NoiseControlMode.OFF -> HuaweiAncState(NoiseControlMode.OFF)
    state.mode == NoiseControlMode.NOISE_CANCELLATION -> when {
        supportsDiscreteAncLevels -> state.takeIf { state.subMode?.let(::supportsAncSubMode) == true }
        else -> HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION)
    }
    state.mode == NoiseControlMode.TRANSPARENCY -> state.takeIf { supportsTransparency }
    else -> null
}
