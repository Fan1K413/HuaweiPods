package moe.chenxy.huaweipods.pods

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams

internal data class FreeBuds3StateSnapshot(
    val address: String,
    val route: HuaweiDeviceRoute,
    val battery: BatteryParams?,
    val batteryCapturedAtMs: Long?,
    val batteryWriterPid: Int?,
    val batteryWriterBuild: String?,
    val batteryWriterBootCount: Int?,
    val moduleAnc: HuaweiAncState?,
    val moduleAncCapturedAtMs: Long?,
    val moduleAncWriterPid: Int?,
    val moduleAncWriterBuild: String?,
    val moduleAncWriterBootCount: Int?,
    val disconnectedAtMs: Long?,
    val disconnectedBootCount: Int?,
)

internal data class FreeBuds3RestoredState(
    val battery: BatteryParams?,
    val batteryAgeMs: Long?,
    val moduleAnc: HuaweiAncState?,
    val moduleAncAgeMs: Long?,
) {
    val isEmpty: Boolean
        get() = battery == null && moduleAnc == null
}

internal fun restorableFreeBuds3State(
    snapshot: FreeBuds3StateSnapshot,
    address: String,
    route: HuaweiDeviceRoute,
    nowMs: Long,
    ttlMs: Long,
    currentPid: Int,
    currentBuild: String,
    currentBootCount: Int,
): FreeBuds3RestoredState? {
    require(ttlMs > 0L)
    if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS3 || snapshot.route != route) return null
    if (!snapshot.address.equals(address, ignoreCase = true)) return null

    fun ageIfRestorable(
        capturedAtMs: Long?,
        writerPid: Int?,
        writerBuild: String?,
        writerBootCount: Int?,
    ): Long? {
        val capturedAt = capturedAtMs ?: return null
        val pid = writerPid ?: return null
        if (pid <= 0 || pid == currentPid || writerBuild != currentBuild) return null
        if (currentBootCount < 0 || writerBootCount != currentBootCount) return null
        if (capturedAt > nowMs) return null
        if (
            snapshot.disconnectedBootCount == writerBootCount &&
            snapshot.disconnectedAtMs?.let { capturedAt <= it } == true
        ) {
            return null
        }
        return (nowMs - capturedAt).takeIf { it <= ttlMs }
    }

    val batteryAge = ageIfRestorable(
        snapshot.batteryCapturedAtMs,
        snapshot.batteryWriterPid,
        snapshot.batteryWriterBuild,
        snapshot.batteryWriterBootCount,
    )
    val ancAge = ageIfRestorable(
        snapshot.moduleAncCapturedAtMs,
        snapshot.moduleAncWriterPid,
        snapshot.moduleAncWriterBuild,
        snapshot.moduleAncWriterBootCount,
    )
    val restored = FreeBuds3RestoredState(
        battery = snapshot.battery?.deepCopy().takeIf { batteryAge != null },
        batteryAgeMs = batteryAge.takeIf { snapshot.battery != null },
        moduleAnc = snapshot.moduleAnc?.takeIf {
            ancAge != null && it.mode.isKnown()
        },
        moduleAncAgeMs = ancAge.takeIf { snapshot.moduleAnc?.mode?.isKnown() == true },
    )
    return restored.takeUnless(FreeBuds3RestoredState::isEmpty)
}

