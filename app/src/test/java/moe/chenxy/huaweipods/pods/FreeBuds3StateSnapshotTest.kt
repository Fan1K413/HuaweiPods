package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class FreeBuds3StateSnapshotTest {
    private val battery = BatteryParams(
        left = PodParams(82, isCharging = false, isConnected = true, rawStatus = 0),
        right = PodParams(79, isCharging = false, isConnected = true, rawStatus = 0),
        case = PodParams(60, isCharging = true, isConnected = true, rawStatus = 0),
    )

    @Test
    fun `restores fresh same-device state only after process restart`() {
        val snapshot = snapshot()

        val restored = restorableFreeBuds3State(
            snapshot = snapshot,
            address = "aa:bb:cc:dd:ee:ff",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 601_000L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 7,
        )

        assertEquals(1_000L, restored?.batteryAgeMs)
        assertEquals(1_000L, restored?.moduleAncAgeMs)
        assertEquals(82, restored?.battery?.left?.battery)
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, restored?.moduleAnc?.mode)
        assertNotSame(battery, restored?.battery)
        assertNotSame(battery.left, restored?.battery?.left)
    }

    @Test
    fun `does not restore during a reconnect in the same host process`() {
        assertNull(
            restorableFreeBuds3State(
                snapshot = snapshot(),
                address = "AA:BB:CC:DD:EE:FF",
                route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                nowMs = 601_000L,
                ttlMs = 600_000L,
                currentPid = 111,
                currentBuild = "build-a",
                currentBootCount = 7,
            ),
        )
    }

    @Test
    fun `isolates route address and build`() {
        val snapshot = snapshot()
        val common = { address: String, route: HuaweiDeviceRoute, build: String ->
            restorableFreeBuds3State(
                snapshot = snapshot,
                address = address,
                route = route,
                nowMs = 601_000L,
                ttlMs = 600_000L,
                currentPid = 222,
                currentBuild = build,
                currentBootCount = 7,
            )
        }

        assertNull(common("11:22:33:44:55:66", HuaweiDeviceRoute.HUAWEI_FREEBUDS3, "build-a"))
        assertNull(common("AA:BB:CC:DD:EE:FF", HuaweiDeviceRoute.HUAWEI_FREEBUDS5, "build-a"))
        assertNull(common("AA:BB:CC:DD:EE:FF", HuaweiDeviceRoute.HUAWEI_FREEBUDS3, "build-b"))
    }

    @Test
    fun `uses strict wall-clock ttl and rejects future snapshots`() {
        val atBoundary = restorableFreeBuds3State(
            snapshot = snapshot(),
            address = "AA:BB:CC:DD:EE:FF",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 1_200_000L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 7,
        )
        val expired = restorableFreeBuds3State(
            snapshot = snapshot(),
            address = "AA:BB:CC:DD:EE:FF",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 1_200_001L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 7,
        )
        val future = restorableFreeBuds3State(
            snapshot = snapshot(batteryCapturedAtMs = 1_300_000L, ancCapturedAtMs = 1_300_000L),
            address = "AA:BB:CC:DD:EE:FF",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 1_200_000L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 7,
        )

        assertEquals(600_000L, atBoundary?.batteryAgeMs)
        assertNull(expired)
        assertNull(future)
    }

    @Test
    fun `restores battery and module anc independently by writer process`() {
        val restored = restorableFreeBuds3State(
            snapshot = snapshot(batteryWriterPid = 222, ancWriterPid = 111),
            address = "AA:BB:CC:DD:EE:FF",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 601_000L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 7,
        )

        assertNull(restored?.battery)
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, restored?.moduleAnc?.mode)
    }

    @Test
    fun `isolates device boot and disconnect boundary`() {
        val wrongBoot = restorableFreeBuds3State(
            snapshot = snapshot(),
            address = "AA:BB:CC:DD:EE:FF",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 601_000L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 8,
        )
        val disconnected = restorableFreeBuds3State(
            snapshot = snapshot(disconnectedAtMs = 600_500L),
            address = "AA:BB:CC:DD:EE:FF",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 601_000L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 7,
        )

        assertNull(wrongBoot)
        assertNull(disconnected)
    }

    @Test
    fun `invalid component metadata does not discard the other component`() {
        val restored = restorableFreeBuds3State(
            snapshot = snapshot(ancWriterBuild = "old-build"),
            address = "AA:BB:CC:DD:EE:FF",
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            nowMs = 601_000L,
            ttlMs = 600_000L,
            currentPid = 222,
            currentBuild = "build-a",
            currentBootCount = 7,
        )

        assertEquals(82, restored?.battery?.left?.battery)
        assertNull(restored?.moduleAnc)
    }

    private fun snapshot(
        batteryCapturedAtMs: Long = 600_000L,
        ancCapturedAtMs: Long = 600_000L,
        batteryWriterPid: Int = 111,
        ancWriterPid: Int = 111,
        batteryWriterBuild: String = "build-a",
        ancWriterBuild: String = "build-a",
        disconnectedAtMs: Long? = null,
    ): FreeBuds3StateSnapshot = FreeBuds3StateSnapshot(
        address = "AA:BB:CC:DD:EE:FF",
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
        battery = battery,
        batteryCapturedAtMs = batteryCapturedAtMs,
        batteryWriterPid = batteryWriterPid,
        batteryWriterBuild = batteryWriterBuild,
        batteryWriterBootCount = 7,
        moduleAnc = HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION),
        moduleAncCapturedAtMs = ancCapturedAtMs,
        moduleAncWriterPid = ancWriterPid,
        moduleAncWriterBuild = ancWriterBuild,
        moduleAncWriterBootCount = 7,
        disconnectedAtMs = disconnectedAtMs,
        disconnectedBootCount = disconnectedAtMs?.let { 7 },
    )
}
