package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAncUiPolicyTest {
    @Test
    fun `programmatic Settings rendering never dispatches an ANC command`() {
        assertTrue(shouldDispatchSettingsAncCommand(internalRenderDepth = 0))
        assertFalse(shouldDispatchSettingsAncCommand(internalRenderDepth = 1))
        assertFalse(shouldDispatchSettingsAncCommand(internalRenderDepth = 2))
    }

    @Test
    fun `confirmed selection is never replayed as a device command`() {
        val gate = SettingsAncPendingGate(timeoutMs = 5_000L)
        val noise = SettingsAncSelection(status = 2, subMode = 2)
        val transparency = SettingsAncSelection(status = 3, subMode = 2)

        assertTrue(gate.tryBegin(noise, nowMs = 100L))
        assertFalse(gate.tryBegin(noise, nowMs = 200L))
        assertTrue(gate.shouldAcceptConfirmation(noise, nowMs = 300L))
        assertNull(gate.current())
        assertFalse(gate.tryBegin(noise, nowMs = 400L))
        assertTrue(gate.tryBegin(transparency, nowMs = 500L))
    }

    @Test
    fun `mode callback and its level callback are coalesced while waiting for confirmation`() {
        val gate = SettingsAncPendingGate(timeoutMs = 5_000L)

        assertTrue(gate.tryBegin(SettingsAncSelection(status = 2), nowMs = 100L))
        assertFalse(gate.tryBegin(SettingsAncSelection(status = 2, subMode = 3), nowMs = 120L))
        assertTrue(
            gate.shouldAcceptConfirmation(
                SettingsAncSelection(status = 2, subMode = 3),
                nowMs = 300L,
            ),
        )
        assertFalse(gate.tryBegin(SettingsAncSelection(status = 2), nowMs = 400L))
        assertTrue(gate.tryBegin(SettingsAncSelection(status = 2, subMode = 1), nowMs = 500L))
    }

    @Test
    fun `stale confirmation is ignored while a newer selection is pending`() {
        val gate = SettingsAncPendingGate(timeoutMs = 5_000L)
        val off = SettingsAncSelection(status = 1)
        gate.shouldAcceptConfirmation(off, nowMs = 0L)
        gate.tryBegin(SettingsAncSelection(status = 2, subMode = 1), nowMs = 100L)

        assertFalse(
            gate.shouldAcceptConfirmation(
                off,
                nowMs = 300L,
            ),
        )
        assertTrue(gate.hasPending(nowMs = 400L))
        assertFalse(gate.tryBegin(off, nowMs = 500L))
    }

    @Test
    fun `newer different selection replaces the pending intent`() {
        val gate = SettingsAncPendingGate(timeoutMs = 5_000L)
        val noise = SettingsAncSelection(status = 2, subMode = 1)
        val transparency = SettingsAncSelection(status = 3, subMode = 2)

        assertTrue(gate.tryBegin(noise, nowMs = 100L))
        assertTrue(gate.tryBegin(transparency, nowMs = 200L))
        assertFalse(gate.shouldAcceptConfirmation(noise, nowMs = 300L))
        assertTrue(gate.shouldAcceptConfirmation(transparency, nowMs = 400L))
    }

    @Test
    fun `timed out pending selection yields to verified device state`() {
        val gate = SettingsAncPendingGate(timeoutMs = 5_000L)
        gate.tryBegin(SettingsAncSelection(status = 3, subMode = 2), nowMs = 100L)

        assertTrue(
            gate.shouldAcceptConfirmation(
                SettingsAncSelection(status = 1),
                nowMs = 5_100L,
            ),
        )
        assertNull(gate.current())
        assertFalse(gate.tryBegin(SettingsAncSelection(status = 1), nowMs = 5_200L))
    }

    @Test
    fun `reset forgets the previous device confirmation`() {
        val gate = SettingsAncPendingGate(timeoutMs = 5_000L)
        val off = SettingsAncSelection(status = 1)
        gate.shouldAcceptConfirmation(off, nowMs = 100L)

        assertFalse(gate.tryBegin(off, nowMs = 200L))
        gate.reset()
        assertTrue(gate.tryBegin(off, nowMs = 300L))
    }
}
