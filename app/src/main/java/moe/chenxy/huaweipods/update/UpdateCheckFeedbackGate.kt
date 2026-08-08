package moe.chenxy.huaweipods.update

/** Keeps a manual tap from being lost when an automatic update check is already running. */
internal class UpdateCheckFeedbackGate {
    private var requested = false

    fun request() {
        requested = true
    }

    fun shouldShow(startedManually: Boolean): Boolean = startedManually || requested

    fun reset() {
        requested = false
    }
}

/** Prevents multiple Miuix overlay dialogs from competing for the same host. */
internal fun shouldShowAvailableUpdateDialog(
    hasAvailableUpdate: Boolean,
    showUpdatedAppDialog: Boolean,
    showRestartScopeDialog: Boolean,
): Boolean = hasAvailableUpdate && !showUpdatedAppDialog && !showRestartScopeDialog
