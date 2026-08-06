package moe.chenxy.huaweipods.hook.milink

import moe.chenxy.huaweipods.pods.HuaweiAncLevel
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiLinkAncRoutingTest {
    @Test
    fun `clip and eyewear routes never expose ANC`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertNull(miLinkAncModeFor(route, 2))
            assertNull(huaweiAncStatusForMiLink(route, 0))
            assertNull(huaweiAncStatusForMiLink(route, 1))
            assertNull(huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `three-state routes preserve off ANC and transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        ).forEach { route ->
            assertEquals(0, miLinkAncModeFor(route, 1))
            assertEquals(1, miLinkAncModeFor(route, 2))
            assertEquals(2, miLinkAncModeFor(route, 3))
            assertEquals(1, huaweiAncStatusForMiLink(route, 0))
            assertEquals(2, huaweiAncStatusForMiLink(route, 1))
            assertEquals(3, huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `two-state ANC routes reject transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertEquals(0, miLinkAncModeFor(route, 1))
            assertEquals(1, miLinkAncModeFor(route, 2))
            assertNull(huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `three-state models use their captured submode defaults`() {
        assertEquals(
            HuaweiAncLevel.ADAPTIVE.protocolValue,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                huaweiStatus = 2,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x02,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                huaweiStatus = 3,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0xFF,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                huaweiStatus = 3,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x01,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                huaweiStatus = 3,
                requestedSubMode = 0x01,
                storedSubMode = null,
            ),
        )
    }
}
