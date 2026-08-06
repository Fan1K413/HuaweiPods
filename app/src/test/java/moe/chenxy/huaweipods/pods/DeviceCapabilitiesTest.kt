package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `unified build recognizes every integrated model by exact official alias`() {
        val cases = listOf(
            "HUAWEI FreeBuds 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            "FreeBuds 5" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            "HUAWEI FreeBuds 6i" to HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            "FreeBuds Pro 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            "HUAWEI FreeBuds Pro 4" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
            "FreeBuds Pro 5" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            "HUAWEI FreeBuds 7i" to HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            "FreeClip" to HuaweiDeviceRoute.HUAWEI_FREECLIP,
            "HUAWEI FreeClip 2" to HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            "HUAWEI Eyewear" to HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            "Eyewear 2" to HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        )

        cases.forEach { (deviceName, expectedRoute) ->
            assertEquals(deviceName, expectedRoute, detectHuaweiDeviceRoute(deviceName))
        }
        assertEquals(HuaweiDeviceRoute.entries.size - 1, enabledHuaweiDeviceRoutes().size)
    }

    @Test
    fun `near matches and unrelated bluetooth devices remain unsupported`() {
        listOf(
            "HUAWEI FreeBuds Pro 5i",
            "HUAWEI Eyewear 2 Pro",
            "My custom freebuds3 headset",
            "OPPO Enco X3",
            "HUAWEI WATCH GT",
            "",
            "   ",
        ).forEach { deviceName ->
            assertEquals(deviceName, HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute(deviceName))
        }
        assertEquals(HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute(null))
    }

    @Test
    fun `noise control capabilities follow verified model protocols`() {
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS3.supportsAnc)
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS3.supportsAncDirectionDial)
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREEBUDS3.supportsTransparency)

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        ).forEach { route ->
            assertTrue(route.displayName, route.supportsTransparency)
            assertTrue(route.displayName, route.supportsAncStateReadback)
            assertTrue(route.displayName, route.supportsDiscreteAncLevels)
        }

        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5.supportsTransparency)
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5.supportsAncStateReadback)
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5.supportsDiscreteAncLevels)
    }

    @Test
    fun `clip and eyewear families never expose traditional ANC`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertTrue(route.displayName, route.supportsRfcommBattery)
            assertFalse(route.displayName, route.supportsAnc)
        }
        assertFalse(HuaweiDeviceRoute.HUAWEI_EYEWEAR.hasChargingCase)
        assertFalse(HuaweiDeviceRoute.HUAWEI_EYEWEAR2.hasChargingCase)
    }

    @Test
    fun `gesture configuration is only exposed for implemented routes`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route -> assertTrue(route.displayName, route.supportsGestureConfiguration) }
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREEBUDS5.supportsGestureConfiguration)
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREECLIP.supportsGestureConfiguration)
    }

    @Test
    fun `reported earbud availability is restricted to FreeBuds Pro 5`() {
        HuaweiDeviceRoute.entries.forEach { route ->
            assertEquals(
                route.name,
                route == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                route.usesReportedEarbudAvailability,
            )
        }
    }
}
