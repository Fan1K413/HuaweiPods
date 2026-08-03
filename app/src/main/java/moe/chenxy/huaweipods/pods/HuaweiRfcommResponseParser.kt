package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.normalizedEarbudAvailability

internal object HuaweiRfcommResponseParser {
    private const val HEADER_SIZE = 5
    private const val CHECKSUM_SIZE = 2
    private const val BATTERY_SERVICE = 0x01
    private val BATTERY_COMMANDS = setOf(0x08, 0x27)
    private const val BATTERY_LEVELS = 0x02
    private const val CHARGING_STATES = 0x03
    private const val ANC_SERVICE = 0x2B
    private const val ANC_STATE_COMMAND = 0x2A
    private const val ANC_STATE = 0x01

    fun parseBattery(stream: ByteArray, includeCase: Boolean = true): BatteryParams? {
        var latestBattery: BatteryParams? = null
        frames(stream).forEach { frame ->
            if (frame.u8(4) != BATTERY_SERVICE || frame.u8(5) !in BATTERY_COMMANDS) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CHECKSUM_SIZE)
            val levels = fields[BATTERY_LEVELS] ?: return@forEach
            if (levels.size < 2) return@forEach
            val charging = fields[CHARGING_STATES] ?: byteArrayOf()
            latestBattery = BatteryParams(
                left = levels.podAt(0, charging),
                right = levels.podAt(1, charging),
                case = levels.podAt(2, charging).takeIf { includeCase },
            ).normalizedEarbudAvailability()
        }
        return latestBattery
    }

    fun parseAncStatus(stream: ByteArray): Int? {
        var latestStatus: Int? = null
        frames(stream).forEach { frame ->
            if (frame.u8(4) != ANC_SERVICE || frame.u8(5) != ANC_STATE_COMMAND) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CHECKSUM_SIZE)
            val state = fields[ANC_STATE]?.takeIf { it.size == 2 } ?: return@forEach
            latestStatus = when (state.u8(1)) {
                0x01 -> 2
                0x00, 0x02 -> 1
                else -> return@forEach
            }
        }
        return latestStatus
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
            yield(stream.copyOfRange(offset, offset + frameSize))
            offset += frameSize
        }
    }

    private fun parseFields(
        frame: ByteArray,
        start: Int,
        endExclusive: Int,
    ): Map<Int, ByteArray> {
        val fields = linkedMapOf<Int, ByteArray>()
        var offset = start
        while (offset + 2 <= endExclusive) {
            val type = frame.u8(offset)
            val length = frame.u8(offset + 1)
            val valueStart = offset + 2
            val valueEnd = valueStart + length
            if (valueEnd > endExclusive) break
            fields[type] = frame.copyOfRange(valueStart, valueEnd)
            offset = valueEnd
        }
        return fields
    }

    private fun ByteArray.podAt(
        index: Int,
        charging: ByteArray,
    ): PodParams? {
        val level = getOrNull(index)?.toInt()?.and(0xFF)?.takeIf { it in 0..100 } ?: return null
        val chargingValue = charging.getOrNull(index)?.toInt()?.and(0xFF) ?: 0
        return PodParams(
            battery = level,
            isCharging = chargingValue != 0,
            isConnected = true,
            rawStatus = chargingValue,
        )
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
