package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiSessionSafetyTest {
    @Test
    fun `control target requires the current route`() {
        val activeAddress = "AA:BB:CC:33:44:55"
        val activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I

        assertTrue(
            matchesHuaweiSessionTarget(
                activeAddress = activeAddress,
                activeRoute = activeRoute,
                requestedAddress = activeAddress.lowercase(),
                requestedRoute = activeRoute,
                requireAddress = true,
            ),
        )
        assertFalse(
            matchesHuaweiSessionTarget(
                activeAddress = activeAddress,
                activeRoute = activeRoute,
                requestedAddress = null,
                requestedRoute = activeRoute,
                requireAddress = true,
            ),
        )
        assertFalse(
            matchesHuaweiSessionTarget(
                activeAddress = activeAddress,
                activeRoute = activeRoute,
                requestedAddress = "AA:BB:CC:33:44:99",
                requestedRoute = activeRoute,
                requireAddress = true,
            ),
        )
        assertFalse(
            matchesHuaweiSessionTarget(
                activeAddress = activeAddress,
                activeRoute = activeRoute,
                requestedAddress = activeAddress,
                requestedRoute = null,
                requireAddress = true,
            ),
        )
        assertFalse(
            matchesHuaweiSessionTarget(
                activeAddress = activeAddress,
                activeRoute = activeRoute,
                requestedAddress = activeAddress,
                requestedRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                requireAddress = true,
            ),
        )
    }

    @Test
    fun `gesture refresh may omit address but never route`() {
        val activeRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2

        assertTrue(
            matchesHuaweiSessionTarget(
                activeAddress = "00:11:22:33:44:55",
                activeRoute = activeRoute,
                requestedAddress = null,
                requestedRoute = activeRoute,
                requireAddress = false,
            ),
        )
        assertFalse(
            matchesHuaweiSessionTarget(
                activeAddress = "00:11:22:33:44:55",
                activeRoute = activeRoute,
                requestedAddress = null,
                requestedRoute = null,
                requireAddress = false,
            ),
        )
    }

    @Test
    fun `Pro 5 preserves protocol reported zero percent availability`() {
        val reportedBattery = BatteryParams(
            left = PodParams(battery = 0, isConnected = true, rawStatus = 1),
            right = PodParams(battery = 78, isConnected = true, rawStatus = 1),
            case = PodParams(battery = 0, isConnected = true, rawStatus = 1),
        )

        val result = normalizeHuaweiPrivateBattery(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            reportedBattery,
        )

        assertSame(reportedBattery, result)
        assertTrue(result.left?.isConnected == true)
        assertTrue(result.case?.isConnected == true)
    }

    @Test
    fun `routes without reported availability still normalize zero percent earbuds`() {
        val battery = BatteryParams(
            left = PodParams(battery = 0, isConnected = true),
            right = PodParams(battery = 78, isConnected = true),
            case = PodParams(battery = 0, isConnected = true),
        )

        val result = normalizeHuaweiPrivateBattery(
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            battery,
        )

        assertFalse(result.left?.isConnected == true)
        assertTrue(result.right?.isConnected == true)
        assertTrue(result.case?.isConnected == true)
    }

    @Test
    fun `invalidating transport generation cancels every older request`() {
        val generation = HuaweiRfcommTransportGeneration()
        val firstRequest = generation.snapshot()

        generation.invalidate()
        val secondRequest = generation.snapshot()

        assertFalse(generation.isCurrent(firstRequest))
        assertTrue(generation.isCurrent(secondRequest))

        generation.invalidate()
        assertFalse(generation.isCurrent(secondRequest))
        assertTrue(generation.isCurrent(generation.snapshot()))
    }
}
