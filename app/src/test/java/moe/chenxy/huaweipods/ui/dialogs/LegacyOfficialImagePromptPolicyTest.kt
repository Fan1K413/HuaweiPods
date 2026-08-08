package moe.chenxy.huaweipods.ui.dialogs

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyOfficialImagePromptPolicyTest {
    @Test
    fun `only connected FreeBuds 3 detail without a box image is eligible`() {
        assertTrue(
            LegacyOfficialImagePromptPolicy.isEligible(
                detailVisible = true,
                deviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                connectedAddress = ADDRESS,
                manualBoxImagePath = null,
                cloudBoxImagePath = null,
            ),
        )
        assertFalse(eligible(detailVisible = false))
        assertFalse(eligible(deviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
        assertFalse(eligible(connectedAddress = ""))
        assertFalse(eligible(manualBoxImagePath = "/images/manual.png"))
        assertFalse(eligible(cloudBoxImagePath = "/images/official.png"))
    }

    @Test
    fun `eligible address is claimed once per process gate regardless of casing`() {
        val gate = LegacyOfficialImagePromptGate()

        assertTrue(gate.claimEligible(ADDRESS))
        assertFalse(gate.claimEligible(ADDRESS.lowercase()))
        assertTrue(gate.claimEligible(OTHER_ADDRESS))
    }

    @Test
    fun `ineligible visit does not consume the later eligible prompt`() {
        val gate = LegacyOfficialImagePromptGate()

        assertFalse(
            gate.claimIfEligible(
                detailVisible = true,
                deviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                connectedAddress = ADDRESS,
                manualBoxImagePath = "/images/manual.png",
                cloudBoxImagePath = null,
            ),
        )
        assertTrue(gate.claimEligible(ADDRESS))
    }

    private fun eligible(
        detailVisible: Boolean = true,
        deviceRoute: HuaweiDeviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
        connectedAddress: String = ADDRESS,
        manualBoxImagePath: String? = null,
        cloudBoxImagePath: String? = null,
    ): Boolean = LegacyOfficialImagePromptPolicy.isEligible(
        detailVisible = detailVisible,
        deviceRoute = deviceRoute,
        connectedAddress = connectedAddress,
        manualBoxImagePath = manualBoxImagePath,
        cloudBoxImagePath = cloudBoxImagePath,
    )

    private fun LegacyOfficialImagePromptGate.claimEligible(address: String): Boolean =
        claimIfEligible(
            detailVisible = true,
            deviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            connectedAddress = address,
            manualBoxImagePath = null,
            cloudBoxImagePath = null,
        )

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val OTHER_ADDRESS = "11:22:33:44:55:66"
    }
}
