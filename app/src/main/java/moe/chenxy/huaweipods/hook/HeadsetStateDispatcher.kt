package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.SystemClock
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.pods.HuaweiHfpController
import moe.chenxy.huaweipods.pods.HuaweiDeviceInfoIdentity
import moe.chenxy.huaweipods.pods.HuaweiDeviceRouteProbePolicy
import moe.chenxy.huaweipods.pods.HuaweiDeviceRouteProbeSession
import moe.chenxy.huaweipods.pods.HuaweiL2capAncController
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.smartaudio.OfficialImageIdentityBridge
import moe.chenxy.huaweipods.utils.SystemApisUtils.setIconVisibility
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

object HeadsetStateDispatcher : HookContext() {
    private const val ROUTE_PROBE_WATCHDOG_MS = 5_500L

    private var appRequestReceiverRegistered = false
    private val connectedA2dpAddresses = ConcurrentHashMap.newKeySet<String>()
    private val activeRouteProbe = AtomicReference<HuaweiDeviceRouteProbeSession?>(null)
    private val lastRouteProbeStartedAtMs = ConcurrentHashMap<String, Long>()

    override fun onHook() {
        runCatching {
            hookAfter(findMethod("com.android.bluetooth.btservice.AdapterService", "onCreate")) {
                registerAppRequestReceiver(instance as? Context)
            }
        }.onFailure {
            Log.w("HuaweiPods", "AdapterService.onCreate hook skipped", it)
        }

        hookAfter(findMethodByParamCount("com.android.bluetooth.a2dp.A2dpService", "handleConnectionStateChanged", 3)) {
            val currState = args[2] as Int
            val fromState = args[1] as Int
            val device = args[0] as BluetoothDevice?
            val handler = getObjectField(instance, "mHandler") as Handler
            if (device == null || currState == fromState) {
                return@hookAfter
            }
            handler.post {
                runCatching {
                    val normalizedAddress = device.address.uppercase()
                    if (currState == BluetoothHeadset.STATE_CONNECTED) {
                        connectedA2dpAddresses.add(normalizedAddress)
                    } else if (
                        currState == BluetoothHeadset.STATE_DISCONNECTING ||
                        currState == BluetoothHeadset.STATE_DISCONNECTED
                    ) {
                        connectedA2dpAddresses.remove(normalizedAddress)
                    }
                    val isHuawei = isHuaweiPod(device)
                    Log.d("HuaweiPods", "A2DP Connection State: $currState, isHuaweiPod=$isHuawei")
                    val context = instance as ContextWrapper
                    registerAppRequestReceiver(context)
                    if (!isHuawei) return@runCatching

                    val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
                    if (currState == BluetoothHeadset.STATE_CONNECTED) {
                        statusBarManager.setIconVisibility("wireless_headset", true)
                        HuaweiHfpController.connectPod(context, device)
                    } else if (currState == BluetoothHeadset.STATE_DISCONNECTING || currState == BluetoothHeadset.STATE_DISCONNECTED) {
                        statusBarManager.setIconVisibility("wireless_headset", false)
                        HuaweiHfpController.disconnectedPod(context, device)
                    }
                }.onFailure {
                    Log.e("HuaweiPods", "A2DP state callback failed without interrupting Bluetooth", it)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerAppRequestReceiver(context: Context?) {
        if (context == null || appRequestReceiverRegistered) return
        val registered = runCatching {
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (context == null) return
                    val receivedIntent = intent ?: return
                    runCatching {
                        when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                            HuaweiPodsAction.ACTION_PODS_UI_INIT,
                            HuaweiPodsAction.ACTION_REFRESH_STATUS -> {
                                context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE).apply {
                                    setPackage(BuildConfig.APPLICATION_ID)
                                    putExtra(HuaweiPodsAction.EXTRA_MODULE_BUILD_ID, BuildConfig.MODULE_BUILD_ID)
                                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                })
                            }
                            HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST -> {
                                val device = receivedIntent.getParcelableExtra("device", BluetoothDevice::class.java)
                                    ?: return@runCatching
                                Log.d("HuaweiPods", "connect request from app device=${device.name}/${device.address}")
                                val supported = isHuaweiPod(device)
                                if (supported && isDeviceConnected(device)) {
                                    HuaweiHfpController.connectPod(context, device)
                                } else if (supported) {
                                    notifyRejectedDevice(
                                        context = context,
                                        device = device,
                                        state = "error",
                                        operation = "connect",
                                        reason = "not_connected",
                                        supported = true,
                                    )
                                } else {
                                    notifyRejectedDevice(context, device, state = "error", operation = "connect")
                                }
                            }
                            HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_REQUEST -> {
                                if (
                                    HuaweiDeviceRouteProbePolicy.isTrustedRequestSender(
                                        sentFromPackage,
                                    )
                                ) {
                                    handleDeviceRouteProbeRequest(context, receivedIntent)
                                } else {
                                    Log.w(
                                        "HuaweiPods",
                                        "Rejected route probe sender=${sentFromPackage.orEmpty()}",
                                    )
                                }
                            }
                        }
                    }.onFailure {
                        Log.e("HuaweiPods", "App request receiver failed without interrupting Bluetooth", it)
                    }
                }
            }, IntentFilter().apply {
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_UI_INIT)
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_REFRESH_STATUS)
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST)
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_REQUEST)
            }, Context.RECEIVER_EXPORTED)
        }.onFailure {
            Log.e("HuaweiPods", "Failed to register app request receiver", it)
        }.isSuccess
        if (registered) appRequestReceiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceRouteProbeRequest(context: Context, intent: Intent) {
        val session = HuaweiDeviceRouteProbePolicy.session(
            address = intent.getStringExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_ADDRESS),
            generation = intent.getLongExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_GENERATION, -1L),
            nonce = intent.getStringExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_NONCE),
        ) ?: return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val device = runCatching { adapter?.getRemoteDevice(session.address) }.getOrNull()
        if (device == null) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        val eligible = HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(
            bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false),
            systemConnected = isActiveA2dpDevice(device),
        ) && !isHuaweiPod(device)
        if (!eligible) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        val nowMs = SystemClock.elapsedRealtime()
        val cooldownAccepted = synchronized(lastRouteProbeStartedAtMs) {
            val allowed = HuaweiDeviceRouteProbePolicy.cooldownAllows(
                lastStartedAtMs = lastRouteProbeStartedAtMs[session.address],
                nowMs = nowMs,
            )
            if (allowed) lastRouteProbeStartedAtMs[session.address] = nowMs
            allowed
        }
        if (!cooldownAccepted) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        if (!activeRouteProbe.compareAndSet(null, session)) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        Handler(context.mainLooper).postDelayed(
            { completeDeviceRouteProbe(context, session, null) },
            ROUTE_PROBE_WATCHDOG_MS,
        )
        Log.d("HuaweiPods", "route-free DeviceInfo probe started device=${session.address}")
        HuaweiL2capAncController.requestDeviceInfoIdentityRouteFree(context, device) { identity ->
            if (activeRouteProbe.get() != session) return@requestDeviceInfoIdentityRouteFree
            val stillEligible = HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(
                bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }
                    .getOrDefault(false),
                systemConnected = isActiveA2dpDevice(device),
            ) && !isHuaweiPod(device)
            val verifiedRoute = identity?.takeIf { stillEligible }?.let {
                HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute(it.modelId, it.subModelId)
            }
            if (identity == null || verifiedRoute == null) {
                completeDeviceRouteProbe(context, session, null)
                return@requestDeviceInfoIdentityRouteFree
            }
            if (activeRouteProbe.get() != session) return@requestDeviceInfoIdentityRouteFree
            // Provider only accepts com.android.bluetooth and repeats strict identity/route
            // validation before binding. The exported result is therefore UX continuation,
            // not the authority that establishes the route.
            OfficialImageIdentityBridge.publishVerifiedRouteAsync(
                context = context,
                address = session.address,
                route = verifiedRoute,
                identity = identity,
                callbackHandler = Handler(context.mainLooper),
            ) { publishResult ->
                if (activeRouteProbe.get() != session) return@publishVerifiedRouteAsync
                val remainsConnected = HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(
                    bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }
                        .getOrDefault(false),
                    systemConnected = isActiveA2dpDevice(device),
                )
                completeDeviceRouteProbe(
                    context = context,
                    session = session,
                    identity = identity.takeIf {
                        publishResult.routeReady && remainsConnected
                    },
                )
            }
        }
    }

    private fun completeDeviceRouteProbe(
        context: Context,
        session: HuaweiDeviceRouteProbeSession,
        identity: HuaweiDeviceInfoIdentity?,
    ) {
        if (!activeRouteProbe.compareAndSet(session, null)) return
        sendDeviceRouteProbeResult(context, session, identity)
    }

    private fun sendDeviceRouteProbeResult(
        context: Context,
        session: HuaweiDeviceRouteProbeSession,
        identity: HuaweiDeviceInfoIdentity?,
    ) {
        val resultIntent = Intent(HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_RESULT).apply {
            putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_ADDRESS, session.address)
            putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_GENERATION, session.generation)
            putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_NONCE, session.nonce)
            identity?.let {
                putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_MODEL_ID, it.modelId)
                putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_SUB_MODEL_ID, it.subModelId)
            }
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        context.sendIdentitySharingBroadcast(resultIntent)
    }

    @SuppressLint("MissingPermission")
    private fun notifyRejectedDevice(
        context: Context,
        device: BluetoothDevice,
        state: String,
        operation: String,
        reason: String = "unsupported",
        supported: Boolean = false,
    ) {
        val deviceName = device.name ?: device.alias ?: ""
        Log.w(
            "HuaweiPods",
            "rejected device $operation request reason=$reason device=$deviceName/${device.address}",
        )
        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED).apply {
            putExtra("address", device.address)
            putExtra("device_name", deviceName)
            putExtra("state", state)
            putExtra("reason", reason)
            putExtra("supported", supported)
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    @SuppressLint("MissingPermission")
    private fun isHuaweiPod(device: BluetoothDevice): Boolean {
        return runCatching { device.huaweiDeviceRoute().isSupported }
            .onFailure { Log.e("HuaweiPods", "Huawei route resolution failed", it) }
            .getOrDefault(false)
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        if (device.address.uppercase() in connectedA2dpAddresses) return true
        return runCatching {
            val method = device.javaClass.methods.firstOrNull {
                it.name == "isConnected" && it.parameterCount in 0..1
            } ?: return@runCatching false
            when (method.parameterCount) {
                0 -> method.invoke(device) as? Boolean == true
                else -> method.invoke(device, BluetoothDevice.TRANSPORT_AUTO) as? Boolean == true
            }
        }.onFailure {
            Log.w("HuaweiPods", "BluetoothDevice.isConnected unavailable device=${device.address}", it)
        }.getOrDefault(false)
    }

    private fun isActiveA2dpDevice(device: BluetoothDevice): Boolean =
        device.address.uppercase() in connectedA2dpAddresses
}
