package moe.chenxy.huaweipods.pods

enum class HuaweiDeviceRoute {
    HUAWEI_FREEBUDS3,
    HUAWEI_FREEBUDS5,
    HUAWEI_FREEBUDS6I,
    HUAWEI_FREECLIP,
    HUAWEI_FREECLIP2,
    UNSUPPORTED,
}

val HuaweiDeviceRoute.isSupported: Boolean
    get() = this != HuaweiDeviceRoute.UNSUPPORTED

val HuaweiDeviceRoute.supportsAnc: Boolean
    get() = this == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS5 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I

val HuaweiDeviceRoute.supportsRfcommBattery: Boolean
    get() = this == HuaweiDeviceRoute.HUAWEI_FREEBUDS5 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
        this == HuaweiDeviceRoute.HUAWEI_FREECLIP ||
        this == HuaweiDeviceRoute.HUAWEI_FREECLIP2

private val enabledExperimentalRoute: HuaweiDeviceRoute? = null

fun detectHuaweiDeviceRoute(deviceName: String?): HuaweiDeviceRoute {
    val route = when (deviceName?.let(::normalizeDeviceName).orEmpty()) {
        "huaweifreebuds3", "freebuds3" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS3
        "huaweifreebuds5", "freebuds5" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS5
        "huaweifreebuds6i", "freebuds6i" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS6I
        "huaweifreeclip", "freeclip" -> HuaweiDeviceRoute.HUAWEI_FREECLIP
        "huaweifreeclip2", "freeclip2" -> HuaweiDeviceRoute.HUAWEI_FREECLIP2
        else -> HuaweiDeviceRoute.UNSUPPORTED
    }
    return route.takeIf {
        it == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 || it == enabledExperimentalRoute
    } ?: HuaweiDeviceRoute.UNSUPPORTED
}

private fun normalizeDeviceName(deviceName: String): String {
    return deviceName.lowercase().filter { it.isLetterOrDigit() }
}
