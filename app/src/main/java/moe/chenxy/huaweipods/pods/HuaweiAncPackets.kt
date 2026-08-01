package moe.chenxy.huaweipods.pods

/** 已验证的协议指令；实验路由可能包含注明待实机复核的同协议族指令。 */
internal object HuaweiAncPackets {
    private val huaweiBatteryQuery =
        packet(0x5A, 0x00, 0x09, 0x00, 0x01, 0x08, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0xFB, 0xB9)
    private val freeBuds3Enabled = mapOf(
        false to packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x04, 0x01, 0x01, 0x00, 0x68, 0x21),
        true to packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x04, 0x01, 0x01, 0x01, 0x78, 0x00),
    )
    // FreeBuds Pro 3 的开启包已实抓；关闭包沿用同协议族格式，需在首轮实机测试中重点复核。
    private val modernFreeBudsEnabled = mapOf(
        false to packet(0x5A, 0x00, 0x07, 0x00, 0x2B, 0x04, 0x01, 0x02, 0x00, 0x00, 0xD2, 0x2D),
        true to packet(0x5A, 0x00, 0x07, 0x00, 0x2B, 0x04, 0x01, 0x02, 0x01, 0xFF, 0xFF, 0xEC),
    )
    private val freeBuds3Levels = listOf(
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x00, 0x27, 0x13),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x01, 0x37, 0x32),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x02, 0x07, 0x51),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x03, 0x17, 0x70),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x04, 0x67, 0x97),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x05, 0x77, 0xB6),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x06, 0x47, 0xD5),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x07, 0x57, 0xF4),
        packet(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x08, 0xA6, 0x1B),
    )

    fun enabled(route: HuaweiDeviceRoute, enabled: Boolean): ByteArray? = when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS3 -> freeBuds3Enabled[enabled]
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I -> modernFreeBudsEnabled[enabled]
        HuaweiDeviceRoute.HUAWEI_FREECLIP,
        HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        HuaweiDeviceRoute.HUAWEI_EYEWEAR,
        HuaweiDeviceRoute.UNSUPPORTED -> null
    }?.copyOf()

    fun level(route: HuaweiDeviceRoute, level: Int): ByteArray? = when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS3 -> freeBuds3Levels[level.coerceIn(0, freeBuds3Levels.lastIndex)]
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        HuaweiDeviceRoute.HUAWEI_FREECLIP,
        HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        HuaweiDeviceRoute.HUAWEI_EYEWEAR,
        HuaweiDeviceRoute.UNSUPPORTED -> null
    }?.copyOf()

    fun batteryQuery(route: HuaweiDeviceRoute): ByteArray? = when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        HuaweiDeviceRoute.HUAWEI_FREECLIP,
        HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        HuaweiDeviceRoute.HUAWEI_EYEWEAR -> huaweiBatteryQuery
        HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
        HuaweiDeviceRoute.UNSUPPORTED -> null
    }?.copyOf()

    private fun packet(vararg values: Int): ByteArray =
        values.map(Int::toByte).toByteArray()
}
