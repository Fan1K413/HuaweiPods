package moe.chenxy.huaweipods.smartaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAudioImageJobPolicyTest {
    @Test
    fun `job id is stable and namespaced per exact identity`() {
        val white = SmartAudioResourceIdentity("AA:BB:CC:DD:EE:FF", "000027", "00")
        val black = white.copy(subModelId = "01")

        assertEquals(SmartAudioImageJobPolicy.jobId(white), SmartAudioImageJobPolicy.jobId(white.copy()))
        assertNotEquals(SmartAudioImageJobPolicy.jobId(white), SmartAudioImageJobPolicy.jobId(black))
        assertTrue(SmartAudioImageJobPolicy.jobId(white) in 0x4800_0000..0x4FFF_FFFF)
    }

    @Test
    fun `failed jobs wait for a new identity event instead of unbounded background retry`() {
        assertFalse(SmartAudioImageJobPolicy.shouldRescheduleAfterFailure())
    }
}
