package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiAncLevelProfileTest {
    @Test
    fun `FreeBuds 5 exposes the three captured ANC levels without deep mode`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5

        assertEquals(
            listOf(
                HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x03, miuiValue = 0x03),
                HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
                HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
            ),
            route.ancLevelOptions,
        )
        assertEquals(0x03, route.defaultAncSubMode)
        assertTrue(route.supportsAncSubMode(0x03))
        assertTrue(route.supportsAncSubMode(0x01))
        assertTrue(route.supportsAncSubMode(0x00))
        assertFalse(route.supportsAncSubMode(0x02))
        assertNull(route.ancSubModeForMiuiLevel(0x02))
        assertNull(route.miuiLevelForAncSubMode(0x02))
    }

    @Test
    fun `FreeBuds 5 rejects unsupported ANC readback states`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5

        assertEquals(
            HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x03),
            route.validateAncState(HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x03)),
        )
        assertNull(route.validateAncState(HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x02)))
        assertNull(route.validateAncState(HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0xFF)))
    }

    @Test
    fun `FreeBuds 6i and Pro 3 retain the existing four-level mapping`() {
        val expected = listOf(
            HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x01, miuiValue = 0x03),
            HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x00, miuiValue = 0x01),
            HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x02, miuiValue = 0x00),
            HuaweiAncLevelOption(HuaweiAncLevel.DEEP, protocolValue = 0x03, miuiValue = 0x02),
        )

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        ).forEach { route ->
            assertEquals(route.name, expected, route.ancLevelOptions)
            assertEquals(route.name, 0x01, route.defaultAncSubMode)
            expected.forEach { option ->
                assertEquals(option.protocolValue, route.ancSubModeForMiuiLevel(option.miuiValue))
                assertEquals(option.miuiValue, route.miuiLevelForAncSubMode(option.protocolValue))
            }
        }
    }
}
