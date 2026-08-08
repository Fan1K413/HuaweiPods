package moe.chenxy.huaweipods.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPolicyTest {
    @Test
    fun `navigation clamps boundaries and only finishes from last page`() {
        assertEquals(
            OnboardingNavigationResult(page = 0),
            reduceOnboardingNavigation(-4, OnboardingNavigationAction.PREVIOUS),
        )
        assertEquals(
            OnboardingNavigationResult(page = 1),
            reduceOnboardingNavigation(0, OnboardingNavigationAction.NEXT),
        )
        assertEquals(
            OnboardingNavigationResult(page = 2),
            reduceOnboardingNavigation(1, OnboardingNavigationAction.NEXT),
        )
        assertEquals(
            OnboardingNavigationResult(page = 2, finish = true),
            reduceOnboardingNavigation(2, OnboardingNavigationAction.NEXT),
        )
        assertEquals(
            OnboardingNavigationResult(page = 2, finish = true),
            reduceOnboardingNavigation(99, OnboardingNavigationAction.NEXT),
        )
    }

    @Test
    fun `layout policy covers portrait landscape and compact windows`() {
        assertEquals(
            OnboardingLayoutPolicy(landscape = false, compact = false),
            onboardingLayoutPolicy(widthDp = 412, heightDp = 915),
        )
        assertEquals(
            OnboardingLayoutPolicy(landscape = true, compact = true),
            onboardingLayoutPolicy(widthDp = 800, heightDp = 480),
        )
        assertTrue(onboardingLayoutPolicy(widthDp = 320, heightDp = 700).compact)
    }

    @Test
    fun `zero animator scale selects static presentation`() {
        assertFalse(onboardingMotionEnabled(0f))
        assertTrue(onboardingMotionEnabled(0.5f))
        assertTrue(onboardingMotionEnabled(1f))
    }
}
