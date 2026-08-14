package moe.chenxy.huaweipods.pods

import java.util.UUID

internal object SmartAudioFreeClip2BridgePolicy {
    const val SMART_AUDIO_PACKAGE = "com.huawei.smartaudio"
    const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
    const val MODULE_PACKAGE = "moe.chenxy.huaweipods"
    const val SETTINGS_PACKAGE = "com.android.settings"

    private val bluetoothAddressPattern = Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$")

    fun normalizeAddress(value: String?): String? = value
        ?.trim()
        ?.uppercase()
        ?.takeIf(bluetoothAddressPattern::matches)

    fun normalizeNonce(value: String?): String? = value
        ?.trim()
        ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }

    /** 智慧音频公开 API 的枚举是 0=关闭、1=头部跟踪、2=固定。 */
    fun officialModeFor(mode: FreeClip2SpatialAudioMode): Int = when (mode) {
        FreeClip2SpatialAudioMode.OFF -> 0
        FreeClip2SpatialAudioMode.FIXED -> 2
        FreeClip2SpatialAudioMode.HEAD_TRACKING -> 1
    }

    fun modeFromOfficial(value: Int): FreeClip2SpatialAudioMode? = when (value) {
        0 -> FreeClip2SpatialAudioMode.OFF
        1 -> FreeClip2SpatialAudioMode.HEAD_TRACKING
        2 -> FreeClip2SpatialAudioMode.FIXED
        else -> null
    }

    /** 智慧音频 EQ 类型；模块未提供的官方/自定义类型只读显示为 CUSTOM。 */
    fun soundEffectFromOfficial(value: Int): FreeClip2SoundEffect = when (value) {
        0, 1, 5 -> FreeClip2SoundEffect.DEFAULT
        3 -> FreeClip2SoundEffect.TREBLE_ENHANCE
        9 -> FreeClip2SoundEffect.CLEAR_VOICE
        10, 16 -> FreeClip2SoundEffect.SPORT_ENHANCE
        else -> FreeClip2SoundEffect.CUSTOM
    }

    fun isTrustedRequestSender(packageName: String?): Boolean =
        packageName == BLUETOOTH_PACKAGE

    fun isTrustedEqualizerRequestSender(packageName: String?): Boolean =
        packageName == MODULE_PACKAGE ||
            packageName == BLUETOOTH_PACKAGE

    fun isTrustedResultSender(packageName: String?): Boolean =
        packageName == SMART_AUDIO_PACKAGE
}
