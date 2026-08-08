package moe.chenxy.huaweipods.smartaudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialImageIdentityPublishResultTest {
    @Test
    fun `verified route does not depend on image job scheduling`() {
        assertTrue(
            OfficialImageIdentityPublishResult(
                identityVerified = true,
                routeBound = true,
                imageScheduled = false,
            ).routeReady,
        )
        assertFalse(
            OfficialImageIdentityPublishResult(
                identityVerified = true,
                routeBound = false,
                imageScheduled = true,
            ).routeReady,
        )
    }
}
