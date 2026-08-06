package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiAncLevel
import moe.chenxy.huaweipods.pods.HuaweiAncState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.normalizeHuaweiAncSubMode
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsTransparency

/** 将小米蓝牙服务的模式编号转换为当前华为机型真正支持的状态。 */
internal fun upstreamHuaweiAncStateForMode(
    route: HuaweiDeviceRoute,
    miuiMode: Int,
    previousState: HuaweiAncState,
): HuaweiAncState? {
    if (!route.supportsAnc) return null
    val mode = when (miuiMode) {
        0 -> NoiseControlMode.OFF
        1 -> NoiseControlMode.NOISE_CANCELLATION
        2 -> NoiseControlMode.TRANSPARENCY.takeIf { route.supportsTransparency }
        else -> null
    } ?: return null
    return HuaweiAncState(
        mode = mode,
        subMode = normalizeHuaweiAncSubMode(route, mode, requestedSubMode = null, previousState),
    )
}

/** 解析刷新载荷里的 00xx/01xx/02xx 指令，同时拒绝机型不支持的模式与子模式。 */
internal fun upstreamHuaweiAncStateForLevel(
    route: HuaweiDeviceRoute,
    level: String,
    previousState: HuaweiAncState,
): HuaweiAncState? {
    if (!route.supportsAnc) return null
    val normalized = level.trim().lowercase()
    if (normalized == "0000") {
        return HuaweiAncState(NoiseControlMode.OFF)
    }
    if (normalized.length < 4) return null
    val type = normalized.substring(0, 2)
    val requestedSubMode = normalized.substring(2, 4).toIntOrNull(16) ?: return null
    val mode = when (type) {
        "01" -> NoiseControlMode.NOISE_CANCELLATION
        "02" -> NoiseControlMode.TRANSPARENCY.takeIf { route.supportsTransparency }
        else -> null
    } ?: return null
    if (
        mode == NoiseControlMode.NOISE_CANCELLATION &&
        route.supportsDiscreteAncLevels &&
        HuaweiAncLevel.fromProtocolValue(requestedSubMode) == null
    ) {
        return null
    }
    val subMode = normalizeHuaweiAncSubMode(route, mode, requestedSubMode, previousState)
    if (mode == NoiseControlMode.TRANSPARENCY && subMode != requestedSubMode) return null
    return HuaweiAncState(mode, subMode)
}

/** 生成 MIUI 刷新载荷使用的噪声控制字段。 */
internal fun upstreamMiuiAncLevel(
    route: HuaweiDeviceRoute,
    state: HuaweiAncState,
): String {
    if (!route.supportsAnc) return "0000"
    return when (state.mode) {
        NoiseControlMode.NOISE_CANCELLATION -> {
            if (!route.supportsDiscreteAncLevels) {
                "0100"
            } else {
                val subMode = normalizeHuaweiAncSubMode(route, state.mode, state.subMode, state)
                    ?: HuaweiAncLevel.ADAPTIVE.protocolValue
                "01${subMode.toString(16).padStart(2, '0')}"
            }
        }
        NoiseControlMode.TRANSPARENCY -> {
            val subMode = normalizeHuaweiAncSubMode(route, state.mode, state.subMode, state)
                ?: return "0000"
            "02${subMode.toString(16).padStart(2, '0')}"
        }
        NoiseControlMode.UNKNOWN,
        NoiseControlMode.OFF -> "0000"
    }
}
