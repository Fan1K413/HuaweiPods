package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiDeviceInfoPublishSessionTest {
    private val identity = HuaweiDeviceInfoIdentity("000153", "02")
    private val request = HuaweiDeviceInfoPublishRequest(
        generation = 7L,
        address = "AA:BB:CC:DD:EE:FF",
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        identity = identity,
    )

    @Test
    fun `accepts only the exact active session and identity`() {
        assertTrue(matches(request = request))
        assertTrue(matches(request = request, address = "aa:bb:cc:dd:ee:ff"))

        assertFalse(matches(request = request, generation = 8L))
        assertFalse(matches(request = request, address = "11:22:33:44:55:66"))
        assertFalse(matches(request = request, address = null))
        assertFalse(matches(request = request, route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))
        assertFalse(matches(request = request, identity = HuaweiDeviceInfoIdentity("000153", "01")))
        assertFalse(matches(request = request, identity = null))
    }

    private fun matches(
        request: HuaweiDeviceInfoPublishRequest,
        generation: Long = request.generation,
        address: String? = request.address,
        route: HuaweiDeviceRoute = request.route,
        identity: HuaweiDeviceInfoIdentity? = request.identity,
    ): Boolean = matchesHuaweiDeviceInfoPublishSession(
        request = request,
        activeGeneration = generation,
        activeAddress = address,
        activeRoute = route,
        activeIdentity = identity,
    )
}
