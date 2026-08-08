package moe.chenxy.huaweipods.pods

/**
 * Keeps FreeClip 2 audio UI state tied to verified device readback.
 *
 * A successful RFCOMM write only means that the packet left the phone. It must not update the
 * confirmed state until a later query reports what the headset actually applied. Query tokens also
 * prevent a response queued before a newer write/query from restoring an obsolete selection.
 */
internal class FreeClip2AudioStateTracker {
    private companion object {
        const val DUPLICATE_WRITE_WINDOW_MS = 5_000L
    }

    class WriteToken internal constructor(
        internal val version: Long,
        internal val update: FreeClip2AudioState,
        internal val startedAtMs: Long,
    )

    class QueryToken internal constructor(
        internal val version: Long,
        internal val mutationVersion: Long,
    )

    private var writeVersion = 0L
    private var queryVersion = 0L
    private var mutationVersion = 0L
    private var pendingWrite: WriteToken? = null

    var confirmedState: FreeClip2AudioState? = null
        private set

    fun reset() {
        writeVersion++
        queryVersion++
        mutationVersion++
        pendingWrite = null
        confirmedState = null
    }

    /** Returns null when the same setting is already awaiting confirmation. */
    fun beginWrite(update: FreeClip2AudioState, nowMs: Long): WriteToken? {
        if (!update.hasExactlyOneField()) return null
        pendingWrite?.takeIf {
            it.update == update && nowMs - it.startedAtMs in 0 until DUPLICATE_WRITE_WINDOW_MS
        }?.let { return null }
        mutationVersion++
        queryVersion++
        return WriteToken(++writeVersion, update, nowMs).also { pendingWrite = it }
    }

    /** Returns false for a completion belonging to an older/replaced write. */
    fun completeWrite(token: WriteToken, success: Boolean): Boolean {
        if (pendingWrite?.version != token.version) return false
        if (!success) pendingWrite = null
        return true
    }

    fun beginQuery(): QueryToken = QueryToken(++queryVersion, mutationVersion)

    /**
     * Applies only a response from the newest query and current mutation generation. A verified
     * value for the pending field resolves the pending write, whether the device accepted or
     * rejected the requested value.
     */
    fun acceptQuery(token: QueryToken, update: FreeClip2AudioState): FreeClip2AudioState? {
        if (token.version != queryVersion || token.mutationVersion != mutationVersion) return null
        confirmedState = mergeFreeClip2AudioState(confirmedState, update)
        pendingWrite?.takeIf { update.observes(it.update) }?.let { pendingWrite = null }
        return confirmedState
    }

    internal fun pendingUpdate(): FreeClip2AudioState? = pendingWrite?.update
}

private fun FreeClip2AudioState.hasExactlyOneField(): Boolean =
    listOf(mode, scene, effect).count { it != null } == 1

private fun FreeClip2AudioState.observes(pending: FreeClip2AudioState): Boolean =
    (pending.mode != null && mode != null) ||
        (pending.scene != null && scene != null) ||
        (pending.effect != null && effect != null)
