package moe.chenxy.huaweipods.debugcapture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAudioCaptureTargetTest {
    @Test
    fun onlySmartAudioIsAllowed() {
        assertTrue(SmartAudioCaptureTarget.isAllowedSender("com.huawei.smartaudio"))
        assertFalse(SmartAudioCaptureTarget.isAllowedSender("com.huawei.smarthome"))
        assertFalse(SmartAudioCaptureTarget.isAllowedSender(null))
    }

    @Test
    fun senderMustMatchSmartAudioSession() {
        assertTrue(
            SmartAudioCaptureTarget.matchesSession(
                sessionPackage = "com.huawei.smartaudio",
                senderPackage = "com.huawei.smartaudio",
            ),
        )
        assertFalse(
            SmartAudioCaptureTarget.matchesSession(
                sessionPackage = "com.huawei.smarthome",
                senderPackage = "com.huawei.smartaudio",
            ),
        )
        assertFalse(SmartAudioCaptureTarget.matchesSession(null, "com.huawei.smartaudio"))
    }
}
