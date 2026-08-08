package moe.chenxy.huaweipods.debugcapture

import java.nio.charset.StandardCharsets

internal data class SmartAudioDeviceIdentity(
    val modelId: String,
    val subModelId: String,
)

/** 从智慧音频 DeviceInfo 应答的 TLV 0x0A/0x19 中提取资源身份。 */
internal object SmartAudioDeviceInfoIdentity {
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
    private const val COMMON_RESOURCE_MODEL_ID = "00000A"
    private val modelValueRegex = Regex("^([A-Z0-9]+)-([0-9A-F]{6})$")

    fun parse(payloadHex: String?): SmartAudioDeviceIdentity? {
        val payload = payloadHex.toByteArrayOrNull() ?: return null
        if (payload.size < FRAME_PREFIX_BYTES + FRAME_CRC_BYTES) return null
        if (
            (payload[0].toInt() and 0xFF) != FRAME_MAGIC ||
            (payload[1].toInt() and 0xFF) != FRAME_VERSION
        ) {
            return null
        }
        val declaredPayloadBytes =
            (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
        if (payload.size != declaredPayloadBytes + 5) return null
        if (
            (payload[4].toInt() and 0xFF) != DEVICE_INFO_SERVICE ||
            (payload[5].toInt() and 0xFF) != DEVICE_INFO_COMMAND
        ) {
            return null
        }
        val expectedCrc =
            ((payload[payload.size - 2].toInt() and 0xFF) shl 8) or
                (payload[payload.size - 1].toInt() and 0xFF)
        if (crc16Xmodem(payload, payload.size - FRAME_CRC_BYTES) != expectedCrc) return null

        var modelId: String? = null
        var modelCode: String? = null
        var modelPrefixFromModel: String? = null
        var declaredModelPrefix: String? = null
        var subModelId: String? = null
        val tlvEnd = payload.size - FRAME_CRC_BYTES
        var index = FRAME_PREFIX_BYTES
        while (index < tlvEnd) {
            if (index + 2 > tlvEnd) return null
            val type = payload[index].toInt() and 0xFF
            val length = payload[index + 1].toInt() and 0xFF
            val valueStart = index + 2
            val valueEnd = valueStart + length
            if (valueEnd > tlvEnd) return null
            when (type) {
                TYPE_MODEL_CODE -> {
                    if (length != 2 || modelCode != null) return null
                    modelCode = "%02X%02X".format(
                        payload[valueStart].toInt() and 0xFF,
                        payload[valueStart + 1].toInt() and 0xFF,
                    )
                }

                TYPE_MODEL -> if (length in 1..MAX_MODEL_VALUE_BYTES && modelId == null) {
                    val value = String(
                        payload,
                        valueStart,
                        length,
                        StandardCharsets.US_ASCII,
                    )
                    val match = modelValueRegex.matchEntire(value) ?: return null
                    modelPrefixFromModel = match.groupValues[1]
                    modelId = SmartAudioResourceLocator.normalizeModelId(match.groupValues[2])
                        ?: return null
                } else {
                    return null
                }

                TYPE_MODEL_PREFIX -> {
                    if (length !in 1..MAX_MODEL_VALUE_BYTES || declaredModelPrefix != null) return null
                    declaredModelPrefix = String(
                        payload,
                        valueStart,
                        length,
                        StandardCharsets.US_ASCII,
                    ).also { if (!it.matches(Regex("^[A-Z0-9]+$"))) return null }
                }

                TYPE_SUB_MODEL -> if (length == 1) {
                    if (subModelId != null) return null
                    subModelId = "%02X".format(payload[valueStart].toInt() and 0xFF)
                } else {
                    return null
                }
            }
            index = valueEnd
        }
        val confirmedModelId = modelId ?: return null
        if (
            confirmedModelId == COMMON_RESOURCE_MODEL_ID ||
            modelCode != confirmedModelId.takeLast(4)
        ) {
            return null
        }
        if (declaredModelPrefix != modelPrefixFromModel) return null
        return SmartAudioDeviceIdentity(
            modelId = confirmedModelId,
            subModelId = SmartAudioResourceLocator.normalizeSubModelId(subModelId) ?: return null,
        )
    }

    private fun crc16Xmodem(bytes: ByteArray, endExclusive: Int): Int {
        var crc = 0
        for (index in 0 until endExclusive) {
            crc = crc xor ((bytes[index].toInt() and 0xFF) shl 8)
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

    private fun String?.toByteArrayOrNull(): ByteArray? {
        val compact = this
            ?.filterNot(Char::isWhitespace)
            ?.takeIf { it.isNotEmpty() && it.length % 2 == 0 }
            ?: return null
        if (compact.length > 2 * 64 * 1024) return null
        val result = ByteArray(compact.length / 2)
        for (index in result.indices) {
            val offset = index * 2
            val value = compact.substring(offset, offset + 2).toIntOrNull(16) ?: return null
            result[index] = value.toByte()
        }
        return result
    }
}
