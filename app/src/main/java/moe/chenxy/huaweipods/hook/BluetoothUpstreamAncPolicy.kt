package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiAncState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.ancSubModeForMiuiLevel
import moe.chenxy.huaweipods.pods.defaultAncSubMode
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.miuiLevelForAncSubMode
import moe.chenxy.huaweipods.pods.normalizeHuaweiAncSubMode
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsAncSubMode
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsTransparency

/**
 * FreeClip 一代目前只接入了电量协议。若仍向控制中心声明为完整的小米 TWS，
 * HyperOS 会进入需要高级设备配置/回调的专属小窗，并在初始化失败后直接关闭。
 * 这里仅让它回退到系统普通蓝牙设备展示；模块通知、详情页和融合设备中心不受影响。
 */
internal fun shouldExposeMiuiAdvancedHeadsetUi(route: HuaweiDeviceRoute): Boolean =
    route.isSupported && route != HuaweiDeviceRoute.HUAWEI_FREECLIP

/**
 * 当前 MIUI 耳机模板的四档菜单编码与 Huawei 协议值并不相同。
 * 转换只应发生在 MIUI Hook 边界，不能用于模块 UI 或 Huawei RFCOMM 指令。
 */
internal fun miuiDiscreteAncLevelToHuaweiSubMode(
    route: HuaweiDeviceRoute,
    miuiLevel: Int,
): Int? = route.ancSubModeForMiuiLevel(miuiLevel)

internal fun huaweiSubModeToMiuiDiscreteAncLevel(
    route: HuaweiDeviceRoute,
    huaweiSubMode: Int,
): Int? = route.miuiLevelForAncSubMode(huaweiSubMode)

/** 6i 借用的小米原生两档透传控件使用 0/1，而耳机协议使用 2/1。 */
internal fun miuiTransparencyLevelToHuaweiSubMode(
    route: HuaweiDeviceRoute,
    value: Int,
): Int? = if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
    when (value) {
        0x00 -> 0x02 // 普通
        0x01 -> 0x01 // 人声增强
        else -> null
    }
} else {
    value
}

internal fun huaweiTransparencySubModeToMiuiLevel(
    route: HuaweiDeviceRoute,
    value: Int,
): Int? = if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
    when (value) {
        0x02 -> 0x00 // 普通
        0x01 -> 0x01 // 人声增强
        else -> null
    }
} else {
    value
}

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
    val miuiSubMode = normalized.substring(2, 4).toIntOrNull(16) ?: return null
    val mode = when (type) {
        "01" -> NoiseControlMode.NOISE_CANCELLATION
        "02" -> NoiseControlMode.TRANSPARENCY.takeIf { route.supportsTransparency }
        else -> null
    } ?: return null
    val requestedSubMode = when {
        mode == NoiseControlMode.NOISE_CANCELLATION && route.supportsDiscreteAncLevels ->
            miuiDiscreteAncLevelToHuaweiSubMode(route, miuiSubMode) ?: return null
        mode == NoiseControlMode.TRANSPARENCY ->
            miuiTransparencyLevelToHuaweiSubMode(route, miuiSubMode) ?: return null
        else -> miuiSubMode
    }
    if (
        mode == NoiseControlMode.NOISE_CANCELLATION &&
        route.supportsDiscreteAncLevels &&
        !route.supportsAncSubMode(requestedSubMode)
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
                val subMode = when (val reported = state.subMode) {
                    null -> route.defaultAncSubMode ?: return "0000"
                    else -> reported.takeIf(route::supportsAncSubMode) ?: return "0000"
                }
                val miuiLevel = huaweiSubModeToMiuiDiscreteAncLevel(route, subMode)
                    ?: return "0000"
                "01${miuiLevel.toString(16).padStart(2, '0')}"
            }
        }
        NoiseControlMode.TRANSPARENCY -> {
            val subMode = normalizeHuaweiAncSubMode(route, state.mode, state.subMode, state)
                ?: return "0000"
            val miuiLevel = huaweiTransparencySubModeToMiuiLevel(route, subMode)
                ?: return "0000"
            "02${miuiLevel.toString(16).padStart(2, '0')}"
        }
        NoiseControlMode.UNKNOWN,
        NoiseControlMode.OFF -> "0000"
    }
}
