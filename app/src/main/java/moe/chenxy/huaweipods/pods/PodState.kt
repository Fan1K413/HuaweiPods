package moe.chenxy.huaweipods.pods

/** 噪声控制状态。UNKNOWN 用于连接后尚未收到耳机确认的短暂阶段。 */
enum class NoiseControlMode(val broadcastStatus: Int) {
    UNKNOWN(0),
    OFF(1),
    NOISE_CANCELLATION(2),
    TRANSPARENCY(3),
    ;

    companion object {
        fun fromBroadcastStatus(status: Int): NoiseControlMode =
            entries.firstOrNull { it.broadcastStatus == status } ?: UNKNOWN
    }
}

fun NoiseControlMode.isKnown(): Boolean = this != NoiseControlMode.UNKNOWN

fun NoiseControlMode.isNoiseCancellation(): Boolean = this == NoiseControlMode.NOISE_CANCELLATION

fun NoiseControlMode.isTransparency(): Boolean = this == NoiseControlMode.TRANSPARENCY

internal data class HuaweiAncState(
    val mode: NoiseControlMode,
    val subMode: Int? = null,
)

/** FreeBuds 6i 与 FreeBuds Pro 3 共用的四档降噪语义。 */
enum class HuaweiAncLevel(val protocolValue: Int) {
    ADAPTIVE(0x01),
    LIGHT(0x00),
    BALANCED(0x02),
    DEEP(0x03),
    ;

    companion object {
        fun fromProtocolValue(value: Int): HuaweiAncLevel? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

enum class HuaweiTransparencyMode {
    STANDARD,
    VOICE,
}
