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
    private val SPATIAL_AUDIO_STATE_QUERY = hex("5A000A002BB4010118020003009B3F")
    private val SOUND_EFFECT_STATE_QUERY = hex("5A0005002B4A02008C46")

    fun setBooleanFeature(
        context: Context,
        device: BluetoothDevice,
        feature: FreeClip2BooleanFeature,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        if (feature == FreeClip2BooleanFeature.LOW_LATENCY) {
            HuaweiLowLatencyController.setEnabled(
                context,
                device,
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                enabled,
                onComplete,
            )
            return
        }
        send(
            context = context,
            device = device,
            packet = feature.packet(enabled),
            description = "freeclip2 ${feature.extraValue} enabled=$enabled",
            onComplete = onComplete,
        )
    }

    fun requestBooleanFeatureState(
        context: Context,
        device: BluetoothDevice,
        feature: FreeClip2BooleanFeature,
        keepSocket: Boolean = false,
        onState: (Boolean?) -> Unit,
    ) {
        val parser = { response: ByteArray -> parseBooleanFeatureState(feature, response) }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = feature.stateQueryPacket(),
            description = "freeclip2 ${feature.extraValue} state-query",
            keepSocket = keepSocket,
            responseWindowMs = 1_000L,
            responseComplete = { parser(it) != null },
            onComplete = { success -> if (!success) onState(null) },
            onResponse = { onState(parser(it)) },
        )
    }

    fun setSpatialAudioMode(
        context: Context,
        device: BluetoothDevice,
        mode: FreeClip2SpatialAudioMode,
        onComplete: ((Boolean) -> Unit)? = null,
        onState: ((FreeClip2AudioState?) -> Unit)? = null,
    ) = transact(
        context = context,
        device = device,
        packet = mode.packet(),
        description = "freeclip2 spatial mode=${mode.extraValue}",
        parser = ::parseSpatialAudioState,
        onComplete = onComplete,
        onState = onState,
    )

    fun setSpatialScene(
        context: Context,
        device: BluetoothDevice,
        scene: FreeClip2SpatialScene,
        onComplete: ((Boolean) -> Unit)? = null,
        onState: ((FreeClip2AudioState?) -> Unit)? = null,
    ) = transact(
        context = context,
        device = device,
        packet = scene.packet(),
        description = "freeclip2 spatial scene=${scene.extraValue}",
        parser = ::parseSpatialAudioState,
        onComplete = onComplete,
        onState = onState,
    )

    fun setSoundEffect(
        context: Context,
        device: BluetoothDevice,
        effect: FreeClip2SoundEffect,
        onComplete: ((Boolean) -> Unit)? = null,
        onState: ((FreeClip2AudioState?) -> Unit)? = null,
    ) = transact(
        context = context,
        device = device,
        packet = effect.packet(),
        description = "freeclip2 sound effect=${effect.extraValue}",
        parser = ::parseSoundEffectState,
        onComplete = onComplete,
        onState = onState,
    )

    fun requestSpatialAudioState(
        context: Context,
        device: BluetoothDevice,
        onState: (FreeClip2AudioState?) -> Unit,
    ) {
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = spatialAudioStateQueryPacket(),
            description = "freeclip2 spatial-audio-state-query",
            onResponse = { response -> onState(parseSpatialAudioState(response)) },
        )
    }

    fun requestSoundEffectState(
        context: Context,
        device: BluetoothDevice,
        onState: (FreeClip2AudioState?) -> Unit,
    ) {
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = soundEffectStateQueryPacket(),
            description = "freeclip2 sound-effect-state-query",
            onResponse = { response -> onState(parseSoundEffectState(response)) },
        )
    }

    fun spatialAudioStateQueryPacket(): ByteArray = SPATIAL_AUDIO_STATE_QUERY.copyOf()

    fun soundEffectStateQueryPacket(): ByteArray = SOUND_EFFECT_STATE_QUERY.copyOf()

    fun parseBooleanFeatureState(
        feature: FreeClip2BooleanFeature,
        stream: ByteArray,
    ): Boolean? = when (feature) {
        FreeClip2BooleanFeature.WEAR_DETECTION ->
            parseLatestBooleanState(stream, command = 0x11, field = 0x01)
        FreeClip2BooleanFeature.DROP_REMINDER ->
            parseLatestB4BooleanState(stream, featureId = 0x07)
        FreeClip2BooleanFeature.ADAPTIVE_VOLUME ->
            parseLatestB4BooleanState(stream, featureId = 0x02)
        FreeClip2BooleanFeature.HEAD_MOTION_CONTROL ->
            parseLatestB4BooleanState(stream, featureId = 0x0B)
        FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY ->
            parseLatestBooleanState(stream, command = 0x88, field = 0x01)
        FreeClip2BooleanFeature.LOW_LATENCY ->
            parseLatestBooleanState(stream, command = 0x6C, field = 0x02)
        FreeClip2BooleanFeature.DUAL_DEVICE ->
            parseLatestBooleanState(stream, command = 0x2F, field = 0x01)
        FreeClip2BooleanFeature.CASE_PROMPT_SOUND ->
            parseLatestBooleanState(stream, command = 0xB1, field = 0x02)
    }

    /**
     * Parses the latest verified spatial-audio frame from a possibly concatenated RFCOMM read.
     * Unknown enum values are rejected instead of being exposed as a misleading UI state.
     */
    fun parseSpatialAudioState(stream: ByteArray): FreeClip2AudioState? {
        var latest: FreeClip2AudioState? = null
        frames(stream).forEach { frame ->
            val marker = frame.indexOfSequence(SPATIAL_AUDIO_RESPONSE_PREFIX, startIndex = 4)
            if (marker < 0 || marker + SPATIAL_AUDIO_RESPONSE_SIZE > frame.size - CHECKSUM_SIZE) {
                return@forEach
            }
            val mode = FreeClip2SpatialAudioMode.fromStateReportValue(frame.u8(marker + 7))
                ?: return@forEach
            val scene = FreeClip2SpatialScene.fromProtocolValue(frame.u8(marker + 10))
                ?: return@forEach
            latest = FreeClip2AudioState(mode = mode, scene = scene)
        }
        return latest
    }

    /** Parses the latest verified built-in sound-effect frame from a concatenated RFCOMM read. */
    fun parseSoundEffectState(stream: ByteArray): FreeClip2AudioState? {
        HuaweiEqualizerCodec.parseState(stream)?.let { equalizer ->
            val effect = FreeClip2SoundEffect.fromProtocolValue(equalizer.selectedId)
                ?: FreeClip2SoundEffect.CUSTOM.takeIf { equalizer.isCustom }
            if (effect != null) {
                return FreeClip2AudioState(effect = effect, equalizer = equalizer)
            }
        }
        var latest: FreeClip2AudioState? = null
        frames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0x4A) return@forEach
            val marker = frame.indexOfSequence(SOUND_EFFECT_RESPONSE_FIELD, startIndex = 6)
            if (marker < 0 || marker + SOUND_EFFECT_RESPONSE_SIZE > frame.size - CHECKSUM_SIZE) {
                return@forEach
            }
            val effect = FreeClip2SoundEffect.fromProtocolValue(frame.u8(marker + 2))
                ?: return@forEach
            latest = FreeClip2AudioState(effect = effect)
        }
        return latest
    }

    /** Parses both response types when the transport returns them in the same byte stream. */
    fun parseAudioState(stream: ByteArray): FreeClip2AudioState? =
        mergeFreeClip2AudioState(
            current = parseSpatialAudioState(stream),
            update = parseSoundEffectState(stream),
        )

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

    private fun transact(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        parser: (ByteArray) -> FreeClip2AudioState?,
        onComplete: ((Boolean) -> Unit)?,
        onState: ((FreeClip2AudioState?) -> Unit)?,
    ) {
        if (onState == null) {
            send(context, device, packet, description, onComplete)
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = packet.copyOf(),
            description = description,
            responseWindowMs = 1_000L,
            responseComplete = { parser(it) != null },
            onComplete = onComplete,
            onResponse = { onState(parser(it)) },
        )
    }
}

