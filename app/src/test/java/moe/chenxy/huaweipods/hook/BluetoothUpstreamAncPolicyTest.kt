package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiAncLevel
import moe.chenxy.huaweipods.pods.HuaweiAncState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothUpstreamAncPolicyTest {
    private val off = HuaweiAncState(NoiseControlMode.OFF)

    @Test
    fun `clip and eyewear routes reject every ANC command`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertNull(upstreamHuaweiAncStateForMode(route, 1, off))
            assertNull(upstreamHuaweiAncStateForMode(route, 2, off))
            assertNull(upstreamHuaweiAncStateForLevel(route, "0100", off))
            assertNull(upstreamHuaweiAncStateForLevel(route, "02ff", off))
            assertEquals("0000", upstreamMiuiAncLevel(route, HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION)))
        }
    }

    @Test
    fun `three-state routes preserve transparency and captured defaults`() {
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0xFF),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0xFF),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x01),
            upstreamHuaweiAncStateForLevel(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, "0201", off),
        )
    }

    @Test
    fun `two-state routes reject transparency without converting it to off`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertNull(upstreamHuaweiAncStateForMode(route, 2, off))
            assertNull(upstreamHuaweiAncStateForLevel(route, "02ff", off))
            assertEquals(
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION),
                upstreamHuaweiAncStateForLevel(route, "0100", off),
            )
        }
    }

    @Test
    fun `discrete ANC level round trips through MIUI payload`() {
        val state = upstreamHuaweiAncStateForLevel(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            "0103",
            off,
        )

        assertEquals(HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, HuaweiAncLevel.DEEP.protocolValue), state)
        assertEquals("0103", upstreamMiuiAncLevel(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, state!!))
        assertNull(upstreamHuaweiAncStateForLevel(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, "0109", off))
    }
}
