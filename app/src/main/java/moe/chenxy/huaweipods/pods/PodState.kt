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

/** 尚未按具体机型确认档位；不得作为 Huawei 协议值下发。 */
internal const val UNKNOWN_HUAWEI_ANC_SUBMODE = -1

/** 降噪档位的业务语义；协议值必须通过具体机型的 [HuaweiAncLevelOption] 转换。 */
enum class HuaweiAncLevel {
    ADAPTIVE,
    LIGHT,
    BALANCED,
    DEEP,
}

enum class HuaweiTransparencyMode {
    STANDARD,
    VOICE,
}
