package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiHfpAncStateTest {
    private val offState = HuaweiAncState(NoiseControlMode.OFF)

    @Test
    fun `transparency routes cycle through all three modes`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        ).forEach { route ->
            assertEquals(NoiseControlMode.NOISE_CANCELLATION, nextHuaweiAncMode(route, NoiseControlMode.UNKNOWN))
            assertEquals(NoiseControlMode.NOISE_CANCELLATION, nextHuaweiAncMode(route, NoiseControlMode.OFF))
            assertEquals(NoiseControlMode.TRANSPARENCY, nextHuaweiAncMode(route, NoiseControlMode.NOISE_CANCELLATION))
            assertEquals(NoiseControlMode.OFF, nextHuaweiAncMode(route, NoiseControlMode.TRANSPARENCY))
        }
    }

    @Test
    fun `two state routes never enter transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        ).forEach { route ->
            assertEquals(NoiseControlMode.NOISE_CANCELLATION, nextHuaweiAncMode(route, NoiseControlMode.UNKNOWN))
            assertEquals(NoiseControlMode.NOISE_CANCELLATION, nextHuaweiAncMode(route, NoiseControlMode.OFF))
            assertEquals(NoiseControlMode.OFF, nextHuaweiAncMode(route, NoiseControlMode.NOISE_CANCELLATION))
            assertEquals(NoiseControlMode.NOISE_CANCELLATION, nextHuaweiAncMode(route, NoiseControlMode.TRANSPARENCY))
        }
    }

    @Test
    fun `6i uses its verified transparency default`() {
        assertEquals(
            0x02,
            normalizeHuaweiAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.TRANSPARENCY,
                null,
                offState,
            ),
        )
    }

    @Test
    fun `pro 3 and pro 5 accept standard and voice transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        ).forEach { route ->
            assertEquals(
                0xFF,
                normalizeHuaweiAncSubMode(route, NoiseControlMode.TRANSPARENCY, 0xFF, offState),
            )
            assertEquals(
                0x01,
                normalizeHuaweiAncSubMode(route, NoiseControlMode.TRANSPARENCY, 0x01, offState),
            )
        }
    }

    @Test
    fun `non transparency routes never leak transparency submode`() {
        assertNull(
            normalizeHuaweiAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                NoiseControlMode.TRANSPARENCY,
                0x01,
                offState,
            ),
        )
    }

    @Test
    fun `FreeBuds 5 accepts only its captured three ANC submodes`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5
        listOf(0x03, 0x01, 0x00).forEach { level ->
            assertEquals(
                level,
                normalizeHuaweiAncSubMode(
                    route,
                    NoiseControlMode.NOISE_CANCELLATION,
                    level,
                    offState,
                ),
            )
        }
        assertEquals(
            0x03,
            normalizeHuaweiAncSubMode(
                route,
                NoiseControlMode.NOISE_CANCELLATION,
                0x02,
                offState,
            ),
        )
        assertEquals(
            0x01,
            normalizeHuaweiAncSubMode(
                route,
                NoiseControlMode.NOISE_CANCELLATION,
                0x02,
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x01),
            ),
        )
    }

    @Test
    fun `invalid FreeBuds 6i submodes fall back to previous valid state then model default`() {
        assertEquals(
            HuaweiAncLevel.DEEP.protocolValue,
            normalizeHuaweiAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.NOISE_CANCELLATION,
                0x7F,
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, HuaweiAncLevel.DEEP.protocolValue),
            ),
        )
        assertEquals(
            HuaweiAncLevel.ADAPTIVE.protocolValue,
            normalizeHuaweiAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.NOISE_CANCELLATION,
                0x7F,
                offState,
            ),
        )
        assertEquals(
            0x01,
            normalizeHuaweiAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.TRANSPARENCY,
                0x7F,
                HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x01),
            ),
        )
        assertEquals(
            0x02,
            normalizeHuaweiAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.TRANSPARENCY,
                0x7F,
                offState,
            ),
        )
    }

    @Test
    fun `invalid Pro 5 transparency submode falls back to standard mode`() {
        assertEquals(
            0xFF,
            normalizeHuaweiAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                NoiseControlMode.TRANSPARENCY,
                0x7F,
                offState,
            ),
        )
    }
}
