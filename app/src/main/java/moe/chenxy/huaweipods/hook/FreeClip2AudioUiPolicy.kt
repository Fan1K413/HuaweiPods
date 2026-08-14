package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import moe.chenxy.huaweipods.pods.HuaweiEqualizerCodec
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

/** FreeClip 2 在系统宿主界面中展示的、可按设备持久化的音频状态。 */
internal data class FreeClip2AudioUiState(
    val spatialMode: FreeClip2SpatialAudioMode = FreeClip2SpatialAudioMode.OFF,
    val spatialScene: FreeClip2SpatialScene = FreeClip2SpatialScene.DEFAULT,
    val soundEffect: FreeClip2SoundEffect = FreeClip2SoundEffect.DEFAULT,
    val equalizerPresetId: Int = 0x64,
    val equalizerName: String = "HuaweiPods",
    val equalizerGains: List<Int> = List(HuaweiEqualizerCodec.BAND_COUNT) { 0 },
) {
    fun mergeExtraValues(
        spatialModeValue: String?,
        spatialSceneValue: String?,
        soundEffectValue: String?,
        equalizerPresetIdValue: Int? = null,
        equalizerNameValue: String? = null,
        equalizerGainsValue: List<Int>? = null,
    ): FreeClip2AudioUiState = copy(
        spatialMode = FreeClip2SpatialAudioMode.fromExtraValue(spatialModeValue) ?: spatialMode,
        spatialScene = FreeClip2SpatialScene.fromExtraValue(spatialSceneValue) ?: spatialScene,
        soundEffect = FreeClip2SoundEffect.fromExtraValue(soundEffectValue) ?: soundEffect,
        equalizerPresetId = equalizerPresetIdValue
            ?.takeIf { it in 0x64..0x66 }
            ?: equalizerPresetId,
        equalizerName = equalizerNameValue
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: equalizerName,
        equalizerGains = equalizerGainsValue
            ?.takeIf(::isValidFreeClip2EqualizerGains)
            ?: equalizerGains,
    )
}

internal fun freeClip2SettingsSoundEffects(): List<FreeClip2SoundEffect> =
    FreeClip2SoundEffect.selectableEntries + FreeClip2SoundEffect.CUSTOM

internal fun shouldShowFreeClip2EqualizerEntry(effect: FreeClip2SoundEffect): Boolean =
    effect == FreeClip2SoundEffect.CUSTOM

internal fun isValidFreeClip2EqualizerGains(gains: List<Int>): Boolean =
    gains.size == HuaweiEqualizerCodec.BAND_COUNT &&
        gains.all { it in HuaweiEqualizerCodec.GAIN_RANGE }

internal fun parseFreeClip2EqualizerGains(value: String?): List<Int>? {
    val gains = value
        ?.split(',')
        ?.map { it.toIntOrNull() ?: return null }
        ?: return null
    return gains.takeIf(::isValidFreeClip2EqualizerGains)
}

internal fun freeClip2AudioPreferencePrefix(address: String?, name: String?): String? {
    val identity = address
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.uppercase()
        ?: name?.trim()?.takeIf(String::isNotEmpty)?.let { "name:$it" }
        ?: return null
    return "freeclip2_audio_${identity}_"
}

internal data class FreeClip2AudioSelection(
    val kind: String,
    val value: String,
)

/** Programmatic card rendering may re-enter the host click handler and must never write the device. */
internal fun shouldDispatchFreeClip2AudioSelection(internalRenderDepth: Int): Boolean =
    internalRenderDepth <= 0

/** Deduplicates the two system-host Hook entry points while a device confirmation is pending. */
internal class FreeClip2AudioPendingGate(
    private val timeoutMs: Long = 5_000L,
) {
    private var pending: FreeClip2AudioSelection? = null
    private var pendingSinceMs = 0L

    fun tryBegin(kind: String, value: String, nowMs: Long): Boolean {
        val next = FreeClip2AudioSelection(kind, value)
        if (pending == next && nowMs - pendingSinceMs in 0 until timeoutMs) return false
        pending = next
        pendingSinceMs = nowMs
        return true
    }

    fun observeConfirmed(
        spatialModeValue: String?,
        spatialSceneValue: String?,
        soundEffectValue: String?,
    ) {
        val current = pending ?: return
        val observed = when (current.kind) {
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE -> spatialModeValue != null
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE -> spatialSceneValue != null
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT -> soundEffectValue != null
            else -> true
        }
        if (observed) clear()
    }

    fun clear() {
        pending = null
        pendingSinceMs = 0L
    }

    internal fun current(): FreeClip2AudioSelection? = pending
}
