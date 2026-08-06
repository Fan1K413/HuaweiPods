package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryIslandTriggerPolicyTest {
    @Test
    fun `waits for valid ear battery and triggers once per session`() {
        val policy = BatteryIslandTriggerPolicy()

        assertFalse(policy.shouldTrigger("AA:BB", hasConnectedEarBattery = false, now = 1_000L))
        assertTrue(policy.shouldTrigger("AA:BB", hasConnectedEarBattery = true, now = 2_000L))
        assertFalse(policy.shouldTrigger("AA:BB", hasConnectedEarBattery = true, now = 3_000L))
    }

    @Test
    fun `suppresses rapid reconnect for the same device`() {
        val policy = BatteryIslandTriggerPolicy(reconnectCooldownMs = 30_000L)

        assertTrue(policy.shouldTrigger("aa:bb", hasConnectedEarBattery = true, now = 10_000L))
        policy.onNewSession()
        assertFalse(policy.shouldTrigger("AA:BB", hasConnectedEarBattery = true, now = 20_000L))
        policy.onNewSession()
        assertTrue(policy.shouldTrigger("AA:BB", hasConnectedEarBattery = true, now = 41_000L))
    }
}
