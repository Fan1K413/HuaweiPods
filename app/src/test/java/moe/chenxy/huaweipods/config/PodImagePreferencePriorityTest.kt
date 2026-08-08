package moe.chenxy.huaweipods.config

import org.junit.Assert.assertEquals
import org.junit.Test

class PodImagePreferencePriorityTest {
    @Test
    fun `manual image wins over cloud image`() {
        val pref = EarphonePref(
            address = "AA:BB:CC:DD:EE:FF",
            name = "FreeBuds",
            boxImagePath = "/images/manual.png",
            cloudBoxImagePath = "/images/cloud.png",
        )

        assertEquals("/images/manual.png", pref.preferredImagePath(PodImageResource.BOX))
    }

    @Test
    fun `cloud image is used when manual image is absent`() {
        val pref = EarphonePref(
            address = "AA:BB:CC:DD:EE:FF",
            name = "FreeBuds",
            cloudBoxImagePath = "/images/cloud.png",
        )

        assertEquals("/images/cloud.png", pref.preferredImagePath(PodImageResource.BOX))
    }
}
