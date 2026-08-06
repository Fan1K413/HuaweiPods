package moe.chenxy.huaweipods.hook.milink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.hook.HuaweiAncSubModeSelectorView
import moe.chenxy.huaweipods.hook.HookContext
import moe.chenxy.huaweipods.hook.Log
import moe.chenxy.huaweipods.hook.callMethod
import moe.chenxy.huaweipods.hook.getObjectField
import moe.chenxy.huaweipods.hook.setObjectField
import moe.chenxy.huaweipods.pods.HuaweiAncLevel
import moe.chenxy.huaweipods.pods.HuaweiAncState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.detectHuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.normalizeHuaweiAncSubMode
import moe.chenxy.huaweipods.pods.resolveHuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.pods.usesReportedEarbudAvailability
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.normalizedEarbudAvailability
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class MiLinkAncSelection(
    val status: Int,
    val subMode: Int? = null,
)

/** 将 Huawei 的 1/2/3 状态映射为融合设备中心的 0/1/2。无 ANC 的机型不参与接管。 */
internal fun miLinkAncModeFor(
    route: HuaweiDeviceRoute,
    huaweiStatus: Int,
): Int? {
    if (!route.supportsAnc) return null
    return when (huaweiStatus) {
        2, 5, 6, 7, 8 -> 1
        3 -> if (route.supportsTransparency) 2 else 0
        else -> 0
    }
}

/** 只接受当前机型确实支持的融合设备中心状态。 */
internal fun huaweiAncStatusForMiLink(
    route: HuaweiDeviceRoute,
    miLinkMode: Int,
): Int? {
    if (!route.supportsAnc) return null
    return when (miLinkMode) {
        0 -> NoiseControlMode.OFF.broadcastStatus
        1 -> NoiseControlMode.NOISE_CANCELLATION.broadcastStatus
        2 -> NoiseControlMode.TRANSPARENCY.broadcastStatus.takeIf { route.supportsTransparency }
        else -> null
    }
}

/** 与耳机控制器共用子模式校验；Pro 5 的通透子模式由融合设备中心保留。 */
internal fun normalizeMiLinkAncSubMode(
    route: HuaweiDeviceRoute,
    huaweiStatus: Int,
    requestedSubMode: Int?,
    storedSubMode: Int?,
): Int? {
    val mode = NoiseControlMode.fromBroadcastStatus(huaweiStatus)
    if (!route.supportsAnc || mode == NoiseControlMode.UNKNOWN) return null
    if (mode == NoiseControlMode.TRANSPARENCY && !route.supportsTransparency) return null
    if (
        route == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 &&
        mode == NoiseControlMode.TRANSPARENCY
    ) {
        val accepted = setOf(0x01, 0xFF)
        return requestedSubMode?.takeIf(accepted::contains)
            ?: storedSubMode?.takeIf(accepted::contains)
            ?: 0xFF
    }
    return normalizeHuaweiAncSubMode(
        route = route,
        mode = mode,
        requestedSubMode = requestedSubMode,
        previousState = HuaweiAncState(mode, storedSubMode),
    )
}

@SuppressLint("MissingPermission")
object MiLinkServiceHook : HookContext() {
    internal const val TAG = "HuaweiPods-MiLink"
    private const val PREFS_NAME = "huaweipods_milink_state"
    private const val PREF_WINDOWS_HOST_IDS = "windows_host_ids"
    private const val CIRCULATE_STATE_DISCONNECTED = 0
    private const val CIRCULATE_STATE_CONNECTED = 2
    private const val CIRCULATE_STATE_CONNECTING = 3
    private const val CIRCULATE_RESULT_ACTIVE_CHANGED_FAILED = 2011
    private const val CIRCULATE_SERVICE_HEADSET_PRIMARY = 393216
    private const val CIRCULATE_SERVICE_HEADSET_FALLBACK = 524288
    private const val HEADSET_BOND_BONDED = 306
    private const val ANC_SUBMODE_SELECTOR_TAG = "huaweipods_milink_anc_submode"
    private const val PREF_ANC_SUBMODE = "anc_submode"
    private const val PREF_ANC_SUBMODE_LEGACY_6I = "anc_level"
    private const val PREF_ANC_SUBMODE_LEGACY_PRO3 = "huawei_anc_level"
    private const val PREF_TRANSPARENCY_SUBMODE = "transparency_submode"
    private val knownHuaweiAddresses = linkedSetOf<String>()
    private val knownWindowsHostIds = linkedSetOf<String>()
    private val ancCards = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    internal var context: Context? = null
    private var receiverRegistered = false
    internal var currentAddress: String? = null
    private var currentName: String? = null
    private var currentBattery: BatteryParams = BatteryParams()
    private var currentAnc = 1
    private var currentAncSubMode: Int? = HuaweiAncLevel.ADAPTIVE.protocolValue
    private var currentTransparencySubMode: Int? = null
    internal var lastAncBatteryController: Any? = null
    internal var lastProfileContext: Any? = null
    private var circulationSignalRewriteUntilMs = 0L
    private var circulationUiCompletedUntilMs = 0L
    private var circulationTargetHostId: String? = null
    private var lastHeadsetServiceClient: Any? = null
    private var lastHeadsetDeviceInfo: Any? = null
    private var lastHeadsetServiceInfo: Any? = null
    private val localBluetoothConnectBurstToken = AtomicInteger(0)

    override fun onHook() {
        hookContextEntry()
        hookMxBluetoothRuntime()
        hookHeadsetRuntimeDisplay()
        hookHeadsetCirculationExperiment()
        hookWindowsHeadsetCirculationCapability()
        hookWindowsHeadsetBondState()
        hookCirculatePlusHeadsetAncCard()
    }

    private fun hookContextEntry() {
        listOf(
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        ).forEach { className ->
            runCatching {
                hookBefore(findMethod(className, "getInstanceForIsMiTWS", Context::class.java)) {
                    registerStatusReceiver(args[0] as? Context)
                }
            }.onFailure { Log.w(TAG, "hook $className.getInstanceForIsMiTWS skipped", it) }
        }
    }

    private fun hookMxBluetoothRuntime() {
        val classes = listOf(
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        )
        classes.forEach { className ->
            hookBluetoothDeviceResult(className, "checkIsMiTWS") { 1 }
            hookBluetoothDeviceResult(className, "getDeviceId") { fakeDeviceId() }
            hookBluetoothDeviceResult(className, "getBatteryLevel") { 1 }
            hookBluetoothDeviceResult(className, "getAncState", requiresAnc = true) { miLinkAncState() }
            hookBluetoothDeviceResult(className, "getDeviceRunInfo") { 0 }
            hookBluetoothDeviceResult(className, "getWearStatus") { "0,0" }
            hookBluetoothDeviceResult(className, "isLeAudio") { false }
            hookAncCommand(className, "openAnc", 2, 1)
            hookAncCommand(className, "closeAnc", 1, 0)
            hookAncCommand(className, "openTransparent", 3, 1, requiresTransparency = true)
        }
        classes.forEach { className ->
            hookStringAddressResult(className, "isMiTWS") { true }
            hookStringAddressResult(className, "isSupportAudioSwitch") { miLinkSwitchState() }
            hookStringAddressResult(className, "getRingFindState") { false }
            hookTransparentFeatureMethods(className)
        }
    }

