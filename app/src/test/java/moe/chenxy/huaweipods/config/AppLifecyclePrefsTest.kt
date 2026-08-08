package moe.chenxy.huaweipods.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLifecyclePrefsTest {
    @Test
    fun `new install shows onboarding without updated dialog`() {
        val decision = decideLaunch(
            snapshot = LaunchSnapshot(
                onboardingCompleted = false,
                previousVersionCode = null,
                hasExistingInstallation = false,
            ),
            currentVersionCode = 4L,
        )

        assertTrue(decision.showOnboarding)
        assertFalse(decision.showUpdated)
    }

    @Test
    fun `legacy upgrade skips onboarding and shows updated dialog`() {
        val decision = decideLaunch(
            snapshot = LaunchSnapshot(
                onboardingCompleted = false,
                previousVersionCode = null,
                hasExistingInstallation = true,
            ),
            currentVersionCode = 4L,
        )

        assertFalse(decision.showOnboarding)
        assertTrue(decision.showUpdated)
        assertTrue(decision.completeOnboardingMigration)
    }

    @Test
    fun `recorded older version upgrade skips onboarding and shows updated dialog`() {
        val decision = decideLaunch(
            snapshot = LaunchSnapshot(
                onboardingCompleted = false,
                previousVersionCode = 3L,
                hasExistingInstallation = true,
            ),
            currentVersionCode = 4L,
        )

        assertFalse(decision.showOnboarding)
        assertTrue(decision.showUpdated)
        assertFalse(decision.completeOnboardingMigration)
    }

    @Test
    fun `unacknowledged update remains pending across launches`() {
        val snapshot = LaunchSnapshot(
            onboardingCompleted = true,
            previousVersionCode = 3L,
            hasExistingInstallation = true,
        )

        assertTrue(decideLaunch(snapshot, currentVersionCode = 4L).showUpdated)
        assertTrue(decideLaunch(snapshot, currentVersionCode = 4L).showUpdated)
    }

    @Test
    fun `completed onboarding on current version enters normally`() {
        val decision = decideLaunch(
            snapshot = LaunchSnapshot(
                onboardingCompleted = true,
                previousVersionCode = 4L,
                hasExistingInstallation = true,
            ),
            currentVersionCode = 4L,
        )

        assertEquals(LaunchDecision(showOnboarding = false, showUpdated = false), decision)
    }

    @Test
    fun `unfinished onboarding resumes on the same version`() {
        val decision = decideLaunch(
            snapshot = LaunchSnapshot(
                onboardingCompleted = false,
                previousVersionCode = 4L,
                hasExistingInstallation = false,
            ),
            currentVersionCode = 4L,
        )

        assertEquals(LaunchDecision(showOnboarding = true, showUpdated = false), decision)
    }

    @Test
    fun `automatic update check is disabled when preference is off`() {
        assertFalse(
            shouldRunAutomaticUpdateCheck(
                enabled = false,
                lastCheckAtMillis = 0L,
                nowMillis = 100L,
            ),
        )
    }

    @Test
    fun `automatic update check runs initially and then once per day`() {
        val day = AppLifecyclePrefs.AUTO_CHECK_INTERVAL_MS

        assertTrue(
            shouldRunAutomaticUpdateCheck(
                enabled = true,
                lastCheckAtMillis = 0L,
                nowMillis = 1L,
            ),
        )
        assertFalse(
            shouldRunAutomaticUpdateCheck(
                enabled = true,
                lastCheckAtMillis = 1_000L,
                nowMillis = 1_000L + day - 1L,
            ),
        )
        assertTrue(
            shouldRunAutomaticUpdateCheck(
                enabled = true,
                lastCheckAtMillis = 1_000L,
                nowMillis = 1_000L + day,
            ),
        )
    }

    @Test
    fun `clock rollback does not suppress automatic update checks indefinitely`() {
        assertTrue(
            shouldRunAutomaticUpdateCheck(
                enabled = true,
                lastCheckAtMillis = 10_000L,
                nowMillis = 5_000L,
            ),
        )
    }

    @Test
    fun `downgrade resets automatic update check throttle`() {
        assertTrue(
            shouldResetAutomaticUpdateCheckAfterVersionChange(
                previousVersionCode = 7L,
                currentVersionCode = 6L,
            ),
        )
        assertFalse(
            shouldResetAutomaticUpdateCheckAfterVersionChange(
                previousVersionCode = 6L,
                currentVersionCode = 7L,
            ),
        )
        assertFalse(
            shouldResetAutomaticUpdateCheckAfterVersionChange(
                previousVersionCode = 7L,
                currentVersionCode = 7L,
            ),
        )
    }
}