data class FreeClip2AudioState(
    val mode: FreeClip2SpatialAudioMode? = null,
    val scene: FreeClip2SpatialScene? = null,
    val effect: FreeClip2SoundEffect? = null,
    val equalizer: HuaweiEqualizerState? = null,
)

fun mergeFreeClip2AudioState(
    current: FreeClip2AudioState?,
    update: FreeClip2AudioState?,
): FreeClip2AudioState? {
    if (current == null) return update
    if (update == null) return current
    return FreeClip2AudioState(
        mode = update.mode ?: current.mode,
        scene = update.scene ?: current.scene,
        effect = update.effect ?: current.effect,
        equalizer = update.equalizer ?: current.equalizer,
    )
}

enum class FreeClip2BooleanFeature(
    val extraValue: String,
    private val disabledPacket: ByteArray,
    private val enabledPacket: ByteArray,
    private val queryPacket: ByteArray,
) {
    WEAR_DETECTION(
        "wear_detection",
        hex("5A0006002B10010100B977"),
        hex("5A0006002B10010101A956"),
        hex("5A0005002B110100772A"),
    ),
    DROP_REMINDER(
        "drop_reminder",
        hex("5A0009002BB4010107020100AFA4"),
        hex("5A0009002BB4010107020101BF85"),
        hex("5A0008002BB40101070200DDE9"),
    ),
    ADAPTIVE_VOLUME(
        "adaptive_volume",
        hex("5A0009002BB401010202010013E1"),
        hex("5A0009002BB401010202010103C0"),
        hex("5A0008002BB401010202003619"),
    ),
    HEAD_MOTION_CONTROL(
        "head_motion_control",
        hex("5A0009002BB401010B020100E096"),
        hex("5A0009002BB401010B020101F0B7"),
        hex("5A0006002BB401010B289B"),
    ),
    SOUND_QUALITY_PRIORITY(
        "sound_quality_priority",
        hex("5A0006002B870101002EC5"),
        hex("5A0006002B870101013EE4"),
        hex("5A0005002B8801009182"),
    ),
    LOW_LATENCY(
        "low_latency",
        hex("5A0006002B6C010100B430"),
        hex("5A0006002B6C010101A411"),
        hex("5A0005002B6C0200B820"),
    ),
    DUAL_DEVICE(
        "dual_device",
        hex("5A0006002B2E01010037C4"),
        hex("5A0006002B2E01010127E5"),
        hex("5A0005002B2F0100A98E"),
    ),
    CASE_PROMPT_SOUND(
        "case_prompt_sound",
        hex("5A0006002BB101010025B5"),
        hex("5A0006002BB10101013594"),
        hex("5A0007002BB1020003007FAB"),
    );

    fun packet(enabled: Boolean): ByteArray =
        (if (enabled) enabledPacket else disabledPacket).copyOf()

    fun stateQueryPacket(): ByteArray = queryPacket.copyOf()

    companion object {
        fun fromExtraValue(value: String?): FreeClip2BooleanFeature? =
            entries.firstOrNull { it.extraValue == value }
    }
}

