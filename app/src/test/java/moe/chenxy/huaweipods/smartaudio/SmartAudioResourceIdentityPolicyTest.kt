package moe.chenxy.huaweipods.smartaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAudioResourceIdentityPolicyTest {
    @Test
    fun `normalizes an exact current-device identity`() {
        assertEquals(
            SmartAudioResourceIdentity(
                address = "AA:BB:CC:DD:EE:FF",
                modelId = "000027",
                subModelId = "00",
            ),
            SmartAudioResourceIdentityPolicy.normalize(
                address = "aa:bb:cc:dd:ee:ff",
                modelId = "000027",
                subModelId = "0x00",
            ),
        )
    }

    @Test
    fun `rejects names missing color and malformed addresses`() {
        assertNull(
            SmartAudioResourceIdentityPolicy.normalize(
                "AA:BB:CC:DD:EE:FF",
                "FreeBuds 3",
                "white",
            ),
        )
        assertNull(SmartAudioResourceIdentityPolicy.normalize("AA:BB", "000027", "00"))
        assertNull(SmartAudioResourceIdentityPolicy.normalize("AA:BB:CC:DD:EE:FF", "000027", null))
    }

    @Test
    fun `only known full-identity mutations are hooked`() {
        assertTrue(
            SmartAudioResourceIdentityPolicy.isIdentityMutation(
                "e1",
                listOf("java.lang.String"),
            ),
        )
        assertTrue(
            SmartAudioResourceIdentityPolicy.isIdentityMutation(
                "U0",
                listOf("com.huawei.audiodevicekit.audiobluetooth.layer.protocol.mbb.j"),
            ),
        )
        // Y0 只写 MAC；此时 model/subModel 可能仍属于上一副耳机，不能据此发布身份。
        assertFalse(
            SmartAudioResourceIdentityPolicy.isIdentityMutation(
                "Y0",
                listOf("java.lang.String"),
            ),
        )
        assertFalse(SmartAudioResourceIdentityPolicy.isIdentityMutation("e1", listOf("int")))
    }
}
