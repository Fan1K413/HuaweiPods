package moe.chenxy.huaweipods.ui.pages

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentationNavigationTrackerTest {
    @Test
    fun `late callback from the previous menu page is ignored`() {
        val tracker = DocumentationNavigationTracker()
        tracker.onPageStarted("https://huaweipods.npiter.de/guide/getting-started")
        tracker.onPageStarted("https://huaweipods.npiter.de/support/")

        assertFalse(tracker.onPageError("https://huaweipods.npiter.de/guide/getting-started"))
        assertTrue(tracker.onPageFinished("https://huaweipods.npiter.de/support/"))
    }

    @Test
    fun `fragment callbacks belong to the same document`() {
        val tracker = DocumentationNavigationTracker()
        tracker.onPageStarted("https://huaweipods.npiter.de/support/#models")

        assertTrue(tracker.onPageFinished("https://huaweipods.npiter.de/support/#images"))
    }

    @Test
    fun `host case and default HTTPS port are normalized`() {
        val tracker = DocumentationNavigationTracker()
        tracker.onPageStarted("https://HUAWEIPODS.NPITER.DE:443/sponsor/")

        assertTrue(tracker.onPageFinished("https://huaweipods.npiter.de/sponsor/"))
        assertFalse(tracker.onPageError("https://huaweipods.npiter.de/support/"))
    }

    @Test
    fun `error after a successful page finish is ignored`() {
        val tracker = DocumentationNavigationTracker()
        val url = "https://huaweipods.npiter.de/support/"
        tracker.onPageStarted(url)

        assertTrue(tracker.onPageFinished(url))
        assertFalse(tracker.onPageError(url))
    }

    @Test
    fun `compatibility page finish does not erase a real failure`() {
        val tracker = DocumentationNavigationTracker()
        val url = "https://huaweipods.npiter.de/support/"
        tracker.onPageStarted(url)

        assertTrue(tracker.onPageError(url))
        assertFalse(tracker.onPageFinished(url))
    }
}