    private fun hookHeadsetRuntimeDisplay() {
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getBatteryLevel") { miLinkBatteryLevels() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getAncState",
            requiresAnc = true,
        ) { miLinkAncState() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getBatteryLevelCache") { miLinkBatteryLevels() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getHeadsetPropertyBlock") { batteryPercentForMiLink() }
        hookStringAddressResult("com.miui.headset.runtime.AncBatteryController", "getSwitchState") { miLinkSwitchState() }
        hookTransparentFeatureMethods("com.miui.headset.runtime.AncBatteryController")
        hookTransparentFeatureMethods("com.miui.headset.runtime.ProfileContext")
        hookTransparentFeatureMethods("com.miui.headset.api.HeadsetInfo")
        hookAncStateBlock()
        hookHeadsetInfoNoArg("getDeviceId") { fakeDeviceId() }
        hookHeadsetInfoNoArg("component3") { fakeDeviceId() }
        hookHeadsetInfoNoArg("getPowers") { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("component4") { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("getMode", requiresAnc = true) { miLinkAncState() }
        hookHeadsetInfoNoArg("component5", requiresAnc = true) { miLinkAncState() }
        hookHeadsetInfoNoArg("getSwitchState") { miLinkSwitchState() }
        hookHeadsetInfoNoArg("component8") { miLinkSwitchState() }
    }

    internal fun hookBluetoothDeviceResult(
        className: String,
        methodName: String,
        requiresAnc: Boolean = false,
        result: () -> Any,
    ) {
        runCatching {
            hookAfter(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookAfter
                val route = routeForDevice(device)
                if (!route.isSupported || requiresAnc && !route.supportsAnc) return@hookAfter
                cacheRuntimeOwner(className, instance)
                captureRuntimeContext(instance)
                this.result = result()
                if (className == "com.miui.headset.runtime.AncBatteryController" && methodName == "getHeadsetPropertyBlock") {
                    notifyHeadsetPropertyChanged(instance, device, 4)
                }
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(BluetoothDevice) skipped", it) }
    }

    internal fun hookStringAddressResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, String::class.java)) {
                val address = args[0] as? String ?: return@hookAfter
                if (!isHuaweiAddress(address)) return@hookAfter
                this.result = result()
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(String) skipped", it) }
    }

    private fun hookAncCommand(
        className: String,
        methodName: String,
        huaweiAnc: Int,
        result: Int,
        requiresTransparency: Boolean = false,
    ) {
        runCatching {
            hookBefore(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                val route = routeForDevice(device)
                if (!route.isSupported) return@hookBefore
                if (!route.supportsAnc || requiresTransparency && !route.supportsTransparency) {
                    this.result = 0
                    return@hookBefore
                }
                cacheRuntimeOwner(className, instance)
                captureRuntimeContext(instance)
                val selection = selectionForStatus(route, huaweiAnc) ?: run {
                    this.result = 0
                    return@hookBefore
                }
                applyAncSelection(selection)
                saveState(context)
                sendHuaweiAnc(selection)
                sendAncChanged(selection)
                refreshAncCards("$methodName-command")
                this.result = result
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName command skipped", it) }
    }

    private fun hookAncStateBlock() {
        runCatching {
            hookBefore(findMethod("com.miui.headset.runtime.AncBatteryController", "setAncStateBlock", BluetoothDevice::class.java, Int::class.javaPrimitiveType!!)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                val route = routeForDevice(device)
                if (!route.isSupported) return@hookBefore
                if (!route.supportsAnc) {
                    this.result = 0
                    return@hookBefore
                }
                lastAncBatteryController = instance
                captureRuntimeContext(instance)
                val miLinkMode = args[1] as? Int ?: return@hookBefore
                val huaweiStatus = huaweiAncStatusForMiLink(route, miLinkMode)
                val selection = huaweiStatus?.let { selectionForStatus(route, it) }
                if (selection == null) {
                    this.result = 0
                    return@hookBefore
                }
                val instanceContext = runCatching { getObjectField(instance, "context") as? Context }.getOrNull()
                if (instanceContext != null) {
                    context = instanceContext.applicationContext ?: instanceContext
                }
                applyAncSelection(selection)
                saveState(instanceContext)
                sendHuaweiAnc(selection, instanceContext)
                sendAncChanged(selection, instanceContext)
                refreshAncCards("setAncStateBlock")
                notifyHeadsetPropertyChanged(instance, device, 8)
                notifyHeadsetPropertyChanged(instance, device, 4)
                this.result = miLinkAncState()
            }
        }.onFailure { Log.w(TAG, "hook AncBatteryController.setAncStateBlock skipped", it) }
    }

    internal fun hookHeadsetInfoNoArg(
        methodName: String,
        requiresAnc: Boolean = false,
        result: () -> Any,
    ) {
        runCatching {
            hookAfter(findMethodByParamCount("com.miui.headset.api.HeadsetInfo", methodName, 0)) {
                val route = routeForHeadsetInfo(instance)
                if (!route.isSupported || requiresAnc && !route.supportsAnc) return@hookAfter
                this.result = result()
            }
        }.onFailure { Log.w(TAG, "hook HeadsetInfo.$methodName skipped", it) }
    }

    private fun hookTransparentFeatureMethods(className: String) {
        runCatching {
            findClass(className).declaredMethods
                .filter { method ->
                    val name = method.name.lowercase()
                    ("transparent" in name || "transparency" in name) &&
                        method.returnType in listOf(
                            Boolean::class.javaPrimitiveType,
                            java.lang.Boolean::class.java,
                            Int::class.javaPrimitiveType,
                            java.lang.Integer::class.java,
                        )
                }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        hookAfter(method) {
                            val route = routeForMethodTarget(args, instance)
                            if (!route.isSupported) return@hookAfter
                            val supported = route.supportsTransparency
                            val enabled = if (isTransparencyCapabilityMethod(method.name)) {
                                supported
                            } else {
                                supported && currentAnc == NoiseControlMode.TRANSPARENCY.broadcastStatus
                            }
                            this.result = when (method.returnType) {
                                Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java -> enabled
                                else -> if (enabled) 1 else 0
                            }
                            Log.d(
                                TAG,
                                "MiLink transparency ${method.declaringClass.name}.${method.name} " +
                                    "route=$route result=${this.result}",
                            )
                        }
                    }.onFailure {
                        Log.w(TAG, "hook ${method.declaringClass.name}.${method.name} transparency skipped", it)
                    }
                }
        }.onFailure { Log.w(TAG, "hook $className transparency methods skipped", it) }
    }

    private fun hookCirculatePlusHeadsetAncCard() {
        runCatching {
            val detailClass = findClass("com.miui.circulateplus.world.headset.HeadSetsDetail")
            val cardClass = findClass("com.miui.circulateplus.world.headset.j")
            hookConstructorAfter(cardClass.getDeclaredConstructor(detailClass).apply { isAccessible = true }) {
                configureAncCard(result ?: instance, "constructor")
            }
            cardClass.declaredMethods
                .filter { it.returnType == Void.TYPE && it.parameterTypes.size <= 1 }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        hookAfter(method) {
                            configureAncCard(instance, method.name)
                        }
                    }.onFailure { Log.w(TAG, "hook ${cardClass.name}.${method.name} hide transparency skipped", it) }
                }
        }.onFailure { Log.w(TAG, "hook CirculatePlus headset ANC card skipped", it) }
    }

    private fun configureAncCard(card: Any?, reason: String) {
        if (card == null) return
        loadState()
        val route = currentHuaweiRoute()
        if (!route.isSupported) return
        ancCards[card] = true
        val clearView = runCatching { getObjectField(card, "f") as? View }.getOrNull()
        configureAncCardViews(card, route, clearView, reason)
        clearView?.post {
            configureAncCardViews(card, currentHuaweiRoute(), clearView, "$reason-post")
        }
    }

    private fun configureAncCardViews(
        card: Any,
        route: HuaweiDeviceRoute,
        clearView: View?,
        reason: String,
    ) {
        if (!route.isSupported) return
        val modeRow = clearView?.parent as? ViewGroup
        val ancContainer = modeRow?.parent as? ViewGroup
        val capabilityContainer: View? = ancContainer ?: modeRow ?: clearView

        if (!route.supportsAnc) {
            setCapabilityViewVisible(capabilityContainer, false)
            ancContainer?.let { findTaggedView(it, ANC_SUBMODE_SELECTOR_TAG)?.visibility = View.GONE }
            Log.d(TAG, "MiLink ANC card hidden route=$route reason=$reason card=${card.javaClass.name}")
            return
        }

        setCapabilityViewVisible(capabilityContainer, true)
        setCapabilityViewVisible(clearView, route.supportsTransparency)
        if (modeRow == null || ancContainer == null) return

        val selector = findTaggedView(ancContainer, ANC_SUBMODE_SELECTOR_TAG) as? HuaweiAncSubModeSelectorView
        val supportsCurrentSubMode = when (currentAnc) {
            NoiseControlMode.NOISE_CANCELLATION.broadcastStatus -> route.supportsDiscreteAncLevels
            NoiseControlMode.TRANSPARENCY.broadcastStatus -> route.supportsTransparency
            else -> false
        }
        if (!supportsCurrentSubMode) {
            selector?.visibility = View.GONE
            return
        }

        val options = selectorOptions(route, currentAnc)
        if (options.isEmpty()) {
            selector?.visibility = View.GONE
            return
        }
        val selected = selectionForStatus(route, currentAnc)?.subMode ?: options.first().value
        val targetSelector = selector ?: HuaweiAncSubModeSelectorView(ancContainer.context) { subMode ->
            val activeRoute = currentHuaweiRoute()
            val selection = selectionForStatus(activeRoute, currentAnc, subMode)
                ?: return@HuaweiAncSubModeSelectorView
            applyAncSelection(selection)
            saveState(ancContainer.context)
            sendHuaweiAnc(selection, ancContainer.context)
            sendAncChanged(selection, ancContainer.context)
            refreshAncCards("submode-selected")
        }.apply {
            tag = ANC_SUBMODE_SELECTOR_TAG
            val insertIndex = (ancContainer.indexOfChild(modeRow) + 1)
                .coerceIn(0, ancContainer.childCount)
            ancContainer.addView(
                this,
                insertIndex,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        targetSelector.render(options, selected, isDarkSurface(ancContainer))
        targetSelector.visibility = View.VISIBLE
        Log.d(TAG, "MiLink ANC submode configured route=$route mode=$currentAnc reason=$reason")
    }

    private fun selectorOptions(
        route: HuaweiDeviceRoute,
        status: Int,
    ): List<HuaweiAncSubModeSelectorView.Option> = when (status) {
        NoiseControlMode.NOISE_CANCELLATION.broadcastStatus -> listOf(
            HuaweiAncSubModeSelectorView.Option(
                HuaweiAncLevel.ADAPTIVE.protocolValue,
                moduleString(R.string.anc_level_adaptive, "智慧动态"),
            ),
            HuaweiAncSubModeSelectorView.Option(
                HuaweiAncLevel.LIGHT.protocolValue,
                moduleString(R.string.anc_level_light, "轻度"),
            ),
            HuaweiAncSubModeSelectorView.Option(
                HuaweiAncLevel.BALANCED.protocolValue,
                moduleString(R.string.anc_level_balanced, "均衡"),
            ),
            HuaweiAncSubModeSelectorView.Option(
                HuaweiAncLevel.DEEP.protocolValue,
                moduleString(R.string.anc_level_deep, "深度"),
            ),
        )
        NoiseControlMode.TRANSPARENCY.broadcastStatus -> when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> transparencyOptions(standardValue = 0x02)
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 -> transparencyOptions(standardValue = 0xFF)
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun transparencyOptions(standardValue: Int) = listOf(
        HuaweiAncSubModeSelectorView.Option(
            standardValue,
            moduleString(R.string.transparency_standard, "普通"),
        ),
        HuaweiAncSubModeSelectorView.Option(
            0x01,
            moduleString(R.string.transparency_voice, "人声增强"),
        ),
    )

    private fun moduleString(resId: Int, fallback: String): String {
        val hostContext = context ?: return fallback
        return runCatching {
            hostContext.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
                .getString(resId)
        }.getOrDefault(fallback)
    }

    private fun findTaggedView(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTaggedView(view.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    private fun setCapabilityViewVisible(view: View?, visible: Boolean) {
        view ?: return
        view.visibility = if (visible) View.VISIBLE else View.GONE
        view.isEnabled = visible
        view.requestLayout()
    }

    private fun isDarkSurface(view: View): Boolean {
        var current: View? = view
        repeat(4) {
            val color = when (val background = current?.background) {
                is ColorDrawable -> background.color
                is GradientDrawable -> background.color?.defaultColor
                else -> null
            }
            if (color != null && Color.alpha(color) > 32) {
                val luminance = (
                    Color.red(color) * 299 +
                        Color.green(color) * 587 +
                        Color.blue(color) * 114
                    ) / 1000
                return luminance < 128
            }
            current = current?.parent as? View
        }
        return true
    }

    private fun routeForMethodTarget(args: List<Any?>, instance: Any?): HuaweiDeviceRoute {
        args.forEach { arg ->
            when (arg) {
                is BluetoothDevice -> routeForDevice(arg).takeIf { it.isSupported }?.let { return it }
                is String -> routeForAddress(arg).takeIf { it.isSupported }?.let { return it }
            }
        }
        return routeForHeadsetInfo(instance).takeIf { it.isSupported } ?: currentHuaweiRoute()
    }

    private fun isTransparencyCapabilityMethod(methodName: String): Boolean {
        val name = methodName.lowercase()
        return "support" in name ||
            "capability" in name ||
            "available" in name ||
            "hastransparent" in name
    }

    private fun hookHeadsetCirculationExperiment() {
        runCatching {
            val circulateParamClass = findClass("com.miui.circulate.api.bean.CirculateParam")
            hookBefore(
                findMethod(
                    "com.miui.circulate.api.protocol.headset.HeadsetServiceClient",
                    "circulateService",
                    List::class.java,
                    List::class.java,
                    circulateParamClass
                )
            ) {
                val param = args[2] ?: return@hookBefore
                val serviceInfo = runCatching { getObjectField(param, "circulateServiceInfo") }.getOrNull()
                if (!isTargetCirculateHeadset(serviceInfo)) return@hookBefore
                lastHeadsetServiceClient = instance
                lastHeadsetServiceInfo = serviceInfo
                cacheHeadsetDeviceInfo(instance, serviceInfo)
                Log.w(
                    TAG,
                    "MiLink circulate experiment circulateService " +
                        "service=${describeCirculateService(serviceInfo)} " +
                        "returnHosts=${describeCollection(args[0])} targetHosts=${describeCollection(args[1])}"
                )

                val wasLocked = isHeadsetCirculationLocked(instance)
                if (wasLocked) {
                    clearHeadsetCirculationLock(instance, "before circulateService")
                }
                proceedWithArgs(args[0], args[1], param)
                clearHeadsetCirculationLock(instance, if (wasLocked) "after retry circulateService" else "after circulateService")
            }
        }.onFailure { Log.w(TAG, "hook HeadsetServiceClient.circulateService experiment skipped", it) }

        runCatching {
            val headsetHostClass = findClass("com.miui.headset.api.HeadsetHost")
            hookBefore(
                findMethod(
                    "com.miui.circulate.api.protocol.headset.HeadsetServiceClient\$3",
                    "onHeadsetHostUpdate",
                    Int::class.javaPrimitiveType!!,
                    headsetHostClass
                )
            ) {
                val type = args[0] as? Int ?: return@hookBefore
                if (type != 3 || !isCurrentHuaweiHeadset()) return@hookBefore
                val hostId = headsetHostId(args[1])
                if (shouldSuppressCirculationActiveLost(hostId)) {
                    Log.w(TAG, "MiLink circulate experiment suppress target ActiveHeadsetLost host=$hostId")
                    this.result = null
                    return@hookBefore
                }
                Log.w(TAG, "MiLink circulate experiment allow ActiveHeadsetLost host=$hostId target=$circulationTargetHostId")
            }
        }.onFailure { Log.w(TAG, "hook HeadsetServiceClient\$3.onHeadsetHostUpdate experiment skipped", it) }

        runCatching {
            hookBefore(
                findMethod(
                    "com.miui.headset.runtime.AncBatteryController\$mmaCallback\$1",
                    "onConnectMmaStateChanged",
                    BluetoothDevice::class.java,
                    Boolean::class.javaPrimitiveType!!
                )
            ) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                val connected = args[1] as? Boolean ?: return@hookBefore
                if (connected || !isHuaweiPod(device)) return@hookBefore
                Log.w(TAG, "MiLink circulate experiment suppress MMA disconnect device=${device.name}/${device.address}")
                this.result = null
            }
        }.onFailure { Log.w(TAG, "hook AncBatteryController.mmaCallback experiment skipped", it) }

        runCatching {
            hookAfter(
                findMethod(
                    "com.miui.headset.runtime.HeadsetLocalServiceImpl\$profileStubAdapter\$1",
                    "connect",
                    Long::class.javaPrimitiveType!!,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java
                )
            ) {
                val address = args[3] as? String ?: return@hookAfter
                if (!isHuaweiAddress(address) && !isCurrentHuaweiHeadset()) return@hookAfter
                if (result == 100) return@hookAfter
                val targetHostId = args[2] as? String
                circulationTargetHostId = targetHostId
                circulationSignalRewriteUntilMs = System.currentTimeMillis() + 15_000L
                Log.w(TAG, "MiLink circulate experiment force connect success host=$targetHostId address=$address original=$result")
                this.result = 100
            }
        }.onFailure { Log.w(TAG, "hook HeadsetLocalServiceImpl.profileStubAdapter.connect experiment skipped", it) }

        runCatching {
            hookBefore(
                findMethod(
                    "com.miui.headset.api.RequestInvokeSync",
                    "signal",
                    String::class.java,
                    Int::class.javaPrimitiveType!!
                )
            ) {
                val resultCode = args[1] as? Int ?: return@hookBefore
                if (resultCode != CIRCULATE_RESULT_ACTIVE_CHANGED_FAILED || !isCirculationRewriteActive()) return@hookBefore
                Log.w(TAG, "MiLink circulate experiment rewrite async signal requestId=${args[0]} result=2011->100 target=$circulationTargetHostId")
                circulationSignalRewriteUntilMs = System.currentTimeMillis() + 30_000L
                proceedWithArgs(args[0], 100)
            }
        }.onFailure { Log.w(TAG, "hook RequestInvokeSync.signal experiment skipped", it) }

        runCatching {
            val deviceInfoClass = findClass("com.miui.circulate.api.service.CirculateDeviceInfo")
            val serviceInfoClass = findClass("com.miui.circulate.api.service.CirculateServiceInfo")
            hookBefore(
                findMethod(
                    "com.miui.circulate.world.headset.HeadsetContentManager",
                    "g0",
                    Int::class.javaPrimitiveType!!,
                    deviceInfoClass,
                    serviceInfoClass
                )
            ) {
                val state = args[0] as? Int ?: return@hookBefore
                val serviceInfo = args[2] ?: return@hookBefore
                if (!isTargetCirculateHeadset(serviceInfo)) return@hookBefore
                if (state == CIRCULATE_RESULT_ACTIVE_CHANGED_FAILED) {
                    Log.w(TAG, "MiLink circulate experiment suppress UI rollback state=$state service=$serviceInfo")
                    this.result = null
                    return@hookBefore
                }
                if (state == CIRCULATE_STATE_CONNECTING && markCirculateServiceConnected(serviceInfo, "g0")) {
                    circulationUiCompletedUntilMs = System.currentTimeMillis() + 30_000L
                    Log.w(TAG, "MiLink circulate experiment patch UI target connected state=3->2 service=$serviceInfo")
                    proceedWithArgs(CIRCULATE_STATE_CONNECTED, args[1], serviceInfo)
                }
            }
        }.onFailure { Log.w(TAG, "hook HeadsetContentManager.g0 experiment skipped", it) }

        runCatching {
            val deviceInfoClass = findClass("com.miui.circulate.api.service.CirculateDeviceInfo")
            hookBefore(
                findMethod(
                    "com.miui.circulate.api.protocol.headset.HeadsetServiceClient",
                    "clientConnect",
                    deviceInfoClass,
                    deviceInfoClass
                )
            ) {
                if (!isCurrentHuaweiHeadset()) return@hookBefore
                val targetDevice = args[0]
                val headsetDevice = args[1]
                val ret = proceedWithArgs(targetDevice, headsetDevice) as? Int
                Log.w(
                    TAG,
                    "MiLink circulate experiment clientConnect ret=$ret " +
                        "target=${describeCirculateDevice(targetDevice)} headset=${describeCirculateDevice(headsetDevice)}"
                )
            }
        }.onFailure { Log.w(TAG, "hook HeadsetServiceClient.clientConnect experiment skipped", it) }

        runCatching {
            val ballClass = findClass("com.miui.circulate.world.view.ball.l")
            val deviceCardClass = findClass("e8.j")
            val headsetCardClass = findClass("com.miui.circulate.world.headset.ui.g")
            hookBefore(
                findMethod(
                    "com.miui.circulate.world.headset.HeadsetContentManager",
                    "m0",
                    ballClass,
                    deviceCardClass,
                    headsetCardClass
                )
            ) {
                val target = args[1] ?: return@hookBefore
                val headset = args[2] ?: return@hookBefore
                val serviceInfo = runCatching { callMethod(headset, "S") }.getOrNull()
                if (!isTargetCirculateHeadset(serviceInfo)) return@hookBefore
                lastHeadsetServiceInfo = serviceInfo
                val origin = runCatching { callMethod(headset, "P") }.getOrNull()
                if (isWindowsCirculateDevice(target)) {
                    rememberWindowsCirculateDevice(target)
                }
                Log.w(
                    TAG,
                    "MiLink circulate experiment m0 target=${circulateDeviceName(target)} local=${isLocalCirculateDevice(target)} " +
                        "origin=${circulateDeviceName(origin)} originLocal=${isLocalCirculateDevice(origin)} " +
                        "targetDetail=${describeCirculateCard(target)} originDetail=${describeCirculateCard(origin)} " +
                        "service=${describeCirculateService(serviceInfo)}"
                )
                markCirculateServiceConnected(serviceInfo, "m0")
                if (origin == null || !isLocalCirculateDevice(target) || isLocalCirculateDevice(origin)) return@hookBefore
                if (scheduleReturnToLocalViaClient(target, headset, serviceInfo)) {
                    Log.w(
                        TAG,
                        "MiLink circulate experiment scheduled direct return to local origin=${circulateDeviceName(origin)} target=${circulateDeviceName(target)}"
                    )
                    this.result = true
                    return@hookBefore
                }
                val ret = callMethod(instance, "D", origin, target, headset, true) as? Int ?: return@hookBefore
                Log.w(
                    TAG,
                    "MiLink circulate experiment force return to local ret=$ret origin=${circulateDeviceName(origin)} target=${circulateDeviceName(target)}"
                )
                this.result = ret == 0
            }
        }.onFailure { Log.w(TAG, "hook HeadsetContentManager.m0 return experiment skipped", it) }
    }

    private fun hookWindowsHeadsetCirculationCapability() {
        runCatching {
            val deviceInfoClass = findClass("com.miui.circulate.api.service.CirculateDeviceInfo")
            deviceInfoClass.declaredMethods
                .filter { method ->
                    method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType
                }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        hookAfter(method) {
                            val serviceType = args[0] as? Int ?: return@hookAfter
                            if (!shouldPatchWindowsHeadsetService(instance, serviceType)) return@hookAfter
                            val patched = patchedWindowsHeadsetServiceResult(method.returnType, result)
                                ?: return@hookAfter
                            Log.w(
                                TAG,
                                "MiLink PC circulate patch ${method.name} service=$serviceType " +
                                    "original=$result patched=$patched target=${describeCirculateDevice(instance)}"
                            )
                            this.result = patched
                        }
                    }.onFailure {
                        Log.w(TAG, "hook CirculateDeviceInfo.${method.name} PC service skipped", it)
                    }
                }
        }.onFailure { Log.w(TAG, "hook CirculateDeviceInfo PC service capability skipped", it) }
    }

    private fun hookWindowsHeadsetBondState() {
        listOf(
            "com.miui.headset.api.Query",
            "com.miui.headset.api.HeadsetClient\$queryProxyAdapter\$1",
            "com.miui.headset.runtime.RemoteProtocol\$Proxy",
            "com.miui.headset.runtime.QueryLocal",
            "com.miui.headset.runtime.QueryServer",
            "com.miui.headset.runtime.HeadsetRemoteImpl",
            "com.miui.headset.runtime.QueryLocal\$getBondStateWithTargetHost\$1"
        ).forEach { className ->
            runCatching {
                findClass(className).declaredMethods
                    .filter { method ->
                        method.name == "getBondStateWithTargetHost" || method.name == "invoke"
                    }
                    .filter { method ->
                        method.parameterTypes.size == 2 &&
                            method.parameterTypes[0] == String::class.java &&
                            method.parameterTypes[1] == String::class.java
                    }
                    .forEach { method ->
                        method.isAccessible = true
                        hookAfter(method) {
                            val targetAddress = args[0] as? String
                            val targetHostId = args[1] as? String
                            if (!shouldPatchWindowsBondState(targetAddress, targetHostId)) return@hookAfter
                            if (result == HEADSET_BOND_BONDED) return@hookAfter
                            Log.w(
                                TAG,
                                "MiLink PC circulate patch bond state method=${method.declaringClass.name}.${method.name} " +
                                    "targetAddress=$targetAddress targetHostId=$targetHostId original=$result patched=$HEADSET_BOND_BONDED"
                            )
                            this.result = HEADSET_BOND_BONDED
                        }
                    }
            }.onFailure { Log.w(TAG, "hook $className PC bond state skipped", it) }
        }
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedIntent = intent ?: return
                when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                    HuaweiPodsAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                        refreshAncCards("config-changed")
                    }
                    HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        saveState(context)
                    }
                    HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                    }
                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentBattery = (
                            receivedIntent.batteryStatusFromExtras()
                                ?: receivedIntent.parcelableStatus()
                                ?: currentBattery
                            ).let(::normalizeBatteryAvailabilityForCurrentRoute)
                        saveState(context)
                    }
                    HuaweiPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        val route = currentHuaweiRoute()
                        val status = receivedIntent.getIntExtra("status", currentAnc)
                        val requestedSubMode = receivedIntent.getIntExtra("submode", -1)
                            .takeIf { receivedIntent.hasExtra("submode") && it >= 0 }
                        when {
                            !route.supportsAnc -> applyAncSelection(MiLinkAncSelection(1))
                            status == 3 && !route.supportsTransparency -> Unit
                            status in setOf(1, 2, 3) -> {
                                selectionForStatus(route, status, requestedSubMode)
                                    ?.let(::applyAncSelection)
                            }
                            status in setOf(5, 6, 7, 8) -> currentAnc = status
                        }
                        saveState(context)
                        refreshAncCards("anc-changed")
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        val route = currentHuaweiRoute()
                        if (!route.supportsDiscreteAncLevels || currentAnc != 2) return
                        val level = receivedIntent.getIntExtra("level", -1)
                        selectionForStatus(route, currentAnc, level)
                            ?.let(::applyAncSelection)
                            ?: return
                        saveState(context)
                        refreshAncCards("anc-level-changed")
                    }
                }
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        context?.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_UI_INIT).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    internal fun isHuaweiPod(device: BluetoothDevice): Boolean {
        return routeForDevice(device).isSupported
    }

    internal fun isHuaweiAddress(address: String): Boolean {
        val normalized = address.uppercase()
        return resolveHuaweiDeviceRoute(address, null).isSupported ||
            normalized == currentAddress?.uppercase() ||
            normalized in knownHuaweiAddresses
    }

    private fun routeForHeadsetInfo(info: Any?): HuaweiDeviceRoute {
        if (info == null) return HuaweiDeviceRoute.UNSUPPORTED
        listOf("getAddress", "component1").forEach { method ->
            val address = runCatching { callMethod(info, method) as? String }.getOrNull()
            if (address != null) {
                routeForAddress(address).takeIf { it.isSupported }?.let { return it }
            }
        }
        return HuaweiDeviceRoute.UNSUPPORTED
    }

    private fun miLinkAncState(): Int {
        loadState()
        return miLinkAncModeFor(currentHuaweiRoute(), currentAnc) ?: 0
    }

    private fun currentHuaweiRoute(): HuaweiDeviceRoute =
        resolveHuaweiDeviceRoute(currentAddress, currentName)

    private fun routeForAddress(address: String): HuaweiDeviceRoute {
        val currentDeviceName = currentName.takeIf {
            address.equals(currentAddress, ignoreCase = true)
        }
        return resolveHuaweiDeviceRoute(address, currentDeviceName)
    }

    private fun routeForDevice(device: BluetoothDevice): HuaweiDeviceRoute {
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching {
            device.name?.takeIf(String::isNotBlank)
                ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        var route = device.huaweiDeviceRoute()
        if (
            !route.isSupported &&
            detectHuaweiDeviceRoute(name) == HuaweiDeviceRoute.UNSUPPORTED &&
            address != null &&
            (address.equals(currentAddress, ignoreCase = true) || address.uppercase() in knownHuaweiAddresses)
        ) {
            route = resolveHuaweiDeviceRoute(address, currentName)
        }
        if (route.isSupported) {
            address?.takeIf(String::isNotBlank)?.let {
                knownHuaweiAddresses.add(it.uppercase())
                currentAddress = it
            }
            name?.takeIf(String::isNotBlank)?.let { currentName = it }
        }
        return route
    }

    private fun selectionForStatus(
        route: HuaweiDeviceRoute,
        status: Int,
        requestedSubMode: Int? = null,
    ): MiLinkAncSelection? {
        if (!route.supportsAnc || status == 3 && !route.supportsTransparency) return null
        if (status !in setOf(1, 2, 3)) return null
        val storedSubMode = when (status) {
            2 -> currentAncSubMode
            3 -> currentTransparencySubMode
            else -> null
        }
        return MiLinkAncSelection(
            status = status,
            subMode = normalizeMiLinkAncSubMode(route, status, requestedSubMode, storedSubMode),
        )
    }

    private fun applyAncSelection(selection: MiLinkAncSelection) {
        currentAnc = selection.status
        when (selection.status) {
            2 -> currentAncSubMode = selection.subMode
            3 -> currentTransparencySubMode = selection.subMode
        }
    }

    private fun refreshAncCards(reason: String) {
        val cards = synchronized(ancCards) { ancCards.keys.toList() }
        cards.forEach { card ->
            runCatching { configureAncCard(card, reason) }
                .onFailure { Log.w(TAG, "MiLink ANC card refresh failed reason=$reason", it) }
        }
    }

    private fun miLinkBatteryLevels(): List<Int> {
        loadState()
        val left = batteryValue(currentBattery.left)
        val right = batteryValue(currentBattery.right)
        val box = batteryValue(currentBattery.case)
        return listOf(
            box,
            left,
            right,
            chargingValue(currentBattery.case),
            chargingValue(currentBattery.left),
            chargingValue(currentBattery.right)
        )
    }

    private fun batteryPercentForMiLink(): Int {
        loadState()
        val values = listOfNotNull(currentBattery.left, currentBattery.right)
            .filter { it.isConnected }
            .map { it.battery.coerceIn(0, 100) }
        return values.minOrNull() ?: 0
    }

    private fun batteryValue(params: moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams?): Int {
        if (params?.isConnected != true) return -1
        return params.battery.coerceIn(0, 100)
    }

    private fun chargingValue(params: moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams?): Int {
        return if (params?.isConnected == true && params.isCharging) 1 else 0
    }

    private fun sendHuaweiAnc(selection: MiLinkAncSelection, fallbackContext: Context? = null) {
        val route = currentHuaweiRoute()
        if (!route.supportsAnc || selection.status == 3 && !route.supportsTransparency) {
            Log.d(TAG, "sendHuaweiAnc skipped: route=$route selection=$selection")
            return
        }
        val ctx = fallbackContext ?: context ?: run {
            Log.w(TAG, "sendHuaweiAnc skipped: context is null selection=$selection")
            return
        }
        Intent(HuaweiPodsAction.ACTION_ANC_SELECT).apply {
            putExtra("status", selection.status)
            selection.subMode?.let { putExtra("submode", it) }
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    private fun sendAncChanged(selection: MiLinkAncSelection, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: return
        listOf(BuildConfig.APPLICATION_ID, "com.milink.service", "com.android.settings").forEach { targetPackage ->
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED).apply {
                currentAddress?.let { putExtra("address", it) }
                currentName?.let { putExtra("device_name", it) }
                putExtra("status", selection.status)
                selection.subMode?.let { putExtra("submode", it) }
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    internal fun miLinkSwitchState(): Int {
        loadState()
        return 1
    }

    private fun rememberSupportedDevice(intent: Intent): Boolean {
        val address = intent.getStringExtra("address")
            ?.takeIf(String::isNotBlank)
            ?: currentAddress
        val name = intent.getStringExtra("device_name")
            ?.takeIf(String::isNotBlank)
            ?: currentName
        val route = resolveHuaweiDeviceRoute(address, name)
        if (!route.isSupported) {
            Log.w(TAG, "ignored unsupported persisted/broadcast device=${name.orEmpty()}/${address.orEmpty()}")
            return false
        }
        val previousRoute = currentHuaweiRoute()
        val identityChanged = when {
            !address.isNullOrBlank() -> !address.equals(currentAddress, ignoreCase = true)
            else -> name != currentName
        }
        currentName = name
        currentAddress = address
        currentAddress?.takeIf { it.isNotBlank() }?.let { knownHuaweiAddresses.add(it.uppercase()) }
        if (identityChanged || previousRoute != route) {
            resetAncState(route)
        }
        return true
    }

    private fun resetAncState(route: HuaweiDeviceRoute) {
        currentAnc = NoiseControlMode.OFF.broadcastStatus
        currentAncSubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.NOISE_CANCELLATION.broadcastStatus,
            requestedSubMode = null,
            storedSubMode = null,
        )
        currentTransparencySubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.TRANSPARENCY.broadcastStatus,
            requestedSubMode = null,
            storedSubMode = null,
        )
    }

    private fun isCurrentHuaweiHeadset(): Boolean {
        loadState()
        return !currentAddress.isNullOrBlank() &&
            resolveHuaweiDeviceRoute(currentAddress, currentName).isSupported
    }

    private fun isTargetCirculateHeadset(serviceInfo: Any?): Boolean {
        if (serviceInfo == null) return false
        val serviceId = runCatching { getObjectField(serviceInfo, "serviceId") as? String }.getOrNull().orEmpty()
        val deviceId = runCatching { getObjectField(serviceInfo, "deviceId") as? String }.getOrNull()
        return detectHuaweiDeviceRoute(serviceId).isSupported ||
            serviceId == currentName?.takeIf { it.isNotBlank() } ||
            deviceId == fakeDeviceId() ||
            isCurrentHuaweiHeadset()
    }

    private fun isCirculationRewriteActive(): Boolean {
        return System.currentTimeMillis() <= circulationSignalRewriteUntilMs && isCurrentHuaweiHeadset()
    }

    private fun isHeadsetCirculationLocked(client: Any?): Boolean {
        return runCatching { (getObjectField(client, "isCirculating") as? AtomicBoolean)?.get() == true }
            .getOrDefault(false)
    }

    private fun clearHeadsetCirculationLock(client: Any?, reason: String) {
        if (client == null || !isCurrentHuaweiHeadset()) return
        val now = System.currentTimeMillis()
        val isRewriteWindow = now <= circulationSignalRewriteUntilMs
        val isUiCompletedWindow = now <= circulationUiCompletedUntilMs
        if (!isRewriteWindow && !isUiCompletedWindow && !isHeadsetCirculationLocked(client)) return

        val startTime = runCatching { getObjectField(client, "circulateStartTime") as? Long }.getOrNull()
        runCatching { (getObjectField(client, "isCirculating") as? AtomicBoolean)?.set(false) }
        runCatching { setObjectField(client, "circulateStartTime", 0L) }
        listOf(
            "mBondingHeadsetDevice",
            "mBondingHeadsetService",
            "mBondingReturnHostDevice",
            "mBondingTargetHostDevice"
        ).forEach { fieldName ->
            runCatching { setObjectField(client, fieldName, null) }
        }
        listOf("connectDeviceList", "disconnectDeviceList").forEach { fieldName ->
            runCatching { (getObjectField(client, fieldName) as? MutableCollection<*>)?.clear() }
        }
        Log.w(
            TAG,
            "MiLink circulate experiment clear stale processing reason=$reason " +
                "startTime=$startTime rewrite=$isRewriteWindow uiCompleted=$isUiCompletedWindow"
        )
    }

    private fun scheduleReturnToLocalViaClient(targetCard: Any?, headsetCard: Any?, serviceInfo: Any?): Boolean {
        val client = lastHeadsetServiceClient ?: return false
        val targetDevice = circulateDeviceInfoFromCard(targetCard) ?: return false
        val headsetDevice = cacheHeadsetDeviceInfo(client, serviceInfo) ?: return false

        clearHeadsetCirculationLock(client, "before local clientConnect")
        circulationSignalRewriteUntilMs = System.currentTimeMillis() + 20_000L
        circulationUiCompletedUntilMs = System.currentTimeMillis() + 30_000L
        circulationTargetHostId = "local_device_id"
        markCirculateServiceConnected(serviceInfo, "schedule-local-clientConnect")
        updateHeadsetAttachedCard(headsetCard, targetCard, "schedule-local-clientConnect")
        playLocalReturnTone("schedule-local-clientConnect")
        startLocalBluetoothConnectBurst("schedule-local-clientConnect")

        Thread {
            val ret = runCatching { callMethod(client, "clientConnect", targetDevice, headsetDevice) as? Int }
                .onFailure { Log.w(TAG, "MiLink circulate experiment async local clientConnect failed", it) }
                .getOrNull()
            Log.w(TAG, "MiLink circulate experiment async local clientConnect ret=$ret")
            if (ret != null && isCirculateConnectAccepted(ret)) {
                clearHeadsetCirculationLock(client, "after async local clientConnect")
                updateHeadsetAttachedCard(headsetCard, targetCard, "after async local clientConnect")
            }
        }.apply {
            name = "HuaweiPods-MiLinkReturn"
            isDaemon = true
        }.start()
        return true
    }

    private fun playLocalReturnTone(reason: String) {
        Thread {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 80) }
                .onFailure { Log.w(TAG, "MiLink circulate experiment local return tone init failed reason=$reason", it) }
                .getOrNull() ?: return@Thread
            runCatching {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                Thread.sleep(240)
            }.onFailure {
                Log.w(TAG, "MiLink circulate experiment local return tone failed reason=$reason", it)
            }
            runCatching { tone.release() }
        }.apply {
            name = "HuaweiPods-MiLinkTone"
            isDaemon = true
        }.start()
    }

    private fun startLocalBluetoothConnectBurst(reason: String) {
        val token = localBluetoothConnectBurstToken.incrementAndGet()
        Thread {
            var lastAttemptAtMs = 0L
            listOf(0L, 650L, 1_800L).forEach { attemptAtMs ->
                if (localBluetoothConnectBurstToken.get() != token) return@Thread
                val sleepMs = attemptAtMs - lastAttemptAtMs
                if (sleepMs > 0) Thread.sleep(sleepMs)
                lastAttemptAtMs = attemptAtMs
                if (localBluetoothConnectBurstToken.get() != token) return@Thread
                connectLocalBluetoothProfilesOnce(reason)
            }
        }.apply {
            name = "HuaweiPods-MiLinkBtConnect"
            isDaemon = true
        }.start()
    }

    private fun connectLocalBluetoothProfilesOnce(reason: String) {
        val ctx = context ?: return
        val address = currentAddress ?: return
        val adapter = runCatching { ctx.getSystemService(BluetoothManager::class.java).adapter }
            .onFailure { Log.w(TAG, "MiLink circulate experiment local bt connect adapter failed reason=$reason", it) }
            .getOrNull() ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }
            .onFailure { Log.w(TAG, "MiLink circulate experiment local bt connect device failed reason=$reason address=$address", it) }
            .getOrNull() ?: return
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).forEach { profile ->
            runCatching {
                adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(connectedProfile: Int, proxy: BluetoothProfile) {
                        if (connectedProfile != profile) return
                        try {
                            val state = runCatching { proxy.getConnectionState(device) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
                            if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
                                Log.w(TAG, "MiLink circulate experiment local bt connect skip profile=$profile state=$state reason=$reason device=${device.address}")
                                return
                            }
                            runCatching {
                                proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device)
                                Log.w(TAG, "MiLink circulate experiment local bt connect profile=$profile reason=$reason device=${device.address}")
                            }.onFailure {
                                Log.w(TAG, "MiLink circulate experiment local bt connect profile failed profile=$profile reason=$reason", it)
                            }
                        } finally {
                            runCatching { adapter.closeProfileProxy(profile, proxy) }
                        }
                    }

                    override fun onServiceDisconnected(disconnectedProfile: Int) = Unit
                }, profile)
            }.onFailure {
                Log.w(TAG, "MiLink circulate experiment local bt profile proxy failed profile=$profile reason=$reason", it)
            }
        }
    }

    private fun updateHeadsetAttachedCard(headsetCard: Any?, targetCard: Any?, reason: String) {
        if (headsetCard == null || targetCard == null) return
        val before = runCatching { callMethod(headsetCard, "P") }.getOrNull()
        runCatching { callMethod(headsetCard, "i0", targetCard) }
            .onFailure { Log.w(TAG, "MiLink circulate experiment patch headset origin failed reason=$reason", it) }
            .getOrElse { return }
        val after = runCatching { callMethod(headsetCard, "P") }.getOrNull()
        Log.w(
            TAG,
            "MiLink circulate experiment patch headset origin reason=$reason " +
                "before=${circulateDeviceName(before)} after=${circulateDeviceName(after)}"
        )
    }

    private fun circulateDeviceInfoFromCard(deviceCard: Any?): Any? {
        val data = runCatching { callMethod(deviceCard, "M") }.getOrNull() ?: deviceCard ?: return null
        if (data.javaClass.name == "com.miui.circulate.api.service.CirculateDeviceInfo") return data
        return runCatching { callMethod(data, "b") }.getOrNull()
            ?: runCatching { getObjectField(data, "a") }.getOrNull()
            ?: data.javaClass.declaredMethods
                .firstOrNull {
                    it.parameterTypes.isEmpty() &&
                        it.returnType.name == "com.miui.circulate.api.service.CirculateDeviceInfo"
                }
                ?.let {
                    runCatching {
                        it.isAccessible = true
                        it.invoke(data)
                    }.getOrNull()
                }
    }

    private fun cacheHeadsetDeviceInfo(client: Any?, serviceInfo: Any?): Any? {
        val direct = listOf("circulateBluetoothDevice", "mBondingHeadsetDevice")
            .firstNotNullOfOrNull { fieldName ->
                runCatching { getObjectField(client, fieldName) }.getOrNull()
            }
        if (direct != null) {
            lastHeadsetDeviceInfo = direct
            return direct
        }

        val deviceId = runCatching { getObjectField(serviceInfo, "deviceId") as? String }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return lastHeadsetDeviceInfo
        val manager = runCatching {
            findClass("com.miui.circulate.api.protocol.headset.HeadsetDeviceManager")
                .getDeclaredMethod("get")
                .apply { isAccessible = true }
                .invoke(null)
        }.getOrNull()
        val fromManager = runCatching { callMethod(manager, "getBluetoothDevice", deviceId) }.getOrNull()
        if (fromManager != null) lastHeadsetDeviceInfo = fromManager
        return fromManager ?: lastHeadsetDeviceInfo
    }

    private fun isCirculateConnectAccepted(ret: Int): Boolean {
        return ret == 100 || ret == 301 || ret == 308
    }

    private fun shouldSuppressCirculationActiveLost(hostId: String?): Boolean {
        if (!isCirculationRewriteActive() || hostId == null || hostId == "local_device_id") return false
        val targetHostId = circulationTargetHostId
        return targetHostId == null || targetHostId == hostId
    }

    private fun headsetHostId(host: Any?): String? {
        if (host == null) return null
        listOf("getHostId", "component1").forEach { methodName ->
            runCatching { callMethod(host, methodName) as? String }.getOrNull()?.let { return it }
        }
        return runCatching { getObjectField(host, "hostId") as? String }.getOrNull()
    }

    private fun isLocalCirculateDevice(deviceCard: Any?): Boolean {
        val deviceData = runCatching { callMethod(deviceCard, "M") }.getOrNull()
        val priority = runCatching { callMethod(deviceData, "getPriority") as? Int }.getOrNull()
        if (priority == -1) return true
        val name = circulateDeviceName(deviceCard)
        return localDeviceNames().any { it.equals(name, ignoreCase = true) }
    }

    private fun shouldPatchWindowsHeadsetService(deviceInfo: Any?, serviceType: Int): Boolean {
        if (!isCurrentHuaweiHeadset()) return false
        if (serviceType != CIRCULATE_SERVICE_HEADSET_PRIMARY && serviceType != CIRCULATE_SERVICE_HEADSET_FALLBACK) {
            return false
        }
        val isWindows = isWindowsCirculateDevice(deviceInfo)
        if (isWindows) rememberWindowsCirculateDevice(deviceInfo)
        return isWindows
    }

    private fun shouldPatchWindowsBondState(targetAddress: String?, targetHostId: String?): Boolean {
        if (!isCurrentHuaweiHeadset()) return false
        if (targetHostId.isNullOrBlank() || targetHostId == "local_device_id") return false
        loadWindowsHostIds()
        if (targetHostId in knownWindowsHostIds) return true
        return targetAddress != null && targetAddress in knownWindowsHostIds
    }

    private fun patchedWindowsHeadsetServiceResult(returnType: Class<*>, original: Any?): Any? {
        if (original != null && original != false && original != 0) return null
        val serviceInfo = lastHeadsetServiceInfo
        return when {
            returnType == Boolean::class.javaPrimitiveType || returnType == java.lang.Boolean::class.java -> true
            returnType == Int::class.javaPrimitiveType || returnType == java.lang.Integer::class.java -> 1
            serviceInfo != null && returnType.isInstance(serviceInfo) -> serviceInfo
            returnType == Any::class.java && serviceInfo != null -> serviceInfo
            else -> null
        }
    }

    private fun isWindowsCirculateDevice(deviceInfo: Any?): Boolean {
        if (deviceInfo == null) return false
        val directType = runCatching { callMethod(deviceInfo, "getDeviceType") as? String }.getOrNull()
            ?: runCatching { getObjectField(deviceInfo, "deviceType") as? String }.getOrNull()
        if (directType.equals("Windows", ignoreCase = true)) return true
        val description = runCatching { describeCirculateDevice(deviceInfo) }.getOrNull().orEmpty()
        return "DeviceType=Windows" in description || "devicesType=Windows" in description
    }

    private fun rememberWindowsCirculateDevice(deviceInfo: Any?) {
        if (deviceInfo == null) return
        val before = knownWindowsHostIds.size
        listOf("getHostId", "getDeviceId", "getId").forEach { methodName ->
            runCatching { callMethod(deviceInfo, methodName) as? String }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { knownWindowsHostIds.add(it) }
        }
        listOf("hostId", "deviceId", "id").forEach { fieldName ->
            runCatching { getObjectField(deviceInfo, fieldName) as? String }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { knownWindowsHostIds.add(it) }
        }
        if (knownWindowsHostIds.size != before) {
            saveWindowsHostIds()
            Log.w(TAG, "MiLink PC circulate remember Windows hosts=$knownWindowsHostIds target=${describeCirculateDevice(deviceInfo)}")
        }
    }

    private fun saveWindowsHostIds() {
        val ctx = context ?: return
        if (knownWindowsHostIds.isEmpty()) return
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_WINDOWS_HOST_IDS, knownWindowsHostIds)
                .apply()
        }.onFailure {
            Log.w(TAG, "MiLink PC circulate save Windows hosts failed", it)
        }
    }

    private fun loadWindowsHostIds() {
        val ctx = context ?: return
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(PREF_WINDOWS_HOST_IDS, emptySet())
                .orEmpty()
                .filter { it.isNotBlank() }
        }.onSuccess { stored ->
            val before = knownWindowsHostIds.size
            knownWindowsHostIds.addAll(stored)
            if (knownWindowsHostIds.size != before) {
                Log.w(TAG, "MiLink PC circulate loaded Windows hosts=$knownWindowsHostIds")
            }
        }.onFailure {
            Log.w(TAG, "MiLink PC circulate load Windows hosts failed", it)
        }
    }

    private fun circulateDeviceName(deviceCard: Any?): String {
        val deviceData = runCatching { callMethod(deviceCard, "M") }.getOrNull()
        return runCatching { callMethod(deviceData, "getName") as? String }.getOrNull().orEmpty()
    }

    private fun describeCirculateCard(deviceCard: Any?): String {
        val deviceData = runCatching { callMethod(deviceCard, "M") }.getOrNull() ?: deviceCard
        return describeCirculateDevice(deviceData)
    }

    private fun describeCirculateDevice(deviceInfo: Any?): String {
        if (deviceInfo == null) return "null"
        val methodValues = linkedMapOf<String, Any?>()
        listOf(
            "getName",
            "getDeviceId",
            "getId",
            "getHostId",
            "getDeviceType",
            "getDeviceModel",
            "getPriority",
            "getAddress",
            "getBtMac",
            "getMac"
        ).forEach { methodName ->
            runCatching { callMethod(deviceInfo, methodName) }
                .getOrNull()
                ?.let { methodValues[methodName.removePrefix("get")] = it }
        }
        return buildString {
            append(deviceInfo.javaClass.name)
            if (methodValues.isNotEmpty()) {
                append(methodValues.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" })
            } else {
                append("{")
                append(deviceInfo.toString())
                append("}")
            }
        }
    }

    private fun describeCirculateService(serviceInfo: Any?): String {
        if (serviceInfo == null) return "null"
        val values = linkedMapOf<String, Any?>()
        listOf(
            "serviceId",
            "deviceId",
            "headsetId",
            "connectState",
            "serviceName",
            "hostId",
            "deviceType"
        ).forEach { fieldName ->
            runCatching { getObjectField(serviceInfo, fieldName) }
                .getOrNull()
                ?.let { values[fieldName] = it }
        }
        listOf("getServiceId", "getDeviceId", "getConnectState").forEach { methodName ->
            runCatching { callMethod(serviceInfo, methodName) }
                .getOrNull()
                ?.let { values[methodName.removePrefix("get")] = it }
        }
        runCatching { getObjectField(serviceInfo, "serviceProperties") }
            .getOrNull()
            ?.let { properties ->
                val all = runCatching { callMethod(properties, "getAll") }.getOrNull()
                values["properties"] = all ?: properties
            }
        return serviceInfo.javaClass.name + values.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }
    }

    private fun describeCollection(value: Any?): String {
        val collection = value as? Collection<*> ?: return value?.toString().orEmpty()
        return collection.joinToString(prefix = "[", postfix = "]") { describeCirculateDevice(it) }
    }

    private fun localDeviceNames(): Set<String> {
        val settingsName = context?.contentResolver?.let { resolver ->
            runCatching { Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        }.orEmpty()
        return setOf(
            settingsName,
            Build.MODEL.orEmpty(),
            "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()}".trim()
        ).filter { it.isNotBlank() }.toSet()
    }

    private fun circulateServiceState(serviceInfo: Any?): Int? {
        return runCatching { getObjectField(serviceInfo, "connectState") as? Int }.getOrNull()
            ?: runCatching { callMethod(serviceInfo, "getConnectState") as? Int }.getOrNull()
    }

    private fun markCirculateServiceConnected(serviceInfo: Any?, reason: String): Boolean {
        if (!isTargetCirculateHeadset(serviceInfo)) return false
        val state = circulateServiceState(serviceInfo)
        if (state != CIRCULATE_STATE_CONNECTING) return false
        runCatching { setObjectField(serviceInfo, "connectState", CIRCULATE_STATE_CONNECTED) }
            .onFailure { Log.w(TAG, "MiLink circulate experiment patch service state failed reason=$reason", it) }
            .getOrElse { return false }
        return true
    }

    internal fun cacheRuntimeOwner(className: String, owner: Any?) {
        when (className) {
            "com.miui.headset.runtime.AncBatteryController" -> lastAncBatteryController = owner
            "com.miui.headset.runtime.ProfileContext" -> lastProfileContext = owner
        }
    }

    internal fun captureRuntimeContext(owner: Any?) {
        val ownerContext = runCatching { getObjectField(owner, "context") as? Context }.getOrNull()
            ?: runCatching { getObjectField(lastProfileContext, "context") as? Context }.getOrNull()
            ?: runCatching { getObjectField(lastAncBatteryController, "context") as? Context }.getOrNull()
            ?: return
        context = ownerContext.applicationContext ?: ownerContext
    }

    private fun notifyHeadsetPropertyChanged(controller: Any?, device: BluetoothDevice, updateType: Int) {
        val listener = runCatching { getObjectField(controller, "headsetPropertyChangeListener") }.getOrNull() ?: return
        runCatching {
            callMethod(listener, "invoke", device, updateType)
        }.onFailure { }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableStatus(): BatteryParams? {
        return runCatching { getParcelableExtra("status", BatteryParams::class.java) }.getOrNull()
            ?: runCatching { getParcelableExtra<BatteryParams>("status") }.getOrNull()
    }

    private fun Intent.batteryStatusFromExtras(): BatteryParams? {
        if (!hasExtra("left_connected") && !hasExtra("right_connected") && !hasExtra("case_connected")) return null
        return BatteryParams(
            left = PodParams(
                getIntExtra("left_battery", 0),
                getBooleanExtra("left_charging", false),
                getBooleanExtra("left_connected", false),
                0
            ),
            right = PodParams(
                getIntExtra("right_battery", 0),
                getBooleanExtra("right_charging", false),
                getBooleanExtra("right_connected", false),
                0
            ),
            case = PodParams(
                getIntExtra("case_battery", 0),
                getBooleanExtra("case_charging", false),
                getBooleanExtra("case_connected", false),
                0
            )
        )
    }

    private fun saveState(ctx: Context?) {
        val prefs = (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val editor = prefs.edit()
            .putString("address", currentAddress)
            .putString("name", currentName)
            .putInt("anc", currentAnc)
            .putInt("left_battery", currentBattery.left?.battery ?: 0)
            .putBoolean("left_charging", currentBattery.left?.isCharging == true)
            .putBoolean("left_connected", currentBattery.left?.isConnected == true)
            .putInt("right_battery", currentBattery.right?.battery ?: 0)
            .putBoolean("right_charging", currentBattery.right?.isCharging == true)
            .putBoolean("right_connected", currentBattery.right?.isConnected == true)
            .putInt("case_battery", currentBattery.case?.battery ?: 0)
            .putBoolean("case_charging", currentBattery.case?.isCharging == true)
            .putBoolean("case_connected", currentBattery.case?.isConnected == true)
        currentAncSubMode?.let { editor.putInt(PREF_ANC_SUBMODE, it) }
            ?: editor.remove(PREF_ANC_SUBMODE)
        currentTransparencySubMode?.let { editor.putInt(PREF_TRANSPARENCY_SUBMODE, it) }
            ?: editor.remove(PREF_TRANSPARENCY_SUBMODE)
        editor.apply()
    }

    private fun loadState() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val hasPersistedIdentity = prefs.contains("address") || prefs.contains("name")
        val persistedAddress = prefs.getString("address", null)
        val persistedName = prefs.getString("name", null)
        if (hasPersistedIdentity && !resolveHuaweiDeviceRoute(persistedAddress, persistedName).isSupported) {
            currentAddress = null
            currentName = null
            currentBattery = BatteryParams()
            currentAnc = 1
            currentAncSubMode = null
            currentTransparencySubMode = null
            knownHuaweiAddresses.clear()
            prefs.edit()
                .remove("address")
                .remove("name")
                .remove("anc")
                .remove(PREF_ANC_SUBMODE)
                .remove(PREF_ANC_SUBMODE_LEGACY_6I)
                .remove(PREF_ANC_SUBMODE_LEGACY_PRO3)
                .remove(PREF_TRANSPARENCY_SUBMODE)
                .remove("left_battery")
                .remove("left_charging")
                .remove("left_connected")
                .remove("right_battery")
                .remove("right_charging")
                .remove("right_connected")
                .remove("case_battery")
                .remove("case_charging")
                .remove("case_connected")
                .apply()
            Log.i(TAG, "removed unsupported legacy headset state name=${persistedName.orEmpty()}")
            return
        }
        currentAddress = prefs.getString("address", currentAddress)
        currentName = prefs.getString("name", currentName)
        currentAnc = prefs.getInt("anc", currentAnc)
        val route = currentHuaweiRoute()
        val persistedAncSubMode = when {
            prefs.contains(PREF_ANC_SUBMODE) -> prefs.getInt(PREF_ANC_SUBMODE, -1)
            prefs.contains(PREF_ANC_SUBMODE_LEGACY_6I) -> prefs.getInt(PREF_ANC_SUBMODE_LEGACY_6I, -1)
            prefs.contains(PREF_ANC_SUBMODE_LEGACY_PRO3) -> prefs.getInt(PREF_ANC_SUBMODE_LEGACY_PRO3, -1)
            else -> -1
        }.takeIf { it >= 0 }
        val persistedTransparencySubMode = prefs.getInt(PREF_TRANSPARENCY_SUBMODE, -1)
            .takeIf { prefs.contains(PREF_TRANSPARENCY_SUBMODE) && it >= 0 }
        currentAncSubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.NOISE_CANCELLATION.broadcastStatus,
            persistedAncSubMode,
            storedSubMode = null,
        )
        currentTransparencySubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.TRANSPARENCY.broadcastStatus,
            persistedTransparencySubMode,
            storedSubMode = null,
        )
        if (!route.supportsAnc || currentAnc == 3 && !route.supportsTransparency) {
            currentAnc = NoiseControlMode.OFF.broadcastStatus
        }
        currentAddress?.let { knownHuaweiAddresses.add(it.uppercase()) }
        currentBattery = BatteryParams(
            left = PodParams(
                prefs.getInt("left_battery", currentBattery.left?.battery ?: 0),
                prefs.getBoolean("left_charging", currentBattery.left?.isCharging == true),
                prefs.getBoolean("left_connected", currentBattery.left?.isConnected == true),
                0
            ),
            right = PodParams(
                prefs.getInt("right_battery", currentBattery.right?.battery ?: 0),
                prefs.getBoolean("right_charging", currentBattery.right?.isCharging == true),
                prefs.getBoolean("right_connected", currentBattery.right?.isConnected == true),
                0
            ),
            case = PodParams(
                prefs.getInt("case_battery", currentBattery.case?.battery ?: 0),
                prefs.getBoolean("case_charging", currentBattery.case?.isCharging == true),
                prefs.getBoolean("case_connected", currentBattery.case?.isConnected == true),
                0
            )
        ).let(::normalizeBatteryAvailabilityForCurrentRoute)
    }

    private fun normalizeBatteryAvailabilityForCurrentRoute(status: BatteryParams): BatteryParams =
        if (currentHuaweiRoute().usesReportedEarbudAvailability) {
            status
        } else {
            status.normalizedEarbudAvailability()
        }
}
