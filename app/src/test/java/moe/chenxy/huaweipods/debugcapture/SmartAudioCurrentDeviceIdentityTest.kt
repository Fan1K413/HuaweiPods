package moe.chenxy.huaweipods.debugcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAudioCurrentDeviceIdentityTest {
    @Test
    fun `normalizes legacy FreeBuds 3 current-device identity`() {
        assertEquals(
            SmartAudioDeviceIdentity(modelId = "000027", subModelId = "00"),
            smartAudioCurrentDeviceIdentity(
                modelId = "000027",
                subModelId = "0x00",
            ),
        )
    }

    @Test
    fun `rejects missing or malformed identity instead of guessing`() {
        assertNull(smartAudioCurrentDeviceIdentity("000027", null))
        assertNull(smartAudioCurrentDeviceIdentity("FreeBuds 3", "00"))
        assertNull(smartAudioCurrentDeviceIdentity("000027", "white"))
        assertNull(smartAudioCurrentDeviceIdentity("000027", "white00"))
    }

    @Test
    fun `current-device bus is an address-bound identity source`() {
        assertTrue(CaptureStore.isAddressBoundIdentitySource("current_device_bus"))
        assertTrue(CaptureStore.isAddressBoundIdentitySource("current_device_bus+official_url"))
        assertTrue(CaptureStore.isAddressBoundIdentitySource("device_info_tlv"))
        assertFalse(CaptureStore.isAddressBoundIdentitySource("official_url"))
        assertFalse(CaptureStore.isAddressBoundIdentitySource(null))
    }

    @Test
    fun `matches only narrow current-device identity mutation methods`() {
        assertTrue(
            isSmartAudioCurrentDeviceIdentityMutation("e1", listOf("java.lang.String")),
        )
        assertTrue(
            isSmartAudioCurrentDeviceIdentityMutation("a1", listOf("java.lang.String")),
        )
        assertTrue(
            isSmartAudioCurrentDeviceIdentityMutation(
                "U0",
                listOf("com.huawei.audiodevicekit.audiobluetooth.layer.protocol.mbb.j"),
            ),
        )

        assertFalse(isSmartAudioCurrentDeviceIdentityMutation("T0", emptyList()))
        assertFalse(
            isSmartAudioCurrentDeviceIdentityMutation("Y0", listOf("java.lang.String")),
        )
        assertFalse(
            isSmartAudioCurrentDeviceIdentityMutation("e1", listOf("java.lang.Integer")),
        )
        assertFalse(
            isSmartAudioCurrentDeviceIdentityMutation("b1", listOf("java.lang.String")),
        )
    }
}
