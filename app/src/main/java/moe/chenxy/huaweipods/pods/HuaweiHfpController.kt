package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
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
    private const val GESTURE_CONFIRM_DELAY_MS = 300L
    private const val GESTURE_REFRESH_MIN_INTERVAL_MS = 750L

    private var context: Context? = null
    private var device: BluetoothDevice? = null
    private var sessionRoute = HuaweiDeviceRoute.UNSUPPORTED
    private var receiverRegistered = false
    private var currentBattery: BatteryParams? = null
    private var currentAnc = HuaweiAncState(NoiseControlMode.UNKNOWN)
    private var currentAncLevel = 0
    private var currentTransparencySubMode = 0xFF
    private var lastDispatchedAncLevel: Int? = null
    private var connectedBroadcastSent = false
    private var lastBatteryRequestAt = 0L
    private var lastAncRequestAt = 0L
    private var lastGestureStateRequestAt = 0L
    private var ancRequestInFlight = false
    private var sessionGeneration = 0L
    private val batteryIslandTriggerPolicy = BatteryIslandTriggerPolicy()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedIntent = intent ?: return
            when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                HuaweiPodsAction.ACTION_PODS_UI_INIT,
                HuaweiPodsAction.ACTION_REFRESH_STATUS -> {
                    sendConnectionState("connected")
                    sendConnected(force = true)
                    currentBattery?.let { sendBattery(it) }
                    requestPrivateBattery()
                    if (sessionRoute.supportsAnc) {
                        if (!requestAncState()) {
                            sendAnc(currentAnc)
                        }
                        if (currentAnc.mode == NoiseControlMode.NOISE_CANCELLATION ||
                            sessionRoute.supportsAncDirectionDial
                        ) {
                            sendAncLevel(currentAncLevel)
                        }
                    }
                }
                HuaweiPodsAction.ACTION_ANC_SELECT -> {
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
                    setAncLevel(receivedIntent.getIntExtra("level", currentAncLevel))
                }
                HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND -> {
                    if (BuildConfig.DEBUG) sendLegacyDebugHex(receivedIntent.getStringExtra("hex").orEmpty())
                }
                HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET -> {
                    setGesture(receivedIntent)
                }
                HuaweiPodsAction.ACTION_HUAWEI_GESTURE_REFRESH -> {
                    requestGestureState(
                        requestedAddress = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_ADDRESS),
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
        ensureSession(context, device, route)
        sendConnectionState("connecting")
        sendConnected()
        requestAncState(force = true)
        requestPrivateBattery()
    }

    fun disconnectedPod(context: Context, device: BluetoothDevice) {
        if (this.device?.address != device.address) return
        MiuiStrongToastUtil.cancelPodsNotificationByMiuiBt(context, device)
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_DISCONNECTED) {
            putExtra("address", device.address)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_DISCONNECTED) {
            putExtra("address", device.address)
        }
        currentBattery = null
        currentAnc = HuaweiAncState(NoiseControlMode.UNKNOWN)
        currentAncLevel = 0
        currentTransparencySubMode = 0xFF
        lastDispatchedAncLevel = null
        connectedBroadcastSent = false
        lastBatteryRequestAt = 0L
        lastAncRequestAt = 0L
        lastGestureStateRequestAt = 0L
        ancRequestInFlight = false
        sessionGeneration++
        this.device = null
        this.context = null
        sessionRoute = HuaweiDeviceRoute.UNSUPPORTED
        HuaweiL2capAncController.disconnect(device)
        Log.d(TAG, "Huawei HFP disconnected device=${device.address}")
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
        ensureSession(context, device, route)
        val battery = result.battery.normalizedEarbudAvailability()
        currentBattery = battery
        sendConnectionState("connected")
        sendConnected()
        sendBattery(battery)
        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(context, battery, device)
        maybeShowBatteryIsland(context, battery, device)
        Log.i(TAG, "Huawei battery parsed device=${device.address} values=${result.values}")
        return battery
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
                rememberAncSubMode(targetState)
                sendAnc(targetState)
            }
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
            val validLevel = HuaweiAncLevel.fromProtocolValue(level)?.protocolValue ?: run {
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
            HuaweiL2capAncController.disconnect(previousDevice)
        }
        if (newSession) {
            currentBattery = null
            currentAnc = HuaweiAncState(NoiseControlMode.UNKNOWN)
            currentAncLevel = if (route.supportsDiscreteAncLevels) {
                HuaweiAncLevel.ADAPTIVE.protocolValue
            } else {
                0
            }
            currentTransparencySubMode = defaultTransparencySubMode(route)
            lastDispatchedAncLevel = null
            connectedBroadcastSent = false
            lastBatteryRequestAt = 0L
            lastAncRequestAt = 0L
            lastGestureStateRequestAt = 0L
            ancRequestInFlight = false
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
    }

    private fun requestPrivateBattery() {
        val currentContext = context ?: return
        val currentDevice = device ?: return
        if (!sessionRoute.supportsRfcommBattery) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBatteryRequestAt < 10_000L) return
        lastBatteryRequestAt = now
        val requestedAddress = currentDevice.address
        HuaweiL2capAncController.requestBattery(
            currentContext,
            currentDevice,
            sessionRoute,
        ) { battery ->
            val activeDevice = device
            if (activeDevice == null || !activeDevice.address.equals(requestedAddress, ignoreCase = true)) {
                return@requestBattery
            }
            currentBattery = battery
            sendConnectionState("connected")
            sendConnected()
            sendBattery(battery)
            MiuiStrongToastUtil.showPodsNotificationByMiuiBt(currentContext, battery, activeDevice)
            maybeShowBatteryIsland(currentContext, battery, activeDevice)
            Log.i(TAG, "Huawei RFCOMM battery updated device=$requestedAddress")
        }
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
                    rememberAncSubMode(it)
                    sendAnc(it)
                    if (it.mode == NoiseControlMode.NOISE_CANCELLATION) {
                        sendAncLevel(currentAncLevel)
                    }
                }
                return@requestAncState
            }
            currentAnc = state
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

    private fun sendBattery(battery: BatteryParams) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", battery)
            putBatteryExtras(battery)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", battery)
            putBatteryExtras(battery)
        }
    }

    private fun sendAnc(state: HuaweiAncState) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", state.mode.broadcastStatus)
            state.subMode?.let { putExtra("submode", it) }
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", state.mode.broadcastStatus)
            state.subMode?.let { putExtra("submode", it) }
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
                    HuaweiAncLevel.fromProtocolValue(state.subMode ?: -1)?.let {
                        currentAncLevel = it.protocolValue
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

    private fun sendAppBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = context ?: return
        val currentDevice = device
        Intent(action).apply {
            putExtra("vendor", "huawei")
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
            ?.takeIf { HuaweiAncLevel.fromProtocolValue(it) != null }
            ?: previousState.subMode
                ?.takeIf { previousState.mode == mode && HuaweiAncLevel.fromProtocolValue(it) != null }
            ?: HuaweiAncLevel.ADAPTIVE.protocolValue
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
