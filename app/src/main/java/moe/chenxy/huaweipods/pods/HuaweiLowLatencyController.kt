package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context

/**
 * 低时延写入命令。以下支持范围均来自对应机型抓包中的同一 2B6C/01 开关帧，
 * 未抓到 setter 的型号不会出现在能力表中，也不会接收该命令。
 */
object HuaweiLowLatencyController {
    private val DISABLED_PACKET = decodeLowLatencyHex("5A0006002B6C010100B430")
    private val ENABLED_PACKET = decodeLowLatencyHex("5A0006002B6C010101A411")

    fun setEnabled(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val address = runCatching { device.address }.getOrNull()
        if (!route.supportsLowLatencyControl ||
            address == null ||
            !BluetoothAdapter.checkBluetoothAddress(address)
        ) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet(enabled),
            description = "${route.name.lowercase()} low-latency enabled=$enabled",
            onComplete = onComplete,
        )
    }

    internal fun packet(enabled: Boolean): ByteArray =
        (if (enabled) ENABLED_PACKET else DISABLED_PACKET).copyOf()
}

private fun decodeLowLatencyHex(value: String): ByteArray =
    value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
