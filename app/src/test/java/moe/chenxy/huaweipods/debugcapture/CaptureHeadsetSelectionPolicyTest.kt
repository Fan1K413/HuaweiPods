package moe.chenxy.huaweipods.debugcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureHeadsetSelectionPolicyTest {
    private val first = ConnectedHeadset(
        address = "AA:BB:CC:DD:EE:01",
        displayName = "HUAWEI FreeBuds 3",
        profiles = setOf(HeadsetProfile.A2DP),
    )
    private val second = ConnectedHeadset(
        address = "AA:BB:CC:DD:EE:02",
        displayName = "HUAWEI FreeBuds 5",
        profiles = setOf(HeadsetProfile.LE_AUDIO),
    )

    @Test
    fun automaticTarget_selectsOnlyUniqueConnectedDevice() {
        assertEquals(first, CaptureHeadsetSelectionPolicy.automaticTarget(success(first)))
        assertNull(CaptureHeadsetSelectionPolicy.automaticTarget(success()))
        assertNull(CaptureHeadsetSelectionPolicy.automaticTarget(success(first, second)))
        assertNull(CaptureHeadsetSelectionPolicy.automaticTarget(DetectionResult.BluetoothDisabled))
    }

    @Test
    fun selectedTarget_requiresConnectedSourceAndCurrentDetectionMembership() {
        val result = success(first, second)

        assertEquals(
            first,
            CaptureHeadsetSelectionPolicy.selectedTarget(result, first.address.lowercase(), true),
        )
        assertNull(CaptureHeadsetSelectionPolicy.selectedTarget(result, first.address, false))
        assertNull(CaptureHeadsetSelectionPolicy.selectedTarget(result, "AA:BB:CC:DD:EE:03", true))
        assertNull(CaptureHeadsetSelectionPolicy.selectedTarget(success(), first.address, true))
        assertNull(CaptureHeadsetSelectionPolicy.selectedTarget(DetectionResult.PermissionRequired, first.address, true))
    }

    private fun success(vararg devices: ConnectedHeadset) = DetectionResult.Success(
        devices = devices.toList(),
        timedOut = false,
    )
}
