package moe.chenxy.huaweipods.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodImageProviderAccessPolicyTest {
    @Test
    fun `only module and actual image scopes may open files`() {
        listOf(
            "moe.chenxy.huaweipods",
            "com.android.bluetooth",
            "com.android.settings",
            "com.milink.service",
            "com.xiaomi.bluetooth",
        ).forEach { assertTrue(it, PodImageProviderAccessPolicy.mayOpenImage(it)) }

        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.huawei.smartaudio"))
        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.example.thirdparty"))
        assertFalse(PodImageProviderAccessPolicy.mayOpenImage(null))
    }

    @Test
    fun `only module and Smart Audio may submit an identity`() {
        assertTrue(PodImageProviderAccessPolicy.maySubmitSmartAudioIdentity("moe.chenxy.huaweipods"))
        assertTrue(PodImageProviderAccessPolicy.maySubmitSmartAudioIdentity("com.huawei.smartaudio"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitSmartAudioIdentity("com.android.settings"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitSmartAudioIdentity("com.example.thirdparty"))
    }
}