/**
 * FreeClip 2 的 AAM 空间音频协议：0=关闭、1=头部跟踪、2=固定。
 * 智慧音频的独立 MBB API 使用相同顺序，桥接层仍保留显式转换。
 */
enum class FreeClip2SpatialAudioMode(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray,
) {
    OFF("off", 0x00, hex("5A0009002BB401011802010060ED")),
    FIXED("fixed", 0x02, hex("5A0009002BB401011802010240AF")),
    HEAD_TRACKING("head_tracking", 0x01, hex("5A0009002BB401011802010170CC"));

    fun packet(): ByteArray = packetBytes.copyOf()

    companion object {
        fun fromExtraValue(value: String?): FreeClip2SpatialAudioMode? =
            entries.firstOrNull { it.extraValue == value }

        fun fromProtocolValue(value: Int): FreeClip2SpatialAudioMode? =
            entries.firstOrNull { it.protocolValue == value }

        /** 耳机 AAM 状态回报与写命令一致：1=头部跟踪、2=固定。 */
        fun fromStateReportValue(value: Int): FreeClip2SpatialAudioMode? = when (value) {
            0 -> OFF
            1 -> HEAD_TRACKING
            2 -> FIXED
            else -> null
        }
    }
}

enum class FreeClip2SpatialScene(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray,
) {
    DEFAULT("default", 0x00, hex("5A0009002BB401011803010057DD")),
    AUDIO_THEATER("audio_theater", 0x01, hex("5A0009002BB401011803010147FC")),
    CINEMA("cinema", 0x02, hex("5A0009002BB4010118030102779F")),
    CONCERT_HALL("concert_hall", 0x03, hex("5A0009002BB401011803010367BE"));

    fun packet(): ByteArray = packetBytes.copyOf()

    companion object {
        fun fromExtraValue(value: String?): FreeClip2SpatialScene? =
            entries.firstOrNull { it.extraValue == value }

        fun fromProtocolValue(value: Int): FreeClip2SpatialScene? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

enum class FreeClip2SoundEffect(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray?,
) {
    DEFAULT("default", 0x01, hex("5A0006002B490101012F1A")),
    SPORT_ENHANCE("sport_enhance", 0x0A, hex("5A0006002B4901010A9E71")),
    TREBLE_ENHANCE("treble_enhance", 0x03, hex("5A0006002B490101030F58")),
    CLEAR_VOICE("clear_voice", 0x09, hex("5A0006002B49010109AE12")),
    /** 官方 App 的自定义或模块尚未提供的音效；没有固定包，需通过均衡器接口写入。 */
    CUSTOM("custom", -1, null);

    val isSelectable: Boolean
        get() = packetBytes != null

    fun packet(): ByteArray = requireNotNull(packetBytes) {
        "The custom/unsupported sound effect has no writable packet"
    }.copyOf()

    companion object {
        val selectableEntries: List<FreeClip2SoundEffect> = entries.filter { it.isSelectable }

        fun fromExtraValue(value: String?): FreeClip2SoundEffect? =
            entries.firstOrNull { it.extraValue == value }

        fun fromProtocolValue(value: Int): FreeClip2SoundEffect? =
            selectableEntries.firstOrNull { it.protocolValue == value }
    }
}

private const val HEADER_SIZE = 5
private const val CHECKSUM_SIZE = 2
private const val SPATIAL_AUDIO_RESPONSE_SIZE = 11
private const val SOUND_EFFECT_RESPONSE_SIZE = 3
private val SPATIAL_AUDIO_RESPONSE_PREFIX = hex("2BB40101180201")
private val SOUND_EFFECT_RESPONSE_FIELD = hex("0201")

private fun parseLatestBooleanState(
    stream: ByteArray,
    command: Int,
    field: Int,
): Boolean? {
    var latest: Boolean? = null
    frames(stream).forEach { frame ->
        if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != command) return@forEach
        frame.booleanField(field)?.let { latest = it }
    }
    return latest
}

private fun parseLatestB4BooleanState(stream: ByteArray, featureId: Int): Boolean? {
    var latest: Boolean? = null
    frames(stream).forEach { frame ->
        if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0xB4) return@forEach
        if (frame.tlvValue(0x01)?.singleOrNull()?.toInt()?.and(0xFF) != featureId) {
            return@forEach
        }
        frame.booleanField(0x02)?.let { latest = it }
    }
    return latest
}

