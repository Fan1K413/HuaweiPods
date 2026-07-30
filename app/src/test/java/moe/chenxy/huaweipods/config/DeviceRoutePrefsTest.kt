package moe.chenxy.huaweipods.config

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.enabledHuaweiDeviceRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceRoutePrefsTest {
    @Test
    fun `enabled address binding wins after device is renamed`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            resolveBoundOrNamedRoute(
                boundRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                deviceName = "Piter's headphones",
            ),
        )
    }

    @Test
    fun `binding from another model branch cannot bypass build isolation`() {
        val disabledRoute = HuaweiDeviceRoute.entries.first {
            it != HuaweiDeviceRoute.UNSUPPORTED && it !in enabledHuaweiDeviceRoutes()
        }
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            resolveBoundOrNamedRoute(
                boundRoute = disabledRoute,
                deviceName = "Renamed device",
            ),
        )
    }

    @Test
    fun `official name remains the first recognition fallback`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            resolveBoundOrNamedRoute(
                boundRoute = null,
                deviceName = "HUAWEI FreeBuds 3",
            ),
        )
    }

    @Test
    fun `official name conflict rejects an old address binding`() {
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            resolveBoundOrNamedRoute(
                boundRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                deviceName = "HUAWEI FreeClip 2",
            ),
        )
    }

    @Test
    fun `binding key normalizes address case and rejects invalid values`() {
        assertEquals(
            DeviceRoutePrefs.bindingKey("AA:BB:CC:DD:EE:FF"),
            DeviceRoutePrefs.bindingKey("aa:bb:cc:dd:ee:ff"),
        )
        assertNull(DeviceRoutePrefs.bindingKey("not-a-bluetooth-address"))
    }
}
