package moe.chenxy.huaweipods.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {
    @Test
    fun `parses HuaweiPods version code and version name tag`() {
        assertEquals(
            ReleaseTag(versionCode = 4L, versionName = "1.2.0"),
            GitHubReleaseChecker.parseTag("4-1.2.0"),
        )
        assertEquals(
            ReleaseTag(versionCode = 12L, versionName = "2.0.0-beta.1"),
            GitHubReleaseChecker.parseTag("12-2.0.0-beta.1"),
        )
    }

    @Test
    fun `rejects legacy and malformed release tags`() {
        listOf(
            "v1.2.0",
            "1.2.0",
            "0-1.0.0",
            "4-",
            "4-1.2.0 beta",
            "not-a-release",
        ).forEach { tag ->
            assertEquals(tag, null, GitHubReleaseChecker.parseTag(tag))
        }
    }

    @Test
    fun `uses Android version code as update ordering`() {
        val remote = ReleaseTag(versionCode = 4L, versionName = "1.2.0")

        assertTrue(GitHubReleaseChecker.isNewer(remote, currentVersionCode = 3L))
        assertFalse(GitHubReleaseChecker.isNewer(remote, currentVersionCode = 4L))
        assertFalse(GitHubReleaseChecker.isNewer(remote, currentVersionCode = 5L))
    }

    @Test
    fun `parses trusted latest release response`() {
        val result = GitHubReleaseChecker.parseReleaseResponse(
            """
            {
              "tag_name": "4-1.2.0",
              "html_url": "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
              "body": "Battery fixes and model integration",
              "ignored": true
            }
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            GitHubRelease(
                tag = "4-1.2.0",
                versionCode = 4L,
                versionName = "1.2.0",
                releaseUrl = "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
                changelog = "Battery fixes and model integration",
            ),
            result.getOrNull(),
        )
    }

    @Test
    fun `accepts only HTTPS release pages from the HuaweiPods GitHub repository`() {
        assertTrue(
            GitHubReleaseChecker.isTrustedReleaseUrl(
                "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
            ),
        )

        listOf(
            "http://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
            "https://github.com.evil.example/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
            "https://github.com/other/HuaweiPods/releases/tag/4-1.2.0",
            "https://github.com/Nshpiter/HuaweiPods/issues",
            "https://user@github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
            "https://github.com:444/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
        ).forEach { url ->
            assertFalse(url, GitHubReleaseChecker.isTrustedReleaseUrl(url))
        }
    }

    @Test
    fun `rejects a release response with an untrusted URL`() {
        val result = GitHubReleaseChecker.parseReleaseResponse(
            """
            {
              "tag_name": "4-1.2.0",
              "html_url": "https://example.com/HuaweiPods.apk",
              "body": ""
            }
            """.trimIndent(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `caps release notes before rendering`() {
        val longReleaseNotes = "x".repeat(GitHubReleaseChecker.MAX_CHANGELOG_CHARS + 1_000)
        val result = GitHubReleaseChecker.parseReleaseResponse(
            """
            {
              "tag_name": "4-1.2.0",
              "html_url": "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0",
              "body": "$longReleaseNotes"
            }
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            GitHubReleaseChecker.MAX_CHANGELOG_CHARS,
            result.getOrThrow().changelog.length,
        )
    }
}
