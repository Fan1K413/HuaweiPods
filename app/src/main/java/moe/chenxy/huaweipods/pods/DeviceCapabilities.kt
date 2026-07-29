package moe.chenxy.huaweipods.pods

enum class HuaweiDeviceRoute {
    HUAWEI_FREEBUDS3,
    HUAWEI_FREEBUDS5,
    HUAWEI_FREEBUDS6I,
    HUAWEI_FREEBUDS_PRO4,
    HUAWEI_FREEBUDS7I,
    HUAWEI_FREECLIP,
    HUAWEI_FREECLIP2,
    HUAWEI_EYEWEAR,
    UNSUPPORTED,
}

val HuaweiDeviceRoute.isSupported: Boolean
    get() = this != HuaweiDeviceRoute.UNSUPPORTED

val HuaweiDeviceRoute.supportsAnc: Boolean
    get() = this == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS5 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS7I

val HuaweiDeviceRoute.supportsRfcommBattery: Boolean
    get() = this == HuaweiDeviceRoute.HUAWEI_FREEBUDS5 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS7I ||
        this == HuaweiDeviceRoute.HUAWEI_FREECLIP ||
        this == HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
        this == HuaweiDeviceRoute.HUAWEI_EYEWEAR

val HuaweiDeviceRoute.hasChargingCase: Boolean
    get() = this == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS5 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 ||
        this == HuaweiDeviceRoute.HUAWEI_FREEBUDS7I ||
        this == HuaweiDeviceRoute.HUAWEI_FREECLIP ||
        this == HuaweiDeviceRoute.HUAWEI_FREECLIP2

private val enabledExperimentalRoute: HuaweiDeviceRoute? = null

fun detectHuaweiDeviceRoute(deviceName: String?): HuaweiDeviceRoute {
    val route = when (deviceName?.let(::normalizeDeviceName).orEmpty()) {
        "huaweifreebuds3", "freebuds3" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS3
        "huaweifreebuds5", "freebuds5" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS5
        "huaweifreebuds6i", "freebuds6i" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS6I
        "huaweifreebudspro4", "freebudspro4" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4
        "huaweifreebuds7i", "freebuds7i" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS7I
        "huaweifreeclip", "freeclip" -> HuaweiDeviceRoute.HUAWEI_FREECLIP
        "huaweifreeclip2", "freeclip2" -> HuaweiDeviceRoute.HUAWEI_FREECLIP2
        "huaweieyewear" -> HuaweiDeviceRoute.HUAWEI_EYEWEAR
        else -> HuaweiDeviceRoute.UNSUPPORTED
    }
    return route.takeIf {
        it == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 || it == enabledExperimentalRoute
    } ?: HuaweiDeviceRoute.UNSUPPORTED
}

private fun normalizeDeviceName(deviceName: String): String {
    return deviceName.lowercase().filter { it.isLetterOrDigit() }
}
