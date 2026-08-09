package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowLatencyAutoApplyPolicyTest {
    private val request = LowLatencyAutoApplyRequest(
        generation = 7L,
        address = "AA:BB:CC:DD:EE:FF",
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        attempt = 1,
    )

    @Test
    fun `accepts only the same active connection session`() {
        assertTrue(
            matchesLowLatencyAutoApplySession(
                request = request,
                activeGeneration = 7L,
                activeAddress = "aa:bb:cc:dd:ee:ff",
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            ),
        )
    }

    @Test
    fun `rejects stale generation address route and disconnected session`() {
        assertFalse(
            matchesLowLatencyAutoApplySession(
                request,
                activeGeneration = 8L,
                activeAddress = request.address,
                activeRoute = request.route,
            ),
        )
        assertFalse(
            matchesLowLatencyAutoApplySession(
                request,
                activeGeneration = request.generation,
                activeAddress = "11:22:33:44:55:66",
                activeRoute = request.route,
            ),
        )
        assertFalse(
            matchesLowLatencyAutoApplySession(
                request,
                activeGeneration = request.generation,
                activeAddress = request.address,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            ),
        )
        assertFalse(
            matchesLowLatencyAutoApplySession(
                request,
                activeGeneration = request.generation,
                activeAddress = null,
                activeRoute = request.route,
            ),
        )
    }
}
