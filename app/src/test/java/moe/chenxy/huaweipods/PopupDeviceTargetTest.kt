package moe.chenxy.huaweipods

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupDeviceTargetTest {
    private val target = PopupDeviceTarget(
        address = "AA:BB:CC:DD:EE:FF",
        deviceName = "HUAWEI FreeBuds 6i",
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
    )

    @Test
    fun `supported launch target keeps immutable identity`() {
        val result = requireNotNull(
            popupDeviceTargetOrNull(
                address = " aa:bb:cc:dd:ee:ff ",
                deviceName = " HUAWEI FreeBuds 6i ",
                route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            ),
        )

        assertEquals("aa:bb:cc:dd:ee:ff", result.address)
        assertEquals("HUAWEI FreeBuds 6i", result.deviceName)
        assertEquals(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, result.route)
    }

    @Test
    fun `unsupported or addressless launch is rejected`() {
        assertNull(
            popupDeviceTargetOrNull(
                address = target.address,
                deviceName = target.deviceName,
                route = HuaweiDeviceRoute.UNSUPPORTED,
            ),
        )
        assertNull(
            popupDeviceTargetOrNull(
                address = " ",
                deviceName = target.deviceName,
                route = target.route,
            ),
        )
    }

    @Test
    fun `matching broadcast requires the same address and route`() {
        assertTrue(
            popupBroadcastMatchesTarget(
                target,
                PopupBroadcastIdentity(
                    address = target.address.lowercase(),
                    deviceName = target.deviceName,
                    route = target.route,
                ),
            ),
        )
    }

    @Test
    fun `other device broadcast is rejected even for the same model`() {
        assertFalse(
            popupBroadcastMatchesTarget(
                target,
                PopupBroadcastIdentity(
                    address = "11:22:33:44:55:66",
                    deviceName = target.deviceName,
                    route = target.route,
                ),
            ),
        )
    }

    @Test
    fun `other model is rejected while a renamed alias remains accepted`() {
        assertFalse(
            popupBroadcastMatchesTarget(
                target,
                PopupBroadcastIdentity(
                    address = target.address,
                    deviceName = target.deviceName,
                    route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                ),
            ),
        )
        assertTrue(
            popupBroadcastMatchesTarget(
                target,
                PopupBroadcastIdentity(
                    address = target.address,
                    deviceName = "Other alias",
                    route = target.route,
                ),
            ),
        )
    }

    @Test
    fun `broadcast requires address and route but not the mutable device name`() {
        assertFalse(
            popupBroadcastMatchesTarget(
                target,
                PopupBroadcastIdentity(null, target.deviceName, target.route),
            ),
        )
        assertTrue(
            popupBroadcastMatchesTarget(
                target,
                PopupBroadcastIdentity(target.address, null, target.route),
            ),
        )
        assertFalse(
            popupBroadcastMatchesTarget(
                target,
                PopupBroadcastIdentity(target.address, target.deviceName, null),
            ),
        )
    }
}