internal class FreeBuds3StateSnapshotStore(
    private val prefsName: String = PREFS_NAME,
) {
    @Synchronized
    fun saveRealBattery(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
        battery: BatteryParams,
        capturedAtMs: Long,
        writerPid: Int,
        writerBuild: String,
        writerBootCount: Int,
    ) {
        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS3) return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        identityEditor(prefs, address, route).apply {
            putLong(KEY_BATTERY_CAPTURED_AT, capturedAtMs)
            putInt(KEY_BATTERY_WRITER_PID, writerPid)
            putString(KEY_BATTERY_WRITER_BUILD, writerBuild)
            putInt(KEY_BATTERY_WRITER_BOOT_COUNT, writerBootCount)
            putPod(BATTERY_LEFT_PREFIX, battery.left)
            putPod(BATTERY_RIGHT_PREFIX, battery.right)
            putPod(BATTERY_CASE_PREFIX, battery.case)
        }.commit()
    }

    @Synchronized
    fun saveModuleAnc(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
        state: HuaweiAncState,
        capturedAtMs: Long,
        writerPid: Int,
        writerBuild: String,
        writerBootCount: Int,
    ) {
        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS3 || !state.mode.isKnown()) return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        identityEditor(prefs, address, route).apply {
            putLong(KEY_ANC_CAPTURED_AT, capturedAtMs)
            putInt(KEY_ANC_WRITER_PID, writerPid)
            putString(KEY_ANC_WRITER_BUILD, writerBuild)
            putInt(KEY_ANC_WRITER_BOOT_COUNT, writerBootCount)
            putInt(KEY_ANC_MODE, state.mode.broadcastStatus)
            if (state.subMode == null) {
                remove(KEY_ANC_SUBMODE)
            } else {
                putInt(KEY_ANC_SUBMODE, state.subMode)
            }
        }.commit()
    }

    @Synchronized
    fun markDisconnected(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
        disconnectedAtMs: Long,
        writerBootCount: Int,
    ) {
        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS3) return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val sameIdentity = prefs.getString(KEY_ADDRESS, null).equals(address, ignoreCase = true) &&
            prefs.getString(KEY_ROUTE, null) == route.name
        if (!sameIdentity) return
        prefs.edit()
            .putLong(KEY_DISCONNECTED_AT, disconnectedAtMs)
            .putInt(KEY_DISCONNECTED_BOOT_COUNT, writerBootCount)
            .commit()
    }

    @Synchronized
    fun restoreAfterProcessRestart(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
        nowMs: Long,
        ttlMs: Long,
        currentPid: Int,
        currentBuild: String,
        currentBootCount: Int,
    ): FreeBuds3RestoredState? {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val snapshot = readSnapshot(prefs) ?: return null
        if (snapshot.route != route || !snapshot.address.equals(address, ignoreCase = true)) {
            prefs.edit().clear().commit()
            return null
        }
        pruneUnrestorableComponents(
            prefs = prefs,
            snapshot = snapshot,
            nowMs = nowMs,
            ttlMs = ttlMs,
            currentBuild = currentBuild,
            currentBootCount = currentBootCount,
        )
        val prunedSnapshot = readSnapshot(prefs) ?: return null
        val restored = restorableFreeBuds3State(
            snapshot = prunedSnapshot,
            address = address,
            route = route,
            nowMs = nowMs,
            ttlMs = ttlMs,
            currentPid = currentPid,
            currentBuild = currentBuild,
            currentBootCount = currentBootCount,
        ) ?: return null
        claimRestoredState(
            prefs = prefs,
            restored = restored,
            currentPid = currentPid,
            currentBuild = currentBuild,
        )
        return restored
    }

    @Synchronized
    fun clearIfIdentityChanged(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
    ) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ADDRESS)) return
        val storedAddress = prefs.getString(KEY_ADDRESS, null)
        val storedRoute = prefs.getString(KEY_ROUTE, null)
        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS3 ||
            !storedAddress.equals(address, ignoreCase = true) ||
            storedRoute != route.name
        ) {
            prefs.edit().clear().commit()
        }
    }

    @Synchronized
    fun clearForAddress(context: Context, address: String) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ADDRESS, null).equals(address, ignoreCase = true)) {
            prefs.edit().clear().commit()
        }
    }

    private fun identityEditor(
        prefs: SharedPreferences,
        address: String,
        route: HuaweiDeviceRoute,
    ): SharedPreferences.Editor {
        val sameIdentity = prefs.getString(KEY_ADDRESS, null).equals(address, ignoreCase = true) &&
            prefs.getString(KEY_ROUTE, null) == route.name
        return prefs.edit().apply {
            if (!sameIdentity) clear()
            putString(KEY_ADDRESS, address.uppercase())
            putString(KEY_ROUTE, route.name)
        }
    }

    private fun readSnapshot(prefs: SharedPreferences): FreeBuds3StateSnapshot? {
        val address = prefs.getString(KEY_ADDRESS, null)?.takeIf(String::isNotBlank) ?: return null
        val route = prefs.getString(KEY_ROUTE, null)?.let { value ->
            runCatching { HuaweiDeviceRoute.valueOf(value) }.getOrNull()
        } ?: return null
        val batteryCapturedAt = prefs.longOrNull(KEY_BATTERY_CAPTURED_AT)
        val ancCapturedAt = prefs.longOrNull(KEY_ANC_CAPTURED_AT)
        return FreeBuds3StateSnapshot(
            address = address,
            route = route,
            battery = batteryCapturedAt?.let {
                BatteryParams(
                    left = prefs.readPod(BATTERY_LEFT_PREFIX),
                    right = prefs.readPod(BATTERY_RIGHT_PREFIX),
                    case = prefs.readPod(BATTERY_CASE_PREFIX),
                )
            },
            batteryCapturedAtMs = batteryCapturedAt,
            batteryWriterPid = prefs.intOrNull(KEY_BATTERY_WRITER_PID),
            batteryWriterBuild = prefs.getString(KEY_BATTERY_WRITER_BUILD, null),
            batteryWriterBootCount = prefs.intOrNull(KEY_BATTERY_WRITER_BOOT_COUNT),
            moduleAnc = ancCapturedAt?.let {
                HuaweiAncState(
                    mode = NoiseControlMode.fromBroadcastStatus(
                        prefs.getInt(KEY_ANC_MODE, NoiseControlMode.UNKNOWN.broadcastStatus),
                    ),
                    subMode = prefs.intOrNull(KEY_ANC_SUBMODE),
                )
            },
            moduleAncCapturedAtMs = ancCapturedAt,
            moduleAncWriterPid = prefs.intOrNull(KEY_ANC_WRITER_PID),
            moduleAncWriterBuild = prefs.getString(KEY_ANC_WRITER_BUILD, null),
            moduleAncWriterBootCount = prefs.intOrNull(KEY_ANC_WRITER_BOOT_COUNT),
            disconnectedAtMs = prefs.longOrNull(KEY_DISCONNECTED_AT),
            disconnectedBootCount = prefs.intOrNull(KEY_DISCONNECTED_BOOT_COUNT),
        )
    }

    private fun claimRestoredState(
        prefs: SharedPreferences,
        restored: FreeBuds3RestoredState,
        currentPid: Int,
        currentBuild: String,
    ) {
        prefs.edit().apply {
            if (restored.battery != null) {
                putInt(KEY_BATTERY_WRITER_PID, currentPid)
                putString(KEY_BATTERY_WRITER_BUILD, currentBuild)
            }
            if (restored.moduleAnc != null) {
                putInt(KEY_ANC_WRITER_PID, currentPid)
                putString(KEY_ANC_WRITER_BUILD, currentBuild)
            }
        }.commit()
    }

    private fun pruneUnrestorableComponents(
        prefs: SharedPreferences,
        snapshot: FreeBuds3StateSnapshot,
        nowMs: Long,
        ttlMs: Long,
        currentBuild: String,
        currentBootCount: Int,
    ) {
        fun invalid(
            capturedAtMs: Long?,
            writerBuild: String?,
            writerBootCount: Int?,
        ): Boolean = capturedAtMs != null && (
            capturedAtMs > nowMs ||
                nowMs - capturedAtMs > ttlMs ||
                writerBuild != currentBuild ||
                writerBootCount == null ||
                (currentBootCount >= 0 && writerBootCount != currentBootCount)
            )

        val batteryInvalid = invalid(
            snapshot.batteryCapturedAtMs,
            snapshot.batteryWriterBuild,
            snapshot.batteryWriterBootCount,
        )
        val ancInvalid = invalid(
            snapshot.moduleAncCapturedAtMs,
            snapshot.moduleAncWriterBuild,
            snapshot.moduleAncWriterBootCount,
        )
        if (!batteryInvalid && !ancInvalid) return
        prefs.edit().apply {
            if (batteryInvalid) removeBattery()
            if (ancInvalid) removeAnc()
        }.commit()
    }

    private fun SharedPreferences.Editor.putPod(prefix: String, pod: PodParams?) {
        putBoolean(prefix + KEY_PRESENT_SUFFIX, pod != null)
        if (pod == null) {
            remove(prefix + KEY_LEVEL_SUFFIX)
            remove(prefix + KEY_CHARGING_SUFFIX)
            remove(prefix + KEY_CONNECTED_SUFFIX)
            remove(prefix + KEY_RAW_STATUS_SUFFIX)
            return
        }
        putInt(prefix + KEY_LEVEL_SUFFIX, pod.battery)
        putBoolean(prefix + KEY_CHARGING_SUFFIX, pod.isCharging)
        putBoolean(prefix + KEY_CONNECTED_SUFFIX, pod.isConnected)
        putInt(prefix + KEY_RAW_STATUS_SUFFIX, pod.rawStatus)
    }

    private fun SharedPreferences.readPod(prefix: String): PodParams? {
        if (!getBoolean(prefix + KEY_PRESENT_SUFFIX, false)) return null
        return PodParams(
            battery = getInt(prefix + KEY_LEVEL_SUFFIX, 0),
            isCharging = getBoolean(prefix + KEY_CHARGING_SUFFIX, false),
            isConnected = getBoolean(prefix + KEY_CONNECTED_SUFFIX, false),
            rawStatus = getInt(prefix + KEY_RAW_STATUS_SUFFIX, 0),
        )
    }

    private fun SharedPreferences.longOrNull(key: String): Long? =
        getLong(key, 0L).takeIf { contains(key) }

    private fun SharedPreferences.intOrNull(key: String): Int? =
        getInt(key, 0).takeIf { contains(key) }

    private fun SharedPreferences.Editor.removeBattery() {
        remove(KEY_BATTERY_CAPTURED_AT)
        remove(KEY_BATTERY_WRITER_PID)
        remove(KEY_BATTERY_WRITER_BUILD)
        remove(KEY_BATTERY_WRITER_BOOT_COUNT)
        listOf(BATTERY_LEFT_PREFIX, BATTERY_RIGHT_PREFIX, BATTERY_CASE_PREFIX).forEach { prefix ->
            remove(prefix + KEY_PRESENT_SUFFIX)
            remove(prefix + KEY_LEVEL_SUFFIX)
            remove(prefix + KEY_CHARGING_SUFFIX)
            remove(prefix + KEY_CONNECTED_SUFFIX)
            remove(prefix + KEY_RAW_STATUS_SUFFIX)
        }
    }

    private fun SharedPreferences.Editor.removeAnc() {
        remove(KEY_ANC_CAPTURED_AT)
        remove(KEY_ANC_WRITER_PID)
        remove(KEY_ANC_WRITER_BUILD)
        remove(KEY_ANC_WRITER_BOOT_COUNT)
        remove(KEY_ANC_MODE)
        remove(KEY_ANC_SUBMODE)
    }

    private companion object {
        private const val PREFS_NAME = "huaweipods_freebuds3_last_state"
        private const val KEY_ADDRESS = "address"
        private const val KEY_ROUTE = "route"
        private const val KEY_BATTERY_CAPTURED_AT = "battery_captured_at"
        private const val KEY_BATTERY_WRITER_PID = "battery_writer_pid"
        private const val KEY_BATTERY_WRITER_BUILD = "battery_writer_build"
        private const val KEY_BATTERY_WRITER_BOOT_COUNT = "battery_writer_boot_count"
        private const val KEY_ANC_CAPTURED_AT = "anc_captured_at"
        private const val KEY_ANC_WRITER_PID = "anc_writer_pid"
        private const val KEY_ANC_WRITER_BUILD = "anc_writer_build"
        private const val KEY_ANC_WRITER_BOOT_COUNT = "anc_writer_boot_count"
        private const val KEY_DISCONNECTED_AT = "disconnected_at"
        private const val KEY_DISCONNECTED_BOOT_COUNT = "disconnected_boot_count"
        private const val KEY_ANC_MODE = "anc_mode"
        private const val KEY_ANC_SUBMODE = "anc_submode"
        private const val BATTERY_LEFT_PREFIX = "battery_left_"
        private const val BATTERY_RIGHT_PREFIX = "battery_right_"
        private const val BATTERY_CASE_PREFIX = "battery_case_"
        private const val KEY_PRESENT_SUFFIX = "present"
        private const val KEY_LEVEL_SUFFIX = "level"
        private const val KEY_CHARGING_SUFFIX = "charging"
        private const val KEY_CONNECTED_SUFFIX = "connected"
        private const val KEY_RAW_STATUS_SUFFIX = "raw_status"
    }
}

internal fun currentBootCount(context: Context): Int = runCatching {
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}.getOrDefault(-1)

private fun BatteryParams.deepCopy(): BatteryParams = BatteryParams(
    left = left?.copy(),
    right = right?.copy(),
    case = case?.copy(),
)
