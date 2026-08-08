package moe.chenxy.huaweipods.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class GitHubRelease(
    val tag: String,
    val versionCode: Long,
    val versionName: String,
    val releaseUrl: String,
    val changelog: String,
)

sealed interface UpdateCheckResult {
    data class Available(val release: GitHubRelease) : UpdateCheckResult

    data class UpToDate(val latest: GitHubRelease) : UpdateCheckResult

    data class Failure(
        val message: String,
        val statusCode: Int? = null,
    ) : UpdateCheckResult
}

internal data class ReleaseTag(
    val versionCode: Long,
    val versionName: String,
)

object GitHubReleaseChecker {
    const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Nshpiter/HuaweiPods/releases/latest"
    const val LATEST_RELEASE_PAGE =
        "https://github.com/Nshpiter/HuaweiPods/releases/latest"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_RESPONSE_CHARS = 1_048_576
    internal const val MAX_CHANGELOG_CHARS = 32_768
    private const val RELEASE_PATH_PREFIX = "/Nshpiter/HuaweiPods/releases/"
    private const val RELEASE_TAG_PATH_PREFIX = "${RELEASE_PATH_PREFIX}tag/"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(
        currentVersionCode: Long,
        currentVersionName: String,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        checkBlocking(currentVersionCode, currentVersionName)
    }

    internal fun parseTag(tag: String): ReleaseTag? {
        val match = TAG_PATTERN.matchEntire(tag.trim()) ?: return null
        val versionCode = match.groupValues[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val versionName = match.groupValues[2].trim()
        if (!VERSION_NAME_PATTERN.matches(versionName)) return null
        return ReleaseTag(versionCode = versionCode, versionName = versionName)
    }

    internal fun isNewer(
        remote: ReleaseTag,
        currentVersionCode: Long,
    ): Boolean = remote.versionCode > currentVersionCode

    internal fun parseReleaseResponse(payload: String): Result<GitHubRelease> = runCatching {
        val response = json.decodeFromString(GitHubReleaseResponse.serializer(), payload)
        val tag = response.tagName.trim()
        val releaseTag = requireNotNull(parseTag(tag)) { "Invalid release tag: $tag" }
        val releaseUrl = response.htmlUrl.trim()
        require(isTrustedReleaseUrl(releaseUrl)) { "Untrusted GitHub release URL" }

        GitHubRelease(
            tag = tag,
            versionCode = releaseTag.versionCode,
            versionName = releaseTag.versionName,
            releaseUrl = releaseUrl,
            changelog = response.body.orEmpty().take(MAX_CHANGELOG_CHARS),
        )
    }

    /**
     * GitHub's public /releases/latest page redirects to the latest release tag. This provides a
     * small fallback for networks where github.com works but api.github.com is unavailable.
     */
    internal fun parseLatestReleaseRedirect(location: String): Result<GitHubRelease> = runCatching {
        val releaseUrl = location.trim()
        require(isTrustedReleaseUrl(releaseUrl)) { "Untrusted GitHub release redirect" }

        val uri = URI(releaseUrl)
        require(uri.query == null && uri.fragment == null) { "Unexpected release redirect suffix" }
        val path = uri.path
        require(path.startsWith(RELEASE_TAG_PATH_PREFIX, ignoreCase = true)) {
            "Invalid GitHub release redirect"
        }
        val tag = path.substring(RELEASE_TAG_PATH_PREFIX.length)
        require(tag.isNotBlank() && tag == tag.trim() && '/' !in tag) {
            "Invalid GitHub release tag"
        }
        val releaseTag = requireNotNull(parseTag(tag)) { "Invalid release tag: $tag" }

        GitHubRelease(
            tag = tag,
            versionCode = releaseTag.versionCode,
            versionName = releaseTag.versionName,
            releaseUrl = releaseUrl,
            changelog = "",
        )
    }

    internal fun isTrustedReleaseUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.host.equals("github.com", ignoreCase = true)) return false
        if (uri.port != -1 || uri.userInfo != null) return false
        return uri.path.startsWith(RELEASE_PATH_PREFIX, ignoreCase = true)
    }

    private fun checkBlocking(
        currentVersionCode: Long,
        currentVersionName: String,
    ): UpdateCheckResult {
        val userAgent = "HuaweiPods/${currentVersionName.ifBlank { "unknown" }}"
        val apiResult = requestLatestReleaseFromApi(userAgent)
        val release = apiResult.getOrElse { apiError ->
            return requestLatestReleaseFromPage(userAgent).fold(
                onSuccess = { fallbackRelease ->
                    classify(fallbackRelease, currentVersionCode)
                },
                onFailure = { pageError ->
                    UpdateCheckResult.Failure(
                        message = buildString {
                            append(apiError.safeMessage("GitHub API request failed"))
                            append("; ")
                            append(pageError.safeMessage("GitHub release page request failed"))
                        },
                        statusCode = (apiError as? GitHubHttpStatusException)?.statusCode
                            ?: (pageError as? GitHubHttpStatusException)?.statusCode,
                    )
                },
            )
        }
        return classify(release, currentVersionCode)
    }

    private fun requestLatestReleaseFromApi(userAgent: String): Result<GitHubRelease> = runCatching {
        val connection = openConnection(LATEST_RELEASE_API, userAgent).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val statusCode = connection.responseCode
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw GitHubHttpStatusException("GitHub API", statusCode)
            }
            parseReleaseResponse(readLimitedResponse(connection)).getOrThrow()
        } finally {
            connection.disconnect()
        }
    }

    private fun requestLatestReleaseFromPage(userAgent: String): Result<GitHubRelease> = runCatching {
        val connection = openConnection(LATEST_RELEASE_PAGE, userAgent)
        try {
            val statusCode = connection.responseCode
            if (statusCode !in REDIRECT_STATUS_CODES) {
                throw GitHubHttpStatusException("GitHub release page", statusCode)
            }
            val location = connection.getHeaderField("Location").orEmpty()
            parseLatestReleaseRedirect(location).getOrThrow()
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, userAgent: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent)
        }

    private fun readLimitedResponse(connection: HttpURLConnection): String =
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            buildString {
                val buffer = CharArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (length + count > MAX_RESPONSE_CHARS) {
                        throw IOException("GitHub release response is too large")
                    }
                    append(buffer, 0, count)
                }
            }
        }

    private fun classify(release: GitHubRelease, currentVersionCode: Long): UpdateCheckResult =
        if (isNewer(ReleaseTag(release.versionCode, release.versionName), currentVersionCode)) {
            UpdateCheckResult.Available(release)
        } else {
            UpdateCheckResult.UpToDate(release)
        }

    private fun Throwable.safeMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    private val TAG_PATTERN = Regex("^(\\d+)-(.+)$")
    private val VERSION_NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._+\\-]*$")
    private val REDIRECT_STATUS_CODES = setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308,
    )
}

private class GitHubHttpStatusException(
    endpoint: String,
    val statusCode: Int,
) : IOException("$endpoint returned HTTP $statusCode")

@Serializable
private data class GitHubReleaseResponse(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null,
)
