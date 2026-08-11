package moe.chenxy.huaweipods.hook

/** 系统设置页中的一次 ANC 选择；仅用于隔离宿主回绘与真实用户操作。 */
internal data class SettingsAncSelection(
    val status: Int,
    val subMode: Int? = null,
)

/** 模块主动回绘宿主控件时，不得把回调再次当成用户操作下发到耳机。 */
internal fun shouldDispatchSettingsAncCommand(internalRenderDepth: Int): Boolean =
    internalRenderDepth <= 0

/**
 * 等待耳机确认期间合并重复点击，并拒绝写入前已经在途的旧状态回包。
 * 超时后恢复以耳机回读为准，避免写失败时界面永久停留在乐观状态。
 */
internal class SettingsAncPendingGate(
    private val timeoutMs: Long = 5_000L,
) {
    private var pending: SettingsAncSelection? = null
    private var confirmed: SettingsAncSelection? = null
    private var pendingSinceMs = 0L

    fun tryBegin(selection: SettingsAncSelection, nowMs: Long): Boolean {
        expirePending(nowMs)
        if (
            pending?.matches(selection) == true &&
            nowMs - pendingSinceMs in 0 until timeoutMs
        ) return false
        // 小米设置页会异步重放当前选中项；与耳机确认值相同的调用只是回绘，不能再次写设备。
        if (confirmed?.matches(selection) == true) return false
        pending = selection
        pendingSinceMs = nowMs
        return true
    }

    fun shouldAcceptConfirmation(confirmed: SettingsAncSelection, nowMs: Long): Boolean {
        val current = pending
        if (current == null) {
            this.confirmed = confirmed
            return true
        }
        if (nowMs - pendingSinceMs !in 0 until timeoutMs) {
            clearPending()
            this.confirmed = confirmed
            return true
        }
        if (!current.matches(confirmed)) return false
        clearPending()
        this.confirmed = confirmed
        return true
    }

    fun hasPending(nowMs: Long): Boolean {
        expirePending(nowMs)
        return pending != null
    }

    fun clearPending() {
        pending = null
        pendingSinceMs = 0L
    }

    fun reset() {
        clearPending()
        confirmed = null
    }

    internal fun current(): SettingsAncSelection? = pending

    internal fun lastConfirmed(): SettingsAncSelection? = confirmed

    private fun expirePending(nowMs: Long) {
        if (pending != null && nowMs - pendingSinceMs !in 0 until timeoutMs) {
            clearPending()
        }
    }

    private fun SettingsAncSelection.matches(other: SettingsAncSelection): Boolean =
        status == other.status && (subMode == null || other.subMode == null || subMode == other.subMode)
}
