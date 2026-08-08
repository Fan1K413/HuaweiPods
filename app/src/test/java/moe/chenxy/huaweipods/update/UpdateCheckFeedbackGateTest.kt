package moe.chenxy.huaweipods.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckFeedbackGateTest {
    @Test
    fun `manual tap upgrades an automatic check to visible feedback`() {
        val gate = UpdateCheckFeedbackGate()

        assertFalse(gate.shouldShow(startedManually = false))
        gate.request()
        assertTrue(gate.shouldShow(startedManually = false))
    }

    @Test
    fun `reset clears feedback requested by a completed check`() {
        val gate = UpdateCheckFeedbackGate()
        gate.request()

        gate.reset()

        assertFalse(gate.shouldShow(startedManually = false))
        assertTrue(gate.shouldShow(startedManually = true))
    }
}
