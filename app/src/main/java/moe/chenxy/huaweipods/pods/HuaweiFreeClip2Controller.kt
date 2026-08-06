package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothDevice
import android.content.Context

/**
 * FreeClip 2 commands verified against the guided Huawei Audio capture from 2026-07-29.
 *
 * This controller intentionally contains only direct device settings. Account-backed features,
 * firmware operations and case sound file transfers are not replayed here.
 */
object HuaweiFreeClip2Controller {
    fun setBooleanFeature(
        context: Context,
        device: BluetoothDevice,
        feature: FreeClip2BooleanFeature,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        packet = feature.packet(enabled),
        description = "freeclip2 ${feature.extraValue} enabled=$enabled",
        onComplete = onComplete,
    )

    fun setSpatialAudioMode(
        context: Context,
        device: BluetoothDevice,
        mode: FreeClip2SpatialAudioMode,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(context, device, mode.packet(), "freeclip2 spatial mode=${mode.extraValue}", onComplete)

    fun setSpatialScene(
        context: Context,
        device: BluetoothDevice,
        scene: FreeClip2SpatialScene,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(context, device, scene.packet(), "freeclip2 spatial scene=${scene.extraValue}", onComplete)

    fun setSoundEffect(
        context: Context,
        device: BluetoothDevice,
        effect: FreeClip2SoundEffect,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(context, device, effect.packet(), "freeclip2 sound effect=${effect.extraValue}", onComplete)

    private fun send(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        onComplete: ((Boolean) -> Unit)?,
    ) {
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = packet.copyOf(),
            description = description,
            onComplete = onComplete,
        )
    }
}

enum class FreeClip2BooleanFeature(
    val extraValue: String,
    private val disabledPacket: ByteArray,
    private val enabledPacket: ByteArray,
) {
    WEAR_DETECTION(
        "wear_detection",
        hex("5A0006002B10010100B977"),
        hex("5A0006002B10010101A956"),
    ),
    DROP_REMINDER(
        "drop_reminder",
        hex("5A0009002BB4010107020100AFA4"),
        hex("5A0009002BB4010107020101BF85"),
    ),
    ADAPTIVE_VOLUME(
        "adaptive_volume",
        hex("5A0009002BB401010202010013E1"),
        hex("5A0009002BB401010202010103C0"),
    ),
    HEAD_MOTION_CONTROL(
        "head_motion_control",
        hex("5A0009002BB401010B020100E096"),
        hex("5A0009002BB401010B020101F0B7"),
    ),
    SOUND_QUALITY_PRIORITY(
        "sound_quality_priority",
        hex("5A0006002B870101002EC5"),
        hex("5A0006002B870101013EE4"),
    ),
    LOW_LATENCY(
        "low_latency",
        hex("5A0006002B6C010100B430"),
        hex("5A0006002B6C010101A411"),
    ),
    DUAL_DEVICE(
        "dual_device",
        hex("5A0006002B2E01010037C4"),
        hex("5A0006002B2E01010127E5"),
    ),
    CASE_PROMPT_SOUND(
        "case_prompt_sound",
        hex("5A0006002BB101010025B5"),
        hex("5A0006002BB10101013594"),
    );

    fun packet(enabled: Boolean): ByteArray =
        (if (enabled) enabledPacket else disabledPacket).copyOf()
}

enum class FreeClip2SpatialAudioMode(val extraValue: String, private val packetBytes: ByteArray) {
    OFF("off", hex("5A0009002BB401011802010060ED")),
    FIXED("fixed", hex("5A0009002BB401011802010170CC")),
    HEAD_TRACKING("head_tracking", hex("5A0009002BB401011802010240AF"));

    fun packet(): ByteArray = packetBytes.copyOf()
}

enum class FreeClip2SpatialScene(val extraValue: String, private val packetBytes: ByteArray) {
    DEFAULT("default", hex("5A0009002BB401011803010057DD")),
    AUDIO_THEATER("audio_theater", hex("5A0009002BB401011803010147FC")),
    CINEMA("cinema", hex("5A0009002BB4010118030102779F")),
    CONCERT_HALL("concert_hall", hex("5A0009002BB401011803010367BE"));

    fun packet(): ByteArray = packetBytes.copyOf()
}

/** The capture exposes three built-in presets but does not include their UI labels. */
enum class FreeClip2SoundEffect(val extraValue: String, private val packetBytes: ByteArray) {
    PRESET_1("preset_1", hex("5A0006002B4901010A9E71")),
    PRESET_2("preset_2", hex("5A0006002B490101030F58")),
    PRESET_3("preset_3", hex("5A0006002B49010109AE12"));

    fun packet(): ByteArray = packetBytes.copyOf()
}

private fun hex(value: String): ByteArray = value.chunked(2)
    .map { it.toInt(16).toByte() }
    .toByteArray()
