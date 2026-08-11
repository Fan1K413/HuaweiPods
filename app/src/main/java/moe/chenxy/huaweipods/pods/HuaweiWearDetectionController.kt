package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/**
 * Shared wear-detection protocol used by the models for which a real capture proves 2B10/2B11.
 *
 * Keeping the protocol here avoids copying the same packets into each model-specific screen. A
 * route is added only after its capture confirms both the setter and the matching state query.
 */
object HuaweiWearDetectionController {
    private val supportedRoutes = setOf(
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        HuaweiDeviceRoute.HUAWEI_FREECLIP2,
    )
    private val stateQuery = hex("5A0005002B110100772A")
    private val disabledPacket = hex("5A0006002B10010100B977")
    private val enabledPacket = hex("5A0006002B10010101A956")

    fun supports(route: HuaweiDeviceRoute): Boolean = route in supportedRoutes

    fun stateQueryPacket(route: HuaweiDeviceRoute): ByteArray? =
        if (supports(route)) stateQuery.copyOf() else null

    fun setPacket(route: HuaweiDeviceRoute, enabled: Boolean): ByteArray? =
        if (supports(route)) {
            (if (enabled) enabledPacket else disabledPacket).copyOf()
        } else {
            null
        }

    fun parseState(stream: ByteArray): Boolean? =
        HuaweiFreeBuds5Controller.parseWearDetectionState(stream)

    fun setEnabled(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = setPacket(route, enabled)
        if (packet == null || !isExpectedTarget(context, device, route)) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "wear-detection enabled=$enabled",
            onComplete = onComplete,
        )
    }

    fun requestState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (Boolean?) -> Unit,
    ) {
        val packet = stateQueryPacket(route)
        if (packet == null || !isExpectedTarget(context, device, route)) {
            onState(null)
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "wear-detection-state",
            responseWindowMs = 1_000L,
            onResponse = { onState(parseState(it)) },
        )
    }

    @SuppressLint("MissingPermission")
    private fun isExpectedTarget(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
    ): Boolean {
        if (!supports(route)) return false
        val address = runCatching { device.address }.getOrNull()
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) return false
        val name = runCatching {
            device.name?.takeIf(String::isNotBlank)
                ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceRoutePrefs.resolve(prefs, address, name) == route
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
