package moe.chenxy.huaweipods.pods

import java.nio.charset.StandardCharsets

internal data class HuaweiDeviceInfoIdentity(
    val modelId: String,
    val subModelId: String,
)

/** 从耳机 01/07 DeviceInfo 应答中严格读取官方资源机型与当前配色。 */
internal object HuaweiDeviceInfoIdentityParser {
    private const val FRAME_MAGIC = 0x5A
    private const val FRAME_VERSION = 0x00
    private const val DEVICE_INFO_SERVICE = 0x01
    private const val DEVICE_INFO_COMMAND = 0x07
    private const val FRAME_PREFIX_BYTES = 6
    private const val FRAME_CRC_BYTES = 2
    private const val TYPE_MODEL_CODE = 0x02
    private const val TYPE_MODEL = 0x0A
    private const val TYPE_MODEL_PREFIX = 0x0F
    private const val TYPE_SUB_MODEL = 0x19
    private const val MAX_MODEL_VALUE_BYTES = 64
    private const val MAX_STREAM_BYTES = 64 * 1024
    private const val COMMON_RESOURCE_MODEL_ID = "00000A"
    private val modelValueRegex = Regex("^([A-Z0-9]+)-([0-9A-F]{6})$")

    /**
     * RFCOMM 读取可能把相邻帧合并；只在全部有效 DeviceInfo 帧给出同一身份时接受结果。
     */
    fun parse(stream: ByteArray): HuaweiDeviceInfoIdentity? {
        if (stream.isEmpty() || stream.size > MAX_STREAM_BYTES) return null
        val identities = frames(stream)
            .mapNotNull(::parseFrame)
            .distinct()
            .toList()
        return identities.singleOrNull()
    }

    private fun parseFrame(frame: ByteArray): HuaweiDeviceInfoIdentity? {
        if (frame.size < FRAME_PREFIX_BYTES + FRAME_CRC_BYTES) return null
        if (
            frame.u8(0) != FRAME_MAGIC ||
            frame.u8(1) != FRAME_VERSION ||
            frame.u8(4) != DEVICE_INFO_SERVICE ||
            frame.u8(5) != DEVICE_INFO_COMMAND
        ) {
            return null
        }
        val expectedCrc = (frame.u8(frame.lastIndex - 1) shl 8) or frame.u8(frame.lastIndex)
        if (crc16Xmodem(frame, frame.size - FRAME_CRC_BYTES) != expectedCrc) return null

        var modelId: String? = null
        var modelCode: String? = null
        var modelPrefixFromModel: String? = null
        var declaredModelPrefix: String? = null
        var subModelId: String? = null
        val tlvEnd = frame.size - FRAME_CRC_BYTES
        var index = FRAME_PREFIX_BYTES
        while (index < tlvEnd) {
            if (index + 2 > tlvEnd) return null
            val type = frame.u8(index)
            val length = frame.u8(index + 1)
            val valueStart = index + 2
            val valueEnd = valueStart + length
            if (valueEnd > tlvEnd) return null
            when (type) {
                TYPE_MODEL_CODE -> {
                    if (length != 2 || modelCode != null) return null
                    modelCode = "%02X%02X".format(frame.u8(valueStart), frame.u8(valueStart + 1))
                }

                TYPE_MODEL -> {
                    if (length !in 1..MAX_MODEL_VALUE_BYTES || modelId != null) return null
                    val value = String(frame, valueStart, length, StandardCharsets.US_ASCII)
                    val match = modelValueRegex.matchEntire(value) ?: return null
                    modelPrefixFromModel = match.groupValues[1]
                    modelId = match.groupValues[2]
                }

                TYPE_MODEL_PREFIX -> {
                    if (length !in 1..MAX_MODEL_VALUE_BYTES || declaredModelPrefix != null) return null
                    declaredModelPrefix = String(
                        frame,
                        valueStart,
                        length,
                        StandardCharsets.US_ASCII,
                    ).takeIf { it.matches(Regex("^[A-Z0-9]+$")) } ?: return null
                }

                TYPE_SUB_MODEL -> {
                    if (length != 1 || subModelId != null) return null
                    subModelId = "%02X".format(frame.u8(valueStart))
                }
            }
            index = valueEnd
        }
        val confirmedModelId = modelId ?: return null
        if (
            confirmedModelId == COMMON_RESOURCE_MODEL_ID ||
            modelCode != confirmedModelId.takeLast(4) ||
            declaredModelPrefix != modelPrefixFromModel
        ) {
            return null
        }
        return HuaweiDeviceInfoIdentity(
            modelId = confirmedModelId,
            subModelId = subModelId ?: return null,
        )
    }

    private fun frames(stream: ByteArray): Sequence<ByteArray> = sequence {
        var offset = 0
        while (offset + 5 <= stream.size) {
            if (stream.u8(offset) != FRAME_MAGIC || stream.u8(offset + 1) != FRAME_VERSION) {
                offset++
                continue
            }
            val payloadLength = stream.u8(offset + 2) or (stream.u8(offset + 3) shl 8)
            val frameSize = payloadLength + 5
            if (
                frameSize < FRAME_PREFIX_BYTES + FRAME_CRC_BYTES ||
                offset + frameSize > stream.size
            ) {
                offset++
                continue
            }
            yield(stream.copyOfRange(offset, offset + frameSize))
            offset += frameSize
        }
    }

    private fun crc16Xmodem(bytes: ByteArray, endExclusive: Int): Int {
        var crc = 0
        for (index in 0 until endExclusive) {
            crc = crc xor (bytes.u8(index) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
