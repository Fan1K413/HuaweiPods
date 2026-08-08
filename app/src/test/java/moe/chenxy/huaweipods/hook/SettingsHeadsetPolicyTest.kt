package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHeadsetPolicyTest {
    @Test
    fun `settings ANC renderer is skipped for clip and eyewear routes`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertFalse(route.name, shouldUpdateSettingsAncUi(route))
        }
    }

    @Test
    fun `settings ANC renderer remains enabled for ANC routes`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        ).forEach { route ->
            assertTrue(route.name, shouldUpdateSettingsAncUi(route))
        }
    }

    @Test
    fun `FreeBuds 5 replaces the native four-level row with a three-level selector`() {
        assertTrue(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
        assertFalse(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I))
        assertFalse(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))
        assertFalse(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
    }
}
