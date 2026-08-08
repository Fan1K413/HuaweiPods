package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeClip2AudioStateTrackerTest {
    @Test
    fun `write success stays pending until verified readback`() {
        val tracker = FreeClip2AudioStateTracker()
        val token = requireNotNull(
            tracker.beginWrite(
                FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.HEAD_TRACKING),
                nowMs = 100L,
            ),
        )

        assertTrue(tracker.completeWrite(token, success = true))
        assertNull(tracker.confirmedState)
        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, tracker.pendingUpdate()?.mode)

        val confirmed = tracker.acceptQuery(
            tracker.beginQuery(),
            FreeClip2AudioState(
                mode = FreeClip2SpatialAudioMode.HEAD_TRACKING,
                scene = FreeClip2SpatialScene.DEFAULT,
            ),
        )

        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, confirmed?.mode)
        assertNull(tracker.pendingUpdate())
    }

    @Test
    fun `query started before a newer write cannot restore old mode`() {
        val tracker = FreeClip2AudioStateTracker()
        val oldQuery = tracker.beginQuery()
        val write = requireNotNull(
            tracker.beginWrite(
                FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.HEAD_TRACKING),
                nowMs = 100L,
            ),
        )

        assertNull(
            tracker.acceptQuery(
                oldQuery,
                FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.FIXED),
            ),
        )
        assertNull(tracker.confirmedState)
        assertTrue(tracker.completeWrite(write, success = true))
    }

    @Test
    fun `newer query supersedes an older query response`() {
        val tracker = FreeClip2AudioStateTracker()
        val oldQuery = tracker.beginQuery()
        val latestQuery = tracker.beginQuery()

        assertNull(
            tracker.acceptQuery(
                oldQuery,
                FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.FIXED),
            ),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.HEAD_TRACKING,
            tracker.acceptQuery(
                latestQuery,
                FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.HEAD_TRACKING),
            )?.mode,
        )
    }

    @Test
    fun `duplicate pending write is rejected but a newer selection replaces it`() {
        val tracker = FreeClip2AudioStateTracker()
        val fixed = FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.FIXED)
        val headTracking = FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.HEAD_TRACKING)

        val first = requireNotNull(tracker.beginWrite(fixed, nowMs = 100L))
        assertNull(tracker.beginWrite(fixed, nowMs = 200L))
        val latest = requireNotNull(tracker.beginWrite(headTracking, nowMs = 300L))

        assertFalse(tracker.completeWrite(first, success = true))
        assertTrue(tracker.completeWrite(latest, success = true))
        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, tracker.pendingUpdate()?.mode)
    }

    @Test
    fun `external confirmed state updates without a pending write`() {
        val tracker = FreeClip2AudioStateTracker()

        val state = tracker.acceptQuery(
            tracker.beginQuery(),
            FreeClip2AudioState(
                mode = FreeClip2SpatialAudioMode.FIXED,
                scene = FreeClip2SpatialScene.CINEMA,
            ),
        )

        assertEquals(FreeClip2SpatialAudioMode.FIXED, state?.mode)
        assertEquals(FreeClip2SpatialScene.CINEMA, state?.scene)
    }

    @Test
    fun `same write can be retried after confirmation timeout`() {
        val tracker = FreeClip2AudioStateTracker()
        val update = FreeClip2AudioState(mode = FreeClip2SpatialAudioMode.FIXED)

        requireNotNull(tracker.beginWrite(update, nowMs = 100L))

        assertNull(tracker.beginWrite(update, nowMs = 4_999L))
        requireNotNull(tracker.beginWrite(update, nowMs = 5_100L))
    }
}