private fun ByteArray.booleanField(field: Int): Boolean? =
    when (tlvValue(field)?.singleOrNull()?.toInt()?.and(0xFF)) {
        0x00 -> false
        0x01 -> true
        else -> null
    }

private fun ByteArray.tlvValue(field: Int): ByteArray? {
    val endExclusive = size - CHECKSUM_SIZE
    var offset = 6
    while (offset + 2 <= endExclusive) {
        val type = u8(offset)
        val length = u8(offset + 1)
        val valueStart = offset + 2
        val valueEnd = valueStart + length
        if (valueEnd > endExclusive) return null
        if (type == field) return copyOfRange(valueStart, valueEnd)
        offset = valueEnd
    }
    return null
}

private fun frames(stream: ByteArray): Sequence<ByteArray> = sequence {
    var offset = 0
    while (offset + HEADER_SIZE <= stream.size) {
        if (stream.u8(offset) != 0x5A || stream.u8(offset + 1) != 0x00) {
            offset++
            continue
        }
        val payloadLength = stream.u8(offset + 2) or (stream.u8(offset + 3) shl 8)
        val frameSize = HEADER_SIZE + payloadLength
        if (frameSize <= HEADER_SIZE || offset + frameSize > stream.size) {
            offset++
            continue
        }
        val frame = stream.copyOfRange(offset, offset + frameSize)
        if (!frame.hasValidCrc16Xmodem()) {
            // Advance one byte so a following valid frame can still be recovered when the corrupt
            // length field overlaps it.
            offset++
            continue
        }
        yield(frame)
        offset += frameSize
    }
}

/** Huawei RFCOMM frames append CRC16/XMODEM as high byte followed by low byte. */
private fun ByteArray.hasValidCrc16Xmodem(): Boolean {
    if (size < HEADER_SIZE + CHECKSUM_SIZE) return false
    var crc = 0
    for (index in 0 until size - CHECKSUM_SIZE) {
        crc = crc xor (u8(index) shl 8)
        repeat(8) {
            crc = if ((crc and 0x8000) != 0) {
                (crc shl 1) xor 0x1021
            } else {
                crc shl 1
            }
            crc = crc and 0xFFFF
        }
    }
    return u8(size - 2) == (crc shr 8) && u8(size - 1) == (crc and 0xFF)
}

private fun ByteArray.indexOfSequence(needle: ByteArray, startIndex: Int): Int {
    if (needle.isEmpty()) return startIndex.coerceIn(0, size)
    val lastStart = size - needle.size
    for (index in startIndex.coerceAtLeast(0)..lastStart) {
        if (needle.indices.all { offset -> this[index + offset] == needle[offset] }) return index
    }
    return -1
}

private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.u8OrNull(index: Int): Int? = getOrNull(index)?.toInt()?.and(0xFF)

private fun hex(value: String): ByteArray = value.chunked(2)
    .map { it.toInt(16).toByte() }
    .toByteArray()
