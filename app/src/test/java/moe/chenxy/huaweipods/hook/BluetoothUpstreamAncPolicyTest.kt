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
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0xFF),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, 2, off),
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
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
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
    fun `discrete ANC levels translate between MIUI menu codes and Huawei protocol`() {
        val cases = mapOf(
            "0103" to HuaweiAncLevel.ADAPTIVE,
            "0101" to HuaweiAncLevel.LIGHT,
            "0100" to HuaweiAncLevel.BALANCED,
            "0102" to HuaweiAncLevel.DEEP,
        )

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            cases.forEach { (miuiPayload, huaweiLevel) ->
                val state = upstreamHuaweiAncStateForLevel(route, miuiPayload, off)

                assertEquals(
                    HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, huaweiLevel.protocolValue),
                    state,
                )
                assertEquals(miuiPayload, upstreamMiuiAncLevel(route, state!!))
            }
            assertNull(upstreamHuaweiAncStateForLevel(route, "0109", off))
        }
    }

    @Test
    fun `FreeBuds 5 MIUI levels map to its captured three-level protocol`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5
        val cases = mapOf(
            "0103" to 0x03,
            "0101" to 0x01,
            "0100" to 0x00,
        )

        cases.forEach { (miuiPayload, huaweiSubMode) ->
            val state = upstreamHuaweiAncStateForLevel(route, miuiPayload, off)
            assertEquals(
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, huaweiSubMode),
                state,
            )
            assertEquals(miuiPayload, upstreamMiuiAncLevel(route, state!!))
        }
        assertNull(upstreamHuaweiAncStateForLevel(route, "0102", off))
        assertNull(upstreamHuaweiAncStateForLevel(route, "02ff", off))
        assertEquals(
            "0000",
            upstreamMiuiAncLevel(
                route,
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x02),
            ),
        )
    }
}
