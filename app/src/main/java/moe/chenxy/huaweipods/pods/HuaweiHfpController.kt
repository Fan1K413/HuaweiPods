package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.hook.Log
import moe.chenxy.huaweipods.utils.miuiStrongToast.MiuiStrongToastUtil
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.normalizedEarbudAvailability

@SuppressLint("MissingPermission", "StaticFieldLeak")
object HuaweiHfpController {
    private const val TAG = "HuaweiPods-HuaweiHfp"
    private const val ANC_CONFIRM_DELAY_MS = 450L
    private const val ANC_REFRESH_MIN_INTERVAL_MS = 750L
    private const val BACKGROUND_BATTERY_REFRESH_INTERVAL_MS = 30_000L
    private const val GESTURE_CONFIRM_DELAY_MS = 300L
    private const val GESTURE_REFRESH_MIN_INTERVAL_MS = 750L
    private const val FREECLIP2_AUDIO_CONFIRM_DELAY_MS = 450L
    private const val FREECLIP2_AUDIO_REFRESH_MIN_INTERVAL_MS = 2_500L
    private const val FREEBUDS3_SNAPSHOT_TTL_MS = 10 * 60_000L
    private const val EXTRA_STATE_CACHED = "state_cached"

    private var context: Context? = null
    private var device: BluetoothDevice? = null
    private var sessionRoute = HuaweiDeviceRoute.UNSUPPORTED
    private var receiverRegistered = false
    @Volatile
    private var currentBattery: BatteryParams? = null
    @Volatile
    private var currentBatteryIsCached = false
    @Volatile
    private var currentAnc = HuaweiAncState(NoiseControlMode.UNKNOWN)
    @Volatile
    private var currentAncIsCached = false
    private var currentAncLevel = 0
    private var currentTransparencySubMode = 0xFF
    private var lastDispatchedAncLevel: Int? = null
    private var connectedBroadcastSent = false
    private var lastBatteryRequestAt = 0L
    private var lastAncRequestAt = 0L
    private var lastGestureStateRequestAt = 0L
    private var lastFreeClip2AudioStateRequestAt = 0L
    private var batteryRequestInFlight = false
    private var ancRequestInFlight = false
    private var sessionGeneration = 0L
    private val freeClip2AudioStateTracker = FreeClip2AudioStateTracker()
    private val batteryIslandTriggerPolicy = BatteryIslandTriggerPolicy()
    private val freeBuds3SnapshotStore = FreeBuds3StateSnapshotStore()
    private val sessionStateLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val freeClip2AudioConfirmationRunnable = Runnable {
        requestFreeClip2AudioState(force = true)
    }
    private var backgroundBatteryRefreshActive = false
    private val backgroundBatteryRefreshRunnable = object : Runnable {
        override fun run() {
            if (!backgroundBatteryRefreshActive) return
            if (device == null || !sessionRoute.supportsBackgroundBatteryRefresh) {
                stopBackgroundBatteryRefresh()
                return
            }
            requestPrivateBattery()
            mainHandler.postDelayed(this, BACKGROUND_BATTERY_REFRESH_INTERVAL_MS)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedIntent = intent ?: return
            if (receivedIntent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val changedDevice = receivedIntent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java,
                ) ?: return
                if (receivedIntent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        changedDevice.bondState,
                    ) == BluetoothDevice.BOND_NONE
                ) {
                    context?.let {
                        freeBuds3SnapshotStore.clearForAddress(it, changedDevice.address)
                    }
                    Log.i(TAG, "FreeBuds 3 cached state cleared after unbond device=${changedDevice.address}")
                }
                return
            }
            when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                HuaweiPodsAction.ACTION_PODS_UI_INIT,
                HuaweiPodsAction.ACTION_REFRESH_STATUS -> {
                    synchronized(sessionStateLock) {
                        sendConnectionState("connected")
                        sendConnected(force = true)
                        currentBattery?.let { sendBattery(it, cached = currentBatteryIsCached) }
                        if (receivedIntent.getBooleanExtra(
                                HuaweiPodsAction.EXTRA_RESTORE_NOTIFICATION,
                                false,
                            )
                        ) {
                            restoreCurrentNotification()
                        }
                    }
                    requestPrivateBattery()
                    if (sessionRoute.supportsAnc) {
                        val requestStarted = requestAncState()
                        synchronized(sessionStateLock) {
                            if (!requestStarted) {
                                sendAnc(currentAnc, cached = currentAncIsCached)
                            }
                            if (currentAnc.mode == NoiseControlMode.NOISE_CANCELLATION ||
                                sessionRoute.supportsAncDirectionDial
                            ) {
                                sendAncLevel(currentAncLevel)
                            }
                        }
                    }
                    if (sessionRoute == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                        requestFreeClip2AudioState()
                    }
                }
                HuaweiPodsAction.ACTION_ANC_SELECT -> {
                    if (!targetsCurrentSession(receivedIntent, requireAddress = true)) return
                    if (!receivedIntent.hasExtra("status")) {
                        Log.w(TAG, "Huawei ANC skipped: missing status")
                        return
                    }
                    val subMode = receivedIntent.getIntExtra("submode", -1).takeIf {
                        receivedIntent.hasExtra("submode") && it >= 0
                    }
                    setAncMode(receivedIntent.getIntExtra("status", NoiseControlMode.UNKNOWN.broadcastStatus), subMode)
                }
                HuaweiPodsAction.ACTION_CYCLE_ANC -> {
                    if (!targetsCurrentSession(receivedIntent, requireAddress = true)) return
                    val knownMode = currentAnc.mode.takeIf(NoiseControlMode::isKnown) ?: run {
                        Log.i(TAG, "Huawei ANC cycle deferred until current state is known")
                        if (!requestAncState(force = true)) {
                            setAncMode(NoiseControlMode.NOISE_CANCELLATION.broadcastStatus)
                        }
                        return
                    }
                    val nextMode = nextHuaweiAncMode(sessionRoute, knownMode)
                    Log.i(TAG, "Huawei ANC cycle current=$knownMode next=$nextMode")
                    setAncMode(nextMode.broadcastStatus)
                }
                HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET -> {
                    if (!targetsCurrentSession(receivedIntent, requireAddress = true)) return
                    setAncLevel(receivedIntent.getIntExtra("level", currentAncLevel))
                }
                HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND -> {
                    if (!targetsCurrentSession(receivedIntent, requireAddress = true)) return
                    if (BuildConfig.DEBUG) sendLegacyDebugHex(receivedIntent.getStringExtra("hex").orEmpty())
                }
                HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET -> {
                    if (!targetsCurrentSession(receivedIntent, requireAddress = true)) return
                    setGesture(receivedIntent)
                }
                HuaweiPodsAction.ACTION_HUAWEI_GESTURE_REFRESH -> {
                    if (!targetsCurrentSession(receivedIntent, requireAddress = false)) return
                    requestGestureState(
                        requestedAddress = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_ADDRESS),
                        force = receivedIntent.getBooleanExtra("force", false),
                    )
                }
                HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_SET -> {
                    if (!targetsCurrentFreeClip2Session(receivedIntent)) return
                    setFreeClip2Audio(receivedIntent)
                }
                HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_REFRESH -> {
                    if (!targetsCurrentFreeClip2Session(receivedIntent)) return
                    requestFreeClip2AudioState(
                        force = receivedIntent.getBooleanExtra("force", false),
                    )
                }
            }
        }
    }

    fun connectPod(context: Context, device: BluetoothDevice) {
        val route = device.huaweiDeviceRoute()
        if (!route.isSupported) {
            Log.w(TAG, "Huawei session skipped: unsupported device=${device.address}")
            return
        }
        synchronized(sessionStateLock) {
            ensureSession(context, device, route)
            val restored = restoreFreeBuds3StateAfterProcessRestart(context, device, route)
            sendConnectionState("connecting")
            sendConnected()
            restored?.battery?.let { sendBattery(it, cached = true) }
            restored?.moduleAnc?.let { sendAnc(it, cached = true) }
        }
        requestAncState(force = true)
        requestPrivateBattery()
        if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            requestFreeClip2AudioState(force = true)
        }
    }

    fun disconnectedPod(context: Context, device: BluetoothDevice) {
        synchronized(sessionStateLock) {
            if (this.device?.address != device.address) return
            if (sessionRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
                freeBuds3SnapshotStore.markDisconnected(
                    context = context,
                    address = device.address,
                    route = sessionRoute,
                    disconnectedAtMs = System.currentTimeMillis(),
                    writerBootCount = currentBootCount(context),
                )
            }
            stopBackgroundBatteryRefresh()
            MiuiStrongToastUtil.cancelPodsNotificationByMiuiBt(context, device)
            sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_DISCONNECTED) {
                putExtra("address", device.address)
            }
            sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_DISCONNECTED) {
                putExtra("address", device.address)
            }
            currentBattery = null
            currentBatteryIsCached = false
            currentAnc = HuaweiAncState(NoiseControlMode.UNKNOWN)
            currentAncIsCached = false
            currentAncLevel = 0
            currentTransparencySubMode = 0xFF
            lastDispatchedAncLevel = null
            connectedBroadcastSent = false
            lastBatteryRequestAt = 0L
            lastAncRequestAt = 0L
            lastGestureStateRequestAt = 0L
            lastFreeClip2AudioStateRequestAt = 0L
            batteryRequestInFlight = false
            ancRequestInFlight = false
            sessionGeneration++
            synchronized(sessionStateLock) { freeClip2AudioStateTracker.reset() }
            mainHandler.removeCallbacks(freeClip2AudioConfirmationRunnable)
            this.device = null
            this.context = null
            sessionRoute = HuaweiDeviceRoute.UNSUPPORTED
            HuaweiL2capAncController.disconnect(device)
            Log.d(TAG, "Huawei HFP disconnected device=${device.address}")
        }
    }

    fun handleAtCommand(
        context: Context,
        device: BluetoothDevice,
        text: String
    ): BatteryParams? {
        val result = HuaweiBatteryParser.parse(text) ?: return null
        val route = device.huaweiDeviceRoute()
        if (!route.isSupported) {
            Log.w(TAG, "Huawei battery ignored: unsupported device=${device.address}")
            return null
        }
        return synchronized(sessionStateLock) {
            ensureSession(context, device, route)
            val battery = result.battery.normalizedEarbudAvailability()
            currentBattery = battery
            currentBatteryIsCached = false
            if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
                freeBuds3SnapshotStore.saveRealBattery(
                    context = context,
                    address = device.address,
                    route = route,
                    battery = battery,
                    capturedAtMs = System.currentTimeMillis(),
                    writerPid = Process.myPid(),
                    writerBuild = BuildConfig.MODULE_BUILD_ID,
                    writerBootCount = currentBootCount(context),
                )
            }
            sendConnectionState("connected")
            sendConnected()
            sendBattery(battery)
            MiuiStrongToastUtil.showPodsNotificationByMiuiBt(context, battery, device)
            maybeShowBatteryIsland(context, battery, device)
            Log.i(TAG, "Huawei battery parsed device=${device.address} values=${result.values}")
            battery
        }
    }

    fun setAncMode(status: Int, subMode: Int? = null) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei ANC skipped: device null status=$status")
            return
        }
        if (!sessionRoute.isSupported) {
            Log.w(TAG, "Huawei ANC skipped: unsupported device=${currentDevice.address}")
            return
        }
        if (!sessionRoute.supportsAnc) {
            Log.w(TAG, "Huawei ANC skipped: feature unavailable device=${currentDevice.address}")
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei ANC skipped: context null status=$status device=${currentDevice.address}")
            return
        }
        val targetMode = NoiseControlMode.fromBroadcastStatus(status)
        if (!targetMode.isKnown()) {
            Log.w(TAG, "Huawei ANC skipped: unknown status=$status device=${currentDevice.address}")
            sendAnc(currentAnc)
            return
        }
        if (targetMode == NoiseControlMode.TRANSPARENCY && !sessionRoute.supportsTransparency) {
            Log.w(TAG, "Huawei transparency skipped: feature unavailable device=${currentDevice.address}")
            sendAnc(currentAnc)
            return
        }
        if (
            targetMode == NoiseControlMode.NOISE_CANCELLATION &&
            sessionRoute.supportsDiscreteAncLevels &&
            subMode != null &&
            !sessionRoute.supportsAncSubMode(subMode)
        ) {
            Log.w(TAG, "Huawei ANC submode skipped: unsupported subMode=$subMode route=$sessionRoute")
            sendAnc(currentAnc)
            return
        }
        val rememberedSubMode = when (targetMode) {
            NoiseControlMode.NOISE_CANCELLATION ->
                subMode ?: currentAncLevel.takeIf { sessionRoute.supportsDiscreteAncLevels }
            NoiseControlMode.TRANSPARENCY -> subMode ?: currentTransparencySubMode
            else -> null
        }
        val commandSubMode = normalizeHuaweiAncSubMode(
            route = sessionRoute,
            mode = targetMode,
            requestedSubMode = rememberedSubMode,
            previousState = currentAnc,
        )
        val previousState = currentAnc
        val requestedAddress = currentDevice.address
        val requestedRoute = sessionRoute
        val requestedGeneration = sessionGeneration
        val targetState = HuaweiAncState(targetMode, commandSubMode)
        Log.i(
            TAG,
            "Huawei ANC dispatch mode=$targetMode subMode=$commandSubMode device=${currentDevice.address}",
        )
        HuaweiL2capAncController.setAncMode(
            currentContext,
            currentDevice,
            requestedRoute,
            targetMode,
            commandSubMode,
        ) { success ->
            synchronized(sessionStateLock) {
                if (!isCurrentSession(requestedGeneration, requestedAddress, requestedRoute)) return@setAncMode
                val supportsReadback = requestedRoute.supportsAncStateReadback
                if (!success) {
                    Log.w(TAG, "Huawei ANC write failed device=$requestedAddress; requesting confirmed state")
                    if (supportsReadback) {
                        scheduleAncConfirmation(requestedGeneration, requestedAddress, requestedRoute, previousState)
                    } else {
                        sendAnc(previousState)
                    }
                    return@setAncMode
                }
                if (supportsReadback) {
                    scheduleAncConfirmation(requestedGeneration, requestedAddress, requestedRoute, targetState)
                } else {
                    currentAnc = targetState
                    currentAncIsCached = false
                    rememberAncSubMode(targetState)
                    if (requestedRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
                        freeBuds3SnapshotStore.saveModuleAnc(
                            context = currentContext,
                            address = requestedAddress,
                            route = requestedRoute,
                            state = targetState,
                            capturedAtMs = System.currentTimeMillis(),
                            writerPid = Process.myPid(),
                            writerBuild = BuildConfig.MODULE_BUILD_ID,
                            writerBootCount = currentBootCount(currentContext),
                        )
                    }
                    sendAnc(targetState)
                }
            }
        }
    }

    private fun targetsCurrentSession(intent: Intent, requireAddress: Boolean): Boolean {
        val activeDevice = device ?: return false
        val requestedAddress = intent.getStringExtra("address")?.takeIf(String::isNotBlank)
        if (requireAddress && requestedAddress == null) {
            Log.w(TAG, "Huawei command ignored: missing target address action=${intent.action}")
            return false
        }
        if (requestedAddress != null && !requestedAddress.equals(activeDevice.address, ignoreCase = true)) {
            Log.w(
                TAG,
                "Huawei command ignored: target mismatch action=${intent.action} " +
                    "requested=$requestedAddress current=${activeDevice.address}",
            )
            return false
        }
        val requestedRoute = decodeHuaweiDeviceRouteFromBroadcast(
            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
        )
        if (requestedRoute == null) {
            Log.w(TAG, "Huawei command ignored: missing or invalid route action=${intent.action}")
            return false
        }
        if (!matchesHuaweiSessionTarget(
                activeAddress = activeDevice.address,
                activeRoute = sessionRoute,
                requestedAddress = requestedAddress,
                requestedRoute = requestedRoute,
                requireAddress = requireAddress,
            )
        ) {
            Log.w(
                TAG,
                "Huawei command ignored: route mismatch action=${intent.action} " +
                    "requested=$requestedRoute current=$sessionRoute",
            )
            return false
        }
        return true
    }

    private fun targetsCurrentFreeClip2Session(intent: Intent): Boolean {
        val activeDevice = device ?: return false
        val requestedAddress = intent.getStringExtra("address")
        val requestedRoute = decodeHuaweiDeviceRouteFromBroadcast(
            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
        )
        if (!matchesFreeClip2AudioSessionTarget(
                activeAddress = activeDevice.address,
                activeRoute = sessionRoute,
                requestedAddress = requestedAddress,
                requestedRoute = requestedRoute,
            )
        ) {
            Log.w(
                TAG,
                "FreeClip 2 audio command ignored: target mismatch action=${intent.action} " +
                    "requestedAddress=$requestedAddress requestedRoute=$requestedRoute " +
                    "currentAddress=${activeDevice.address} currentRoute=$sessionRoute",
            )
            return false
        }
        return true
    }

    private fun setFreeClip2Audio(intent: Intent) {
        val currentContext = context ?: return
        val currentDevice = device ?: return
        val requestedAddress = currentDevice.address
        val requestedGeneration = sessionGeneration
        val kind = intent.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_KIND)
        val value = intent.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_VALUE)
        val update = when (kind) {
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE -> {
                val mode = FreeClip2SpatialAudioMode.fromExtraValue(value) ?: run {
                    Log.w(TAG, "FreeClip 2 audio skipped: invalid spatial mode=$value")
                    return
                }
                FreeClip2AudioState(mode = mode)
            }
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE -> {
                val scene = FreeClip2SpatialScene.fromExtraValue(value) ?: run {
                    Log.w(TAG, "FreeClip 2 audio skipped: invalid spatial scene=$value")
                    return
                }
                FreeClip2AudioState(scene = scene)
            }
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT -> {
                val effect = FreeClip2SoundEffect.fromExtraValue(value) ?: run {
                    Log.w(TAG, "FreeClip 2 audio skipped: invalid sound effect=$value")
                    return
                }
                FreeClip2AudioState(effect = effect)
            }
            else -> {
                Log.w(TAG, "FreeClip 2 audio skipped: invalid kind=$kind")
                return
            }
        }
        val writeToken = synchronized(sessionStateLock) {
            freeClip2AudioStateTracker.beginWrite(update, SystemClock.elapsedRealtime())
        } ?: run {
            Log.d(TAG, "FreeClip 2 duplicate audio write ignored kind=$kind value=$value device=$requestedAddress")
            return
        }
        val onComplete: (Boolean) -> Unit = completion@{ success ->
            if (!isCurrentSession(
                    requestedGeneration,
                    requestedAddress,
                    HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                )
            ) {
                return@completion
            }
            val isLatestWrite = synchronized(sessionStateLock) {
                freeClip2AudioStateTracker.completeWrite(writeToken, success)
            }
            if (!isLatestWrite) {
                Log.d(TAG, "FreeClip 2 stale audio write completion ignored kind=$kind value=$value")
                return@completion
            }
            if (!success) {
                Log.w(TAG, "FreeClip 2 audio write failed kind=$kind value=$value device=$requestedAddress")
            }
            mainHandler.removeCallbacks(freeClip2AudioConfirmationRunnable)
            mainHandler.postDelayed(
                freeClip2AudioConfirmationRunnable,
                FREECLIP2_AUDIO_CONFIRM_DELAY_MS,
            )
        }
        when (kind) {
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE ->
                HuaweiFreeClip2Controller.setSpatialAudioMode(
                    currentContext,
                    currentDevice,
                    requireNotNull(update.mode),
                    onComplete,
                )
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE ->
                HuaweiFreeClip2Controller.setSpatialScene(
                    currentContext,
                    currentDevice,
                    requireNotNull(update.scene),
                    onComplete,
                )
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT ->
                HuaweiFreeClip2Controller.setSoundEffect(
                    currentContext,
                    currentDevice,
                    requireNotNull(update.effect),
                    onComplete,
                )
            else -> Unit
        }
    }

    fun setAncLevel(level: Int) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei ANC level skipped: device null level=$level")
            return
        }
        if (!sessionRoute.isSupported) {
            Log.w(TAG, "Huawei ANC level skipped: unsupported device=${currentDevice.address}")
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei ANC level skipped: context null level=$level device=${currentDevice.address}")
            return
        }
        val activeMode = currentAnc.mode
        if (activeMode == NoiseControlMode.TRANSPARENCY && sessionRoute.supportsTransparency) {
            val validLevel = normalizeHuaweiAncSubMode(
                route = sessionRoute,
                mode = activeMode,
                requestedSubMode = level,
                previousState = currentAnc,
            ) ?: run {
                Log.w(TAG, "Huawei transparency submode skipped: invalid level=$level")
                return
            }
            currentTransparencySubMode = validLevel
            setAncMode(activeMode.broadcastStatus, validLevel)
            return
        }
        if (sessionRoute.supportsDiscreteAncLevels) {
            val validLevel = level.takeIf(sessionRoute::supportsAncSubMode) ?: run {
                Log.w(TAG, "Huawei ANC submode skipped: invalid level=$level")
                return
            }
            currentAncLevel = validLevel
            setAncMode(NoiseControlMode.NOISE_CANCELLATION.broadcastStatus, validLevel)
            return
        }
        if (!sessionRoute.supportsAncDirectionDial) {
            Log.w(TAG, "Huawei ANC level skipped: feature unavailable device=${currentDevice.address}")
            return
        }
        val safeLevel = level.coerceIn(0, 8)
        currentAncLevel = safeLevel
        if (lastDispatchedAncLevel == safeLevel) {
            Log.i(TAG, "Huawei ANC level duplicate skipped level=$safeLevel device=${currentDevice.address}")
            sendAncLevel(safeLevel)
            return
        }
        lastDispatchedAncLevel = safeLevel
        Log.i(TAG, "Huawei ANC level dispatch level=$safeLevel device=${currentDevice.address}")
        HuaweiL2capAncController.setAncLevel(
            currentContext,
            currentDevice,
            sessionRoute,
            safeLevel,
        )
        sendAncLevel(safeLevel)
    }

    fun sendLegacyDebugHex(hex: String) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei legacy debug skipped: device null")
            return
        }
        if (!sessionRoute.isSupported) {
            Log.w(TAG, "Huawei legacy debug skipped: unsupported device=${currentDevice.address}")
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei legacy debug skipped: context null device=${currentDevice.address}")
            return
        }
        val packet = parseHex(hex) ?: run {
            Log.w(TAG, "Huawei legacy debug invalid HEX: $hex")
            return
        }
        Log.i(TAG, "Huawei legacy debug send bytes=${packet.size} device=${currentDevice.address}")
        HuaweiL2capAncController.sendRawPacket(
            currentContext,
            currentDevice,
            sessionRoute,
            packet,
            "debug",
        )
    }

    fun setGesture(intent: Intent) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei gesture skipped: device null")
            return
        }
        if (!sessionRoute.isSupported) {
            Log.w(TAG, "Huawei gesture skipped: unsupported device=${currentDevice.address}")
            return
        }
        if (!sessionRoute.supportsGestureConfiguration) {
            Log.w(TAG, "Huawei gesture skipped: unverified model device=${currentDevice.address}")
            return
        }
        val requestedAddress = intent.getStringExtra(HuaweiGestureController.EXTRA_ADDRESS)
        if (!requestedAddress.isNullOrBlank() && !requestedAddress.equals(currentDevice.address, ignoreCase = true)) {
            Log.w(
                TAG,
                "Huawei gesture skipped: target mismatch requested=$requestedAddress current=${currentDevice.address}",
            )
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei gesture skipped: context null device=${currentDevice.address}")
            return
        }
        val side = HuaweiGestureSide.fromExtra(intent.getStringExtra(HuaweiGestureController.EXTRA_SIDE))
            ?: HuaweiGestureSide.fromProtocolValue(intent.getIntExtra(HuaweiGestureController.EXTRA_SIDE, -1))
            ?: run {
                Log.w(TAG, "Huawei gesture skipped: invalid side device=${currentDevice.address}")
                return
            }
        val kindExtra = intent.getStringExtra(HuaweiGestureController.EXTRA_GESTURE_KIND)
        val kind = if (kindExtra.isNullOrBlank()) {
            HuaweiGestureKind.DOUBLE_TAP
        } else {
            HuaweiGestureKind.fromExtra(kindExtra) ?: run {
                Log.w(TAG, "Huawei gesture skipped: invalid kind=$kindExtra device=${currentDevice.address}")
                return
            }
        }
        val onComplete: (Boolean) -> Unit = { success ->
            if (!success) {
                Log.w(
                    TAG,
                    "Huawei gesture write failed kind=${kind.extraValue} side=${side.extraValue} device=${currentDevice.address}",
                )
            }
            if (sessionRoute == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                mainHandler.postDelayed(
                    { requestGestureState(currentDevice.address, force = true) },
                    GESTURE_CONFIRM_DELAY_MS,
                )
            }
        }
        if (kind == HuaweiGestureKind.SWIPE) {
            val action = HuaweiSwipeAction.fromExtra(
                intent.getStringExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION),
            ) ?: HuaweiSwipeAction.fromProtocolValue(
                sessionRoute,
                intent.getIntExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION, -1),
            ) ?: run {
                Log.w(TAG, "Huawei swipe skipped: invalid action device=${currentDevice.address}")
                return
            }
            if (!HuaweiGestureController.supportsSwipeAction(sessionRoute, action)) {
                Log.w(TAG, "Huawei swipe skipped: unsupported action=$action route=$sessionRoute")
                return
            }
            Log.i(
                TAG,
                "Huawei gesture dispatch kind=${kind.extraValue} side=${side.extraValue} action=${action.extraValue} device=${currentDevice.address}",
            )
            HuaweiGestureController.setSwipe(
                currentContext,
                currentDevice,
                sessionRoute,
                side,
                action,
                onComplete,
            )
            return
        }
        val action = HuaweiTapAction.fromExtra(intent.getStringExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION))
            ?: HuaweiTapAction.fromProtocolValue(
                sessionRoute,
                kind,
                intent.getIntExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION, -1),
            )
            ?: run {
                Log.w(TAG, "Huawei gesture skipped: invalid action device=${currentDevice.address}")
                return
            }
        if (!HuaweiGestureController.supportsTapAction(sessionRoute, kind, action)) {
            Log.w(TAG, "Huawei gesture skipped: unsupported kind=$kind action=$action route=$sessionRoute")
            return
        }
        Log.i(
            TAG,
            "Huawei gesture dispatch kind=${kind.extraValue} side=${side.extraValue} action=${action.extraValue} device=${currentDevice.address}",
        )
        HuaweiGestureController.setTap(
            currentContext,
            currentDevice,
            sessionRoute,
            kind,
            side,
            action,
            onComplete,
        )
    }

    private fun ensureSession(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
    ) {
        val previousDevice = this.device
        val deviceChanged = previousDevice != null &&
            !previousDevice.address.equals(device.address, ignoreCase = true)
        val routeChanged = sessionRoute.isSupported && sessionRoute != route
        val newSession = previousDevice == null || deviceChanged || routeChanged
        if (deviceChanged || routeChanged) {
            stopBackgroundBatteryRefresh()
            HuaweiL2capAncController.disconnect(previousDevice)
        }
        if (newSession) {
            freeBuds3SnapshotStore.clearIfIdentityChanged(context, device.address, route)
            currentBattery = null
            currentBatteryIsCached = false
            currentAnc = HuaweiAncState(NoiseControlMode.UNKNOWN)
            currentAncIsCached = false
            currentAncLevel = if (route.supportsDiscreteAncLevels) {
                route.defaultAncSubMode ?: 0
            } else {
                0
            }
            currentTransparencySubMode = defaultTransparencySubMode(route)
            lastDispatchedAncLevel = null
            connectedBroadcastSent = false
            lastBatteryRequestAt = 0L
            lastAncRequestAt = 0L
            lastGestureStateRequestAt = 0L
            lastFreeClip2AudioStateRequestAt = 0L
            batteryRequestInFlight = false
            ancRequestInFlight = false
            synchronized(sessionStateLock) { freeClip2AudioStateTracker.reset() }
            mainHandler.removeCallbacks(freeClip2AudioConfirmationRunnable)
            batteryIslandTriggerPolicy.onNewSession()
            sessionGeneration++
            Log.i(
                TAG,
                "Huawei session switched from=${previousDevice?.address} to=${device.address} route=$route",
            )
        }
        this.context = context.applicationContext ?: context
        this.device = device
        sessionRoute = route
        registerReceiver()
        startBackgroundBatteryRefresh()
    }

    private fun restoreFreeBuds3StateAfterProcessRestart(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
    ): FreeBuds3RestoredState? {
        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS3) return null
        val restored = freeBuds3SnapshotStore.restoreAfterProcessRestart(
            context = context,
            address = device.address,
            route = route,
            nowMs = System.currentTimeMillis(),
            ttlMs = FREEBUDS3_SNAPSHOT_TTL_MS,
            currentPid = Process.myPid(),
            currentBuild = BuildConfig.MODULE_BUILD_ID,
            currentBootCount = currentBootCount(context),
        ) ?: return null

        val battery = restored.battery?.takeIf { currentBattery == null }
        val moduleAnc = restored.moduleAnc?.takeIf { !currentAnc.mode.isKnown() }
        battery?.let {
            currentBattery = it
            currentBatteryIsCached = true
        }
        moduleAnc?.let {
            currentAnc = it
            currentAncIsCached = true
            rememberAncSubMode(it)
        }
        if (battery == null && moduleAnc == null) return null
        Log.i(
            TAG,
            "FreeBuds 3 cached state restored device=${device.address} " +
                "batteryAgeMs=${restored.batteryAgeMs} ancAgeMs=${restored.moduleAncAgeMs}",
        )
        return FreeBuds3RestoredState(
            battery = battery,
            batteryAgeMs = restored.batteryAgeMs.takeIf { battery != null },
            moduleAnc = moduleAnc,
            moduleAncAgeMs = restored.moduleAncAgeMs.takeIf { moduleAnc != null },
        )
    }

    private fun requestPrivateBattery() {
        val currentContext = context ?: return
        val currentDevice = device ?: return
        if (!sessionRoute.supportsRfcommBattery) return
        if (batteryRequestInFlight) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBatteryRequestAt < 10_000L) return
        lastBatteryRequestAt = now
        val requestedAddress = currentDevice.address
        val requestedRoute = sessionRoute
        val requestedGeneration = sessionGeneration
        batteryRequestInFlight = true
        HuaweiL2capAncController.requestBattery(
            context = currentContext,
            device = currentDevice,
            route = requestedRoute,
            onBattery = { battery ->
                if (isCurrentSession(requestedGeneration, requestedAddress, requestedRoute)) {
                    device?.let { activeDevice ->
                        val normalizedBattery = normalizeHuaweiPrivateBattery(requestedRoute, battery)
                        currentBattery = normalizedBattery
                        currentBatteryIsCached = false
                        sendConnectionState("connected")
                        sendConnected()
                        sendBattery(normalizedBattery)
                        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(
                            currentContext,
                            normalizedBattery,
                            activeDevice,
                        )
                        maybeShowBatteryIsland(currentContext, normalizedBattery, activeDevice)
                        Log.i(TAG, "Huawei RFCOMM battery updated device=$requestedAddress")
                    }
                }
            },
            onComplete = { success ->
                if (isCurrentSession(requestedGeneration, requestedAddress, requestedRoute)) {
                    batteryRequestInFlight = false
                    if (!success) {
                        Log.w(TAG, "Huawei RFCOMM battery refresh failed device=$requestedAddress")
                    }
                }
            }
        )
    }

    private fun startBackgroundBatteryRefresh() {
        if (!sessionRoute.supportsBackgroundBatteryRefresh || backgroundBatteryRefreshActive) return
        backgroundBatteryRefreshActive = true
        mainHandler.removeCallbacks(backgroundBatteryRefreshRunnable)
        mainHandler.postDelayed(
            backgroundBatteryRefreshRunnable,
            BACKGROUND_BATTERY_REFRESH_INTERVAL_MS,
        )
        Log.i(TAG, "Huawei background battery refresh started interval=$BACKGROUND_BATTERY_REFRESH_INTERVAL_MS")
    }

    private fun stopBackgroundBatteryRefresh() {
        backgroundBatteryRefreshActive = false
        batteryRequestInFlight = false
        mainHandler.removeCallbacks(backgroundBatteryRefreshRunnable)
    }

    private fun maybeShowBatteryIsland(
        context: Context,
        battery: BatteryParams,
        device: BluetoothDevice,
    ) {
        if (ConfigManager.islandMode() != ConfigManager.ISLAND_MODE_MODULE) return
        val hasConnectedEarBattery = battery.left?.isConnected == true || battery.right?.isConnected == true
        if (!batteryIslandTriggerPolicy.shouldTrigger(
                address = device.address,
                hasConnectedEarBattery = hasConnectedEarBattery,
                now = SystemClock.elapsedRealtime(),
            )
        ) return
        runCatching {
            MiuiStrongToastUtil.showPodsBatteryToastByMiuiBt(context, battery, device)
        }.onSuccess {
            Log.i(TAG, "Huawei battery island requested device=${device.address}")
        }.onFailure {
            Log.e(TAG, "Huawei battery island request failed safely device=${device.address}", it)
        }
    }

    private fun requestAncState(
        force: Boolean = false,
        fallbackState: HuaweiAncState? = null,
    ): Boolean {
        val currentContext = context ?: return false
        val currentDevice = device ?: return false
        val requestedRoute = sessionRoute
        if (HuaweiAncPackets.currentStateQuery(requestedRoute) == null) return false
        if (ancRequestInFlight) return true
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAncRequestAt < ANC_REFRESH_MIN_INTERVAL_MS) return false
        lastAncRequestAt = now
        ancRequestInFlight = true
        val requestedAddress = currentDevice.address
        val requestedGeneration = sessionGeneration
        HuaweiL2capAncController.requestAncState(
            currentContext,
            currentDevice,
            requestedRoute,
        ) { state ->
            if (!isCurrentSession(requestedGeneration, requestedAddress, requestedRoute)) return@requestAncState
            ancRequestInFlight = false
            if (state == null) {
                Log.w(TAG, "Huawei ANC state query returned no verified state device=$requestedAddress")
                fallbackState?.let {
                    currentAnc = it
                    currentAncIsCached = false
                    rememberAncSubMode(it)
                    sendAnc(it)
                    if (it.mode == NoiseControlMode.NOISE_CANCELLATION) {
                        sendAncLevel(currentAncLevel)
                    }
                }
                return@requestAncState
            }
            currentAnc = state
            currentAncIsCached = false
            rememberAncSubMode(state)
            sendAnc(state)
            if (state.mode == NoiseControlMode.NOISE_CANCELLATION) {
                sendAncLevel(currentAncLevel)
            }
            Log.i(TAG, "Huawei ANC state confirmed state=$state device=$requestedAddress")
        }
        return true
    }

    private fun requestGestureState(
        requestedAddress: String? = null,
        force: Boolean = false,
    ) {
        val currentContext = context ?: return
        val currentDevice = device ?: return
        val requestedRoute = sessionRoute
        if (requestedRoute != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        if (!requestedAddress.isNullOrBlank() &&
            !requestedAddress.equals(currentDevice.address, ignoreCase = true)
        ) {
            Log.w(
                TAG,
                "Huawei gesture state skipped: target mismatch requested=$requestedAddress current=${currentDevice.address}",
            )
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastGestureStateRequestAt < GESTURE_REFRESH_MIN_INTERVAL_MS) return
        lastGestureStateRequestAt = now
        val activeAddress = currentDevice.address
        val activeGeneration = sessionGeneration
        HuaweiGestureController.requestGestureState(
            context = currentContext,
            device = currentDevice,
            route = requestedRoute,
        ) { state ->
            if (!isCurrentSession(activeGeneration, activeAddress, requestedRoute)) {
                return@requestGestureState
            }
            sendGestureState(state)
            Log.i(
                TAG,
                "Huawei gesture state confirmed device=$activeAddress double=${state.doubleTap} triple=${state.tripleTap} swipe=${state.swipe}",
            )
        }
    }

    private fun restoreCurrentNotification() {
        val currentContext = context ?: return
        val currentDevice = device ?: return
        val battery = currentBattery ?: return
        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(
            currentContext,
            battery,
            currentDevice,
        )
    }

    private fun requestFreeClip2AudioState(force: Boolean = false) {
        val currentContext = context ?: return
        val currentDevice = device ?: return
        val requestedRoute = sessionRoute
        if (requestedRoute != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFreeClip2AudioStateRequestAt < FREECLIP2_AUDIO_REFRESH_MIN_INTERVAL_MS) {
            return
        }
        lastFreeClip2AudioStateRequestAt = now
        val requestedAddress = currentDevice.address
        val requestedGeneration = sessionGeneration
        val queryToken = synchronized(sessionStateLock) {
            freeClip2AudioStateTracker.beginQuery()
        }
        val acceptState: (FreeClip2AudioState?) -> Unit = accept@{ update ->
            if (!isCurrentSession(requestedGeneration, requestedAddress, requestedRoute)) {
                return@accept
            }
            if (update == null) {
                Log.w(TAG, "FreeClip 2 audio state query returned no verified state device=$requestedAddress")
                return@accept
            }
            val confirmed = synchronized(sessionStateLock) {
                freeClip2AudioStateTracker.acceptQuery(queryToken, update)
            } ?: run {
                Log.d(TAG, "FreeClip 2 stale audio query response ignored update=$update device=$requestedAddress")
                return@accept
            }
            sendFreeClip2AudioState(confirmed)
            Log.i(TAG, "FreeClip 2 audio state confirmed update=$update device=$requestedAddress")
        }
        HuaweiFreeClip2Controller.requestSpatialAudioState(
            currentContext,
            currentDevice,
            acceptState,
        )
        HuaweiFreeClip2Controller.requestSoundEffectState(
            currentContext,
            currentDevice,
            acceptState,
        )
    }

    private fun scheduleAncConfirmation(
        generation: Long,
        address: String,
        route: HuaweiDeviceRoute,
        fallbackState: HuaweiAncState?,
    ) {
        mainHandler.postDelayed({
            if (!isCurrentSession(generation, address, route)) return@postDelayed
            requestAncState(force = true, fallbackState = fallbackState)
        }, ANC_CONFIRM_DELAY_MS)
    }

    private fun isCurrentSession(
        generation: Long,
        address: String,
        route: HuaweiDeviceRoute,
    ): Boolean {
        val activeDevice = device ?: return false
        return sessionGeneration == generation &&
            sessionRoute == route &&
            activeDevice.address.equals(address, ignoreCase = true)
    }

    private fun registerReceiver() {
        val ctx = context ?: return
        if (receiverRegistered) return
        ctx.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_UI_INIT)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_REFRESH_STATUS)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_ANC_SELECT)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CYCLE_ANC)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET)
            if (BuildConfig.DEBUG) {
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND)
            }
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_REFRESH)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_SET)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_REFRESH)
        }, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun sendConnected(force: Boolean = false) {
        if (connectedBroadcastSent && !force) return
        val currentDevice = device ?: return
        val deviceName = currentDevice.name ?: currentDevice.alias ?: ""
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_CONNECTED) {
            putExtra("address", currentDevice.address)
            putExtra("device_name", deviceName)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_CONNECTED) {
            putExtra("device_name", deviceName)
        }
        connectedBroadcastSent = true
    }

    private fun sendConnectionState(state: String) {
        val currentDevice = device ?: return
        val deviceName = currentDevice.name ?: currentDevice.alias ?: ""
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED) {
            putExtra("address", currentDevice.address)
            putExtra("device_name", deviceName)
            putExtra("state", state)
        }
    }

    private fun sendBattery(
        battery: BatteryParams,
        cached: Boolean = currentBatteryIsCached,
    ) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", battery)
            putBatteryExtras(battery)
            if (cached) putExtra(EXTRA_STATE_CACHED, true)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", battery)
            putBatteryExtras(battery)
            if (cached) putExtra(EXTRA_STATE_CACHED, true)
        }
    }

    private fun sendAnc(
        state: HuaweiAncState,
        cached: Boolean = currentAncIsCached,
    ) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", state.mode.broadcastStatus)
            state.subMode?.let { putExtra("submode", it) }
            if (cached) putExtra(EXTRA_STATE_CACHED, true)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", state.mode.broadcastStatus)
            state.subMode?.let { putExtra("submode", it) }
            if (cached) putExtra(EXTRA_STATE_CACHED, true)
        }
    }

    private fun sendAncLevel(level: Int) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED) {
            putExtra("level", level)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED) {
            putExtra("level", level)
        }
    }

    private fun rememberAncSubMode(state: HuaweiAncState) {
        when (state.mode) {
            NoiseControlMode.NOISE_CANCELLATION -> {
                if (sessionRoute.supportsDiscreteAncLevels) {
                    state.subMode?.takeIf(sessionRoute::supportsAncSubMode)?.let {
                        currentAncLevel = it
                    }
                }
            }
            NoiseControlMode.TRANSPARENCY -> {
                normalizeHuaweiAncSubMode(
                    route = sessionRoute,
                    mode = state.mode,
                    requestedSubMode = state.subMode,
                    previousState = state,
                )?.let { currentTransparencySubMode = it }
            }
            else -> Unit
        }
    }

    private fun defaultTransparencySubMode(route: HuaweiDeviceRoute): Int =
        if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) 0x02 else 0xFF

    private fun sendGestureState(state: HuaweiGestureState) {
        val fillState: Intent.() -> Unit = {
            state.doubleTap?.let { tap ->
                putExtra("left_action", tap.left.extraValue)
                putExtra("right_action", tap.right.extraValue)
                putExtra(HuaweiGestureController.EXTRA_DOUBLE_LEFT_ACTION, tap.left.extraValue)
                putExtra(HuaweiGestureController.EXTRA_DOUBLE_RIGHT_ACTION, tap.right.extraValue)
            }
            state.tripleTap?.let { tap ->
                putExtra(HuaweiGestureController.EXTRA_TRIPLE_LEFT_ACTION, tap.left.extraValue)
                putExtra(HuaweiGestureController.EXTRA_TRIPLE_RIGHT_ACTION, tap.right.extraValue)
            }
            state.swipe?.let { swipe ->
                putExtra(HuaweiGestureController.EXTRA_SWIPE_LEFT_ACTION, swipe.left.extraValue)
                putExtra(HuaweiGestureController.EXTRA_SWIPE_RIGHT_ACTION, swipe.right.extraValue)
            }
        }
        sendAppBroadcast(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_CHANGED, fillState)
        sendExternalBroadcast(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_CHANGED, fillState)
    }

    private fun sendFreeClip2AudioState(state: FreeClip2AudioState) {
        val fillState: Intent.() -> Unit = {
            putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_CONFIRMED, true)
            state.mode?.let {
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_MODE, it.extraValue)
            }
            state.scene?.let {
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_SCENE, it.extraValue)
            }
            state.effect?.let {
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_SOUND_EFFECT, it.extraValue)
            }
        }
        sendAppBroadcast(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED, fillState)
        sendExternalBroadcast(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED, fillState)
    }

    private fun sendAppBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = context ?: return
        val currentDevice = device
        Intent(action).apply {
            putExtra("vendor", "huawei")
            encodeHuaweiDeviceRouteForBroadcast(sessionRoute)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            currentDevice?.let {
                putExtra("address", it.address)
                putExtra("device_name", it.name ?: it.alias ?: "")
            }
            fill()
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    private fun sendExternalBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = context ?: return
        val currentDevice = device
        listOf("com.milink.service", "com.xiaomi.bluetooth", "com.android.settings").forEach { targetPackage ->
            Intent(action).apply {
                putExtra("vendor", "huawei")
                encodeHuaweiDeviceRouteForBroadcast(sessionRoute)?.let {
                    putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
                }
                currentDevice?.let {
                    putExtra("address", it.address)
                    putExtra("device_name", it.name ?: it.alias ?: "")
                }
                fill()
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                ctx.sendBroadcast(this)
            }
        }
    }

    private fun Intent.putBatteryExtras(status: BatteryParams) {
        putExtra("left_battery", status.left?.battery ?: 0)
        putExtra("left_charging", status.left?.isCharging ?: false)
        putExtra("left_connected", status.left?.isConnected ?: false)
        putExtra("right_battery", status.right?.battery ?: 0)
        putExtra("right_charging", status.right?.isCharging ?: false)
        putExtra("right_connected", status.right?.isConnected ?: false)
        putExtra("case_battery", status.case?.battery ?: 0)
        putExtra("case_charging", status.case?.isCharging ?: false)
        putExtra("case_connected", status.case?.isConnected ?: false)
    }

    private fun parseHex(hex: String): ByteArray? {
        val normalized = hex.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        if (normalized.isEmpty() || normalized.length % 2 != 0) return null
        if (!normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return normalized.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}

internal fun matchesHuaweiSessionTarget(
    activeAddress: String,
    activeRoute: HuaweiDeviceRoute,
    requestedAddress: String?,
    requestedRoute: HuaweiDeviceRoute?,
    requireAddress: Boolean,
): Boolean {
    val normalizedAddress = requestedAddress?.takeIf(String::isNotBlank)
    if (requireAddress && normalizedAddress == null) return false
    if (normalizedAddress != null && !normalizedAddress.equals(activeAddress, ignoreCase = true)) {
        return false
    }
    return requestedRoute != null && requestedRoute == activeRoute
}

internal fun matchesFreeClip2AudioSessionTarget(
    activeAddress: String,
    activeRoute: HuaweiDeviceRoute,
    requestedAddress: String?,
    requestedRoute: HuaweiDeviceRoute?,
): Boolean =
    activeRoute == HuaweiDeviceRoute.HUAWEI_FREECLIP2 &&
        requestedRoute == HuaweiDeviceRoute.HUAWEI_FREECLIP2 &&
        matchesHuaweiSessionTarget(
            activeAddress = activeAddress,
            activeRoute = activeRoute,
            requestedAddress = requestedAddress,
            requestedRoute = requestedRoute,
            requireAddress = true,
        )

internal fun normalizeHuaweiPrivateBattery(
    route: HuaweiDeviceRoute,
    battery: BatteryParams,
): BatteryParams = if (route.usesReportedEarbudAvailability) {
    battery
} else {
    battery.normalizedEarbudAvailability()
}

internal fun nextHuaweiAncMode(
    route: HuaweiDeviceRoute,
    currentMode: NoiseControlMode,
): NoiseControlMode = if (route.supportsTransparency) {
    when (currentMode) {
        NoiseControlMode.UNKNOWN,
        NoiseControlMode.OFF -> NoiseControlMode.NOISE_CANCELLATION
        NoiseControlMode.NOISE_CANCELLATION -> NoiseControlMode.TRANSPARENCY
        NoiseControlMode.TRANSPARENCY -> NoiseControlMode.OFF
    }
} else if (currentMode == NoiseControlMode.NOISE_CANCELLATION) {
    NoiseControlMode.OFF
} else {
    NoiseControlMode.NOISE_CANCELLATION
}

internal fun normalizeHuaweiAncSubMode(
    route: HuaweiDeviceRoute,
    mode: NoiseControlMode,
    requestedSubMode: Int?,
    previousState: HuaweiAncState,
): Int? {
    if (mode == NoiseControlMode.NOISE_CANCELLATION && route.supportsDiscreteAncLevels) {
        return requestedSubMode
            ?.takeIf(route::supportsAncSubMode)
            ?: previousState.subMode
                ?.takeIf { previousState.mode == mode && route.supportsAncSubMode(it) }
            ?: route.defaultAncSubMode
    }
    if (mode != NoiseControlMode.TRANSPARENCY || !route.supportsTransparency) return null
    val accepted = when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> setOf(0x01, 0x02)
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 -> setOf(0x01, 0xFF)
        else -> emptySet()
    }
    if (accepted.isEmpty()) return null
    return requestedSubMode
        ?.takeIf(accepted::contains)
        ?: previousState.subMode?.takeIf { previousState.mode == mode && it in accepted }
        ?: if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) 0x02 else 0xFF
}
