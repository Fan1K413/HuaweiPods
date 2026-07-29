package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `base route only enables verified FreeBuds 3`() {
        val cases = listOf(
            "HUAWEI FreeBuds 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            "FreeBuds 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            "HUAWEI FreeBuds 5" to HuaweiDeviceRoute.UNSUPPORTED,
            "FreeBuds 5" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI FreeBuds 6i" to HuaweiDeviceRoute.UNSUPPORTED,
            "FreeBuds 6i" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI FreeClip" to HuaweiDeviceRoute.UNSUPPORTED,
            "FreeClip" to HuaweiDeviceRoute.UNSUPPORTED,
            "FreeClip 2" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI FreeClip 2" to HuaweiDeviceRoute.UNSUPPORTED,
            "OPPO Enco" to HuaweiDeviceRoute.UNSUPPORTED,
            "OPPO Enco X3" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI WATCH" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI WATCH GT" to HuaweiDeviceRoute.UNSUPPORTED,
            "" to HuaweiDeviceRoute.UNSUPPORTED,
            "   " to HuaweiDeviceRoute.UNSUPPORTED,
            "My custom freebuds3 headset" to HuaweiDeviceRoute.UNSUPPORTED,
        )

        cases.forEach { (deviceName, expectedRoute) ->
            assertEquals(deviceName, expectedRoute, detectHuaweiDeviceRoute(deviceName))
        }
        assertEquals("null", HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute(null))
    }

    @Test
    fun `FreeClip exposes battery integration without ANC`() {
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREECLIP.isSupported)
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREECLIP.supportsRfcommBattery)
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREECLIP.supportsAnc)
    }

    @Test
    fun `FreeClip 2 exposes battery integration without ANC`() {
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREECLIP2.isSupported)
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREECLIP2.supportsRfcommBattery)
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREECLIP2.supportsAnc)
    }

    @Test
    fun `FreeBuds 6i exposes battery and basic ANC integration`() {
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I.isSupported)
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I.supportsRfcommBattery)
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I.supportsAnc)
    }
}
