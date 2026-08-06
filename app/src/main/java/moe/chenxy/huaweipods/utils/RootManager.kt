package moe.chenxy.huaweipods.utils

import java.util.concurrent.TimeUnit

object RootManager {
    private val packageNameRegex = Regex("^[A-Za-z0-9_.]+$")

    fun restartPackages(packages: Collection<String>): Boolean {
        val targets = packages.distinct().filter { it.matches(packageNameRegex) }
        if (targets.isEmpty()) return false

        return runCatching {
            val command = targets.joinToString(" && ") { "am force-stop $it" }
            val shellCommand = "exec >/dev/null 2>&1; $command"
            val process = ProcessBuilder("su", "-c", shellCommand).start()
            if (!process.waitFor(RESTART_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(PROCESS_CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)
    }

    private const val RESTART_TIMEOUT_SECONDS = 15L
    private const val PROCESS_CLEANUP_TIMEOUT_SECONDS = 2L
}
