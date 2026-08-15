package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.PodImageChangeNotifier
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.pods.FreeClip2BooleanFeature
import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerCodec
import moe.chenxy.huaweipods.pods.HuaweiAncLevel
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.UNKNOWN_HUAWEI_ANC_SUBMODE
import moe.chenxy.huaweipods.pods.HuaweiGestureController
import moe.chenxy.huaweipods.pods.HuaweiGestureKind
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.pods.HuaweiSwipeAction
import moe.chenxy.huaweipods.pods.HuaweiTapAction
import moe.chenxy.huaweipods.pods.ancLevelOptions
import moe.chenxy.huaweipods.pods.defaultAncSubMode
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.resolveHuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsAncDirectionDial
import moe.chenxy.huaweipods.pods.supportsAncStateReadback
import moe.chenxy.huaweipods.pods.supportsAncSubMode
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsGestureConfiguration
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.ui.components.SmartAudioEqualizerClient
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.ModuleResourceResolver
import moe.chenxy.huaweipods.utils.PodImageLoader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.WeakHashMap

@SuppressLint("MissingPermission")
object SettingsHeadsetHook : HookContext() {
    private data class SettingsHeaderImageKey(
        val address: String,
        val route: HuaweiDeviceRoute,
        val manualPath: String?,
        val cloudPath: String?,
    )

    private data class SettingsHeaderBitmapCache(
        val key: SettingsHeaderImageKey,
        val bitmap: Bitmap,
    )

    private data class HiddenSettingsCapabilityView(
        val visibility: Int,
        val isEnabled: Boolean,
        val isClickable: Boolean,
        val importantForAccessibility: Int,
        val layoutState: SettingsRowLayoutState,
        var layoutCollapsed: Boolean = false,
    )

    private const val TAG = "HuaweiPods-Settings"
    private const val PREFS_NAME = "huaweipods_milink_state"
    private const val PREF_DEVICE_ROUTE = "device_route"
    private const val SETTINGS_REFRESH_INTERVAL_MS = 5_000L
    private const val SETTINGS_SCROLL_IDLE_DELAY_MS = 160L
    private const val SETTINGS_FREEBUDS_ANC_OPTIONS = "0100"
    private const val SETTINGS_FREEBUDS_SUPPORT_FLAGS = "000000000000000010000000"
    private const val HUAWEI_ANC_LEVEL_LAST = 8
    private const val HUAWEI_ANC_DIAL_TICKS = 72
    private const val HUAWEI_ANC_TICKS_PER_LEVEL = 8
    private const val HUAWEI_ANC_DIAL_TICK_DEGREES = 5f
    private const val HUAWEI_ANC_DIAL_START_DEGREES = 70f
    private const val SETTINGS_HUAWEI_DIAL_TAG = "huaweipods_settings_anc_level_dial"
    private const val SETTINGS_HUAWEI_TRANSPARENCY_SELECTOR_TAG =
        "huaweipods_settings_transparency_selector"
    private const val SETTINGS_HUAWEI_ANC_SELECTOR_TAG = "huaweipods_settings_anc_selector"
    private const val SETTINGS_FREECLIP2_AUDIO_CATEGORY_KEY =
        "huaweipods_freeclip2_audio_category"
    private const val SETTINGS_FREECLIP2_SOUND_EFFECT_KEY =
        "huaweipods_freeclip2_sound_effect"
    private const val SETTINGS_FREECLIP2_EQUALIZER_ENTRY_KEY =
        "huaweipods_freeclip2_equalizer"
    private const val SETTINGS_FREECLIP2_EQUALIZER_BAND_KEY_PREFIX =
        "huaweipods_freeclip2_equalizer_band_"
    private const val SETTINGS_FREECLIP2_EQUALIZER_PAGE_ARGUMENT =
        "huaweipods_freeclip2_equalizer_page"
    private const val SETTINGS_FREECLIP2_SMART_CATEGORY_KEY =
        "huaweipods_freeclip2_smart_category"
    private const val SETTINGS_FREECLIP2_SOUND_CATEGORY_KEY =
        "huaweipods_freeclip2_sound_category"
    private const val SETTINGS_FREECLIP2_FEATURE_KEY_PREFIX =
        "huaweipods_freeclip2_feature_"
    private const val SETTINGS_FREECLIP2_SWIPE_LEFT_KEY =
        "huaweipods_freeclip2_swipe_left"
    private const val SETTINGS_FREECLIP2_SWIPE_RIGHT_KEY =
        "huaweipods_freeclip2_swipe_right"
    private const val HUAWEI_SMART_AUDIO_PACKAGE = "com.huawei.smartaudio"
    private val moreSettingsKeywords = listOf("更多设置", "More settings")
    private val gestureEntryKeywords = listOf(
        "手势操作",
        "手势控制",
        "Gesture control",
        "Gestures",
    )
    private val freeClip2SmartFeatures = listOf(
        FreeClip2BooleanFeature.WEAR_DETECTION,
        FreeClip2BooleanFeature.DROP_REMINDER,
        FreeClip2BooleanFeature.ADAPTIVE_VOLUME,
        FreeClip2BooleanFeature.HEAD_MOTION_CONTROL,
    )
    private val freeClip2SoundFeatures = listOf(
        FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY,
        FreeClip2BooleanFeature.LOW_LATENCY,
        FreeClip2BooleanFeature.DUAL_DEVICE,
        FreeClip2BooleanFeature.CASE_PROMPT_SOUND,
    )
    private val ancLevelKeywords = listOf("自适应", "智能", "轻度", "均衡", "深度", "Smart", "Adaptive", "Light", "Medium", "Deep")
    private val ancLevelAnchorKeywords = listOf("轻度", "均衡", "深度", "Light", "Medium", "Deep")
    private val ancModeKeywords = listOf("降噪", "通透", "关闭", "Noise", "Transparency", "Off")
    private val nativeGesturePreferenceKeys = listOf(
        "left_double",
        "right_double",
        "left_triple",
        "right_triple",
        SETTINGS_FREECLIP2_SWIPE_LEFT_KEY,
        SETTINGS_FREECLIP2_SWIPE_RIGHT_KEY,
        "long_press_left_headset",
        "long_press_right_headset",
    )
    private val knownHuaweiAddresses = linkedMapOf<String, HuaweiDeviceRoute>()
    private val batteryViews = WeakHashMap<Any, BluetoothDevice>()
    private val headsetFragments = WeakHashMap<Any, Boolean>()
    private val keyConfigFragments = WeakHashMap<Any, Boolean>()
    private val freeClip2EqualizerFragments = WeakHashMap<Any, Boolean>()
    private val gestureActionCache = linkedMapOf<String, HuaweiTapAction>()
    private val swipeActionCache = linkedMapOf<String, HuaweiSwipeAction>()
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentAddress: String? = null
    private var currentName: String? = null
    private var currentRoute: HuaweiDeviceRoute? = null
    private var currentBattery: BatteryParams = BatteryParams()
    private var currentAnc = 1
    private var currentAncConfirmed = false
    private var currentHuaweiAncLevel = UNKNOWN_HUAWEI_ANC_SUBMODE
    private var currentTransparencySubMode = 0x02
    private var currentFreeClip2AudioState = FreeClip2AudioUiState()
    private val currentFreeClip2FeatureStates = linkedMapOf<FreeClip2BooleanFeature, Boolean>()
    private val confirmedFreeClip2Features = linkedSetOf<FreeClip2BooleanFeature>()
    private val pendingFreeClip2Features = linkedSetOf<FreeClip2BooleanFeature>()
    private val failedFreeClip2Features = linkedSetOf<FreeClip2BooleanFeature>()
    private val freeClip2AudioPendingGate = FreeClip2AudioPendingGate()
    private var freeClip2EqualizerWriteGeneration = 0L
    private val settingsAncPendingGate = SettingsAncPendingGate()
    private var settingsAncInternalRenderDepth = 0
    private var proxyCheckSupportCalls = 0
    private var proxySetCommonCommandCalls = 0
    private var proxyGetDeviceConfigCalls = 0
    private var proxyGetCommonConfigCalls = 0
    private var settingsHeaderBitmapCache: SettingsHeaderBitmapCache? = null
    private val hiddenSettingsCapabilityViews = WeakHashMap<View, HiddenSettingsCapabilityView>()
    private val relabeledFreeBuds6iTransparencyTexts = WeakHashMap<TextView, CharSequence>()
    private val observedSettingsRoots = WeakHashMap<View, Boolean>()
    private val pendingSettingsScrollPrunes = WeakHashMap<View, Runnable>()
    private var huaweiSmartAudioLaunchIntent: Intent? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val freeClip2FeatureResultReceiver = object : ResultReceiver(refreshHandler) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            val data = resultData ?: return
            handleFreeClip2FeatureState(Intent().putExtras(data))
        }
    }
    private var refreshLoopStarted = false
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (headsetFragments.keys.any { isCurrentHuaweiFragment(it) }) {
                requestBluetoothStatus("settings-periodic")
                refreshHandler.postDelayed(this, SETTINGS_REFRESH_INTERVAL_MS)
            } else {
                refreshLoopStarted = false
                Log.d(TAG, "settings periodic refresh stopped: no active fragment")
            }
        }
    }

    override fun onHook() {
        hookActivityEntry()
        hookSupportChecks()
        hookServiceProxy()
        hookBatteryView()
        hookFragmentState()
    }

    private fun hookActivityEntry() {
        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetActivity", "onCreate", Bundle::class.java)) {
                val activity = instance as? Context ?: return@hookBefore
                registerStatusReceiver(activity)
                val intent = callMethod(instance, "getIntent") as? Intent ?: return@hookBefore
                val device = intent.parcelableDevice("android.bluetooth.device.extra.DEVICE")
                Log.d(TAG, "Activity.onCreate before device=${device.describe()} support=${intent.getStringExtra("MIUI_HEADSET_SUPPORT")} comeFrom=${intent.getStringExtra("COME_FROM")} btAddress=${intent.getStringExtra("bluetoothaddress")} known=$knownHuaweiAddresses current=$currentAddress")
                if (!isHuaweiPod(device)) return@hookBefore
                intent.putExtra("MIUI_HEADSET_SUPPORT", settingsSupport())
                intent.putExtra("COME_FROM", intent.getStringExtra("COME_FROM") ?: "MIUI_BLUETOOTH_SETTINGS")
                intent.putExtra("DEVICE_ID", fakeDeviceId())
                Log.d(TAG, "MiuiHeadsetActivity intent patched address=${device?.address}")
            }
            hookActivityStringGetter("getDeviceID") { fakeDeviceId() }
            hookActivityStringGetter("getSupport") { settingsSupport() }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetActivity skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetActivityPlugin", "onCreate", Bundle::class.java)) {
                val activity = instance as? Context ?: return@hookBefore
                registerStatusReceiver(activity)
                val intent = callMethod(instance, "getIntent") as? Intent ?: return@hookBefore
                val device = intent.parcelableDevice("android.bluetooth.device.extra.DEVICE")
                Log.d(TAG, "Plugin.onCreate before device=${device.describe()} support=${intent.getStringExtra("MIUI_HEADSET_SUPPORT")} comeFrom=${intent.getStringExtra("COME_FROM")} btAddress=${intent.getStringExtra("bluetoothaddress")} known=$knownHuaweiAddresses current=$currentAddress")
                if (!isHuaweiPod(device)) return@hookBefore
                intent.putExtra("MIUI_HEADSET_SUPPORT", settingsSupport())
                intent.putExtra("DEVICE_ID", fakeDeviceId())
                Log.d(TAG, "MiuiHeadsetActivityPlugin intent patched address=${device?.address}")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetActivityPlugin skipped", it) }
    }

    private fun hookActivityStringGetter(methodName: String, value: () -> String) {
        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetActivity", methodName, 0)) {
                val device = runCatching { getObjectField(instance, "mDevice") as? BluetoothDevice }.getOrNull()
                Log.d(TAG, "Activity.$methodName old=$result device=${device.describe()} isHuawei=${isHuaweiPod(device)}")
                if (!isHuaweiPod(device)) return@hookAfter
                result = value()
                Log.d(TAG, "Activity.$methodName forced=$result")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetActivity.$methodName skipped", it) }
    }

    private fun hookSupportChecks() {
        hookStringStaticResult("com.android.settings.bluetooth.HeadsetIDConstants", "checkSupport") { support ->
            support.startsWith(fakeDeviceId()) || support.contains(fakeDeviceId())
        }
        hookStringStaticResult("com.android.settings.bluetooth.HeadsetIDConstants", "isTWS01Headset") { it == fakeDeviceId() }
        hookStringStaticResult("com.android.settings.bluetooth.HeadsetIDConstants", "isK77sHeadset") { false }
        hookBleMmaConnectByContext()
        hookBleMmaConnectByService()
    }

    private fun hookStringStaticResult(className: String, methodName: String, resultForValue: (String) -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, String::class.java)) {
                val value = args[0] as? String ?: return@hookAfter
                Log.d(TAG, "$className.$methodName value=$value old=$result")
                val deviceId = fakeDeviceId()
                if (value != deviceId && !value.startsWith(deviceId)) return@hookAfter
                result = resultForValue(value)
                Log.d(TAG, "$className.$methodName forced value=$value result=$result")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(String) skipped", it) }
    }

    private fun hookBleMmaConnectByContext() {
        runCatching {
            hookAfter(findMethod("com.android.settings.bluetooth.HeadsetIDConstants", "isBleMmaConnect", Context::class.java, BluetoothDevice::class.java, String::class.java)) {
                val device = args[1] as? BluetoothDevice
                val deviceId = args[2] as? String
                Log.d(TAG, "isBleMmaConnect(Context) old=$result device=${device.describe()} deviceId=$deviceId service=${runCatching { callMethod(args[0], "getService") }.getOrNull()}")
                if (deviceId == fakeDeviceId() || isHuaweiPod(device)) {
                    result = true
                    Log.d(TAG, "isBleMmaConnect(Context) forced true")
                }
            }
        }.onFailure { Log.w(TAG, "hook HeadsetIDConstants.isBleMmaConnect(Context) skipped", it) }
    }

    private fun hookBleMmaConnectByService() {
        runCatching {
            val serviceClass = findClass("com.android.bluetooth.ble.app.IMiuiHeadsetService")
            hookAfter(findMethod("com.android.settings.bluetooth.HeadsetIDConstants", "isBleMmaConnect", serviceClass, BluetoothDevice::class.java, String::class.java)) {
                val device = args[1] as? BluetoothDevice
                val deviceId = args[2] as? String
                Log.d(TAG, "isBleMmaConnect(Service) old=$result service=${args[0]} device=${device.describe()} deviceId=$deviceId")
                if (deviceId == fakeDeviceId() || isHuaweiPod(device)) {
                    result = true
                    Log.d(TAG, "isBleMmaConnect(Service) forced true")
                }
            }
        }.onFailure { Log.w(TAG, "hook HeadsetIDConstants.isBleMmaConnect(Service) skipped", it) }
    }

    private fun hookServiceProxy() {
        val proxyClass = "com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub\$Proxy"
        hookProxyStringResult(proxyClass, "checkSupport", BluetoothDevice::class.java) { settingsSupport() }
        hookProxyStringArgResult(proxyClass, "getDeviceInfo") { settingsSupport() }
        hookProxyStringArgResult(proxyClass, "isSupportAudioSwitch") { "1" }
        hookProxyStringArgResult(proxyClass, "setCommonCommand", Int::class.java, String::class.java, BluetoothDevice::class.java) { commandArgs ->
            val command = commandArgs[0] as? Int
            if (command == 102) "0" else "1"
        }
        hookProxyVoidDeviceNoop(proxyClass, "connect", BluetoothDevice::class.java)
        hookProxyVoidDeviceNoop(proxyClass, "getDeviceConfig", BluetoothDevice::class.java)
        hookProxyVoidDeviceStringNoop(proxyClass, "getCommonConfig", BluetoothDevice::class.java, String::class.java)
        hookProxyBooleanStringResult(proxyClass, "isMiTWS") { true }
        hookProxyBooleanStringResult(proxyClass, "checkIsMiTWS") { true }
        hookProxyBooleanStringResult(proxyClass, "getRingFindState") { false }
        hookProxyVoidDeviceCommand(proxyClass, "changeAncMode", Int::class.java, BluetoothDevice::class.java) { commandArgs ->
            val miMode = commandArgs[0] as? Int ?: return@hookProxyVoidDeviceCommand null
            huaweiAncFromSettings(miMode)
        }
        hookProxyVoidDeviceCommand(proxyClass, "changeAncLevel", String::class.java, BluetoothDevice::class.java) { commandArgs ->
            val level = commandArgs[0] as? String ?: return@hookProxyVoidDeviceCommand null
            huaweiAncFromLevelCommand(level)
        }
    }

    private fun hookProxyStringResult(className: String, methodName: String, vararg parameterTypes: Class<*>, result: () -> String) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                val isHuawei = isHuaweiPod(device)
                if (methodName == "checkSupport") proxyCheckSupportCalls++
                Log.d(TAG, "$methodName proxy call#${if (methodName == "checkSupport") proxyCheckSupportCalls else -1} device=${device.describe()} isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                this.result = result()
                Log.d(TAG, "$methodName proxy forced result=${this.result} address=${device?.address}")
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyStringArgResult(className: String, methodName: String, vararg parameterTypes: Class<*>, result: (List<Any?>) -> String) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                val address = args.firstOrNull { it is String } as? String
                val isHuawei = isHuaweiPod(device) || (address != null && isHuaweiAddress(address))
                if (methodName == "setCommonCommand") proxySetCommonCommandCalls++
                Log.d(TAG, "$methodName proxy call#${if (methodName == "setCommonCommand") proxySetCommonCommandCalls else -1} args=${args.describeArgs()} device=${device.describe()} addressArg=$address isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                // 通知栏开关交给蓝牙服务处理。
                if (methodName == "setCommonCommand" && (args[0] == 114 || args[0] == 115)) {
                    return@hookBefore
                }
                this.result = result(args)
                Log.d(TAG, "$methodName proxy forced result=${this.result} address=${device?.address ?: address}")
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyBooleanStringResult(className: String, methodName: String, result: () -> Boolean) {
        runCatching {
            hookBefore(findMethod(className, methodName, String::class.java)) {
                val address = args[0] as? String ?: return@hookBefore
                val isHuawei = isHuaweiAddress(address)
                Log.d(TAG, "$methodName proxy string call address=$address isHuawei=$isHuawei oldKnown=$knownHuaweiAddresses current=$currentAddress")
                if (!isHuawei) return@hookBefore
                this.result = result()
                Log.d(TAG, "$methodName proxy forced result=${this.result} address=$address")
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyVoidDeviceCommand(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
        mode: (List<Any?>) -> SettingsAncSelection?,
    ) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                Log.d(TAG, "$methodName proxy command args=${args.describeArgs()} device=${device.describe()} isHuawei=${isHuaweiPod(device)}")
                if (!isHuaweiPod(device)) return@hookBefore
                if (!shouldDispatchSettingsAncCommand(settingsAncInternalRenderDepth)) {
                    this.result = null
                    Log.d(TAG, "$methodName proxy command swallowed during internal Settings render")
                    return@hookBefore
                }
                val selection = mode(args)
                if (selection == null) {
                    this.result = null
                    Log.w(TAG, "$methodName proxy command swallowed unsupported Huawei mapping args=${args.describeArgs()}")
                    return@hookBefore
                }
                val dispatched = dispatchAncSelection(selection)
                this.result = null
                Log.i(
                    TAG,
                    "$methodName proxy command handled address=${device?.address} " +
                        "selection=$selection dispatched=$dispatched",
                )
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyVoidDeviceNoop(className: String, methodName: String, vararg parameterTypes: Class<*>) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                if (methodName == "getDeviceConfig") proxyGetDeviceConfigCalls++
                val isHuawei = isHuaweiPod(device)
                Log.d(TAG, "$methodName proxy before#${if (methodName == "getDeviceConfig") proxyGetDeviceConfigCalls else -1} device=${device.describe()} isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                this.result = null
                Log.d(TAG, "$methodName proxy swallowed for virtual Huawei device")
            }
        }.onFailure { Log.w(TAG, "hook proxy noop $methodName skipped", it) }
    }

    private fun hookProxyVoidDeviceStringNoop(className: String, methodName: String, vararg parameterTypes: Class<*>) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                proxyGetCommonConfigCalls++
                val isHuawei = isHuaweiPod(device)
                Log.d(TAG, "$methodName proxy before#$proxyGetCommonConfigCalls args=${args.describeArgs()} device=${device.describe()} isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                this.result = null
                Log.d(TAG, "$methodName proxy swallowed for virtual Huawei device")
            }
        }.onFailure { Log.w(TAG, "hook proxy noop $methodName skipped", it) }
    }

    private fun hookBatteryView() {
        runCatching {
            hookConstructorAfter(findConstructorByParamCount("com.android.settings.bluetooth.tws.MiuiHeadsetBattery", 4)) {
                val device = args[0] as? BluetoothDevice ?: return@hookConstructorAfter
                val ctx = args[1] as? Context
                registerStatusReceiver(ctx)
                Log.d(TAG, "Battery.<init> device=${device.describe()} isHuawei=${isHuaweiPod(device)} ctx=$ctx currentBattery=${settingsBatteryString()}")
                if (!isHuaweiPod(device)) return@hookConstructorAfter
                batteryViews[instance ?: return@hookConstructorAfter] = device
                requestBluetoothStatus("battery-init")
                updateBatteryView(instance)
                Log.d(TAG, "MiuiHeadsetBattery registered address=${device.address}")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetBattery constructor skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.tws.MiuiHeadsetBattery", "onBatteryChanged", String::class.java)) {
                val device = batteryViews[instance]
                val isCurrentHuawei = isCurrentHuaweiDevice(device)
                Log.d(TAG, "Battery.onBatteryChanged(String) original=${args[0]} mappedDevice=${device.describe()} isCurrentHuawei=$isCurrentHuawei forced=${settingsBatteryString()}")
                if (!isCurrentHuawei) return@hookBefore
                result = null
                updateBatteryView(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetBattery.onBatteryChanged(String) skipped", it) }
    }

    private fun hookFragmentState() {
        runCatching {
            hookAfter(
                findMethod(
                    "com.android.settings.bluetooth.MiuiHeadsetFragment",
                    "onCreate",
                    Bundle::class.java,
                ),
            ) {
                if (!isHuaweiFragment(instance)) return@hookAfter
                instance?.let { headsetFragments[it] = true }
                installFreeClip2AudioPreferences(instance)
                installFreeClip2FeaturePreferences(instance)
                orderFreeClip2SettingsPreferences(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.onCreate skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetFragment", "onCreateView", 3)) {
                registerStatusReceiver(runCatching { getObjectField(instance, "mActivity") as? Context }.getOrNull())
                Log.d(TAG, "Fragment.onCreateView after ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                instance?.let { headsetFragments[it] = true }
                installFreeClip2AudioPreferences(instance)
                installFreeClip2FeaturePreferences(instance)
                orderFreeClip2SettingsPreferences(instance)
                schedulePruneFreeBudsUnsupportedViews(result as? View)
                requestBluetoothStatus("fragment-create")
                startPeriodicRefresh()
                injectFragmentStatus(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.onCreateView skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetFragment", "onResume", 0)) {
                Log.d(TAG, "Fragment.onResume after ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                installFreeClip2AudioPreferences(instance)
                orderFreeClip2SettingsPreferences(instance)
                schedulePruneFreeBudsUnsupportedViews(fragmentRootView(instance))
                injectFragmentStatus(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.onResume skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetFragment", "onServiceConnected", 0)) {
                Log.d(TAG, "Fragment.onServiceConnected after ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                instance?.let { headsetFragments[it] = true }
                requestBluetoothStatus("service-connected")
                startPeriodicRefresh()
                injectFragmentStatus(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.onServiceConnected skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", "refreshStatus", String::class.java, String::class.java)) {
                val key = args[0] as? String
                val data = args[1] as? String
                Log.d(TAG, "Fragment.refreshStatus before key=$key data=$data ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (isHuaweiFragment(instance) && key?.startsWith("MMA_CONNECTION_FAILED") == true) {
                    Log.w(TAG, "Fragment.refreshStatus swallowed MMA failure for virtual Huawei device key=$key")
                    injectFragmentStatus(instance)
                    result = null
                }
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.refreshStatus skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", "handleConnectMmaFailed", String::class.java)) {
                Log.w(TAG, "Fragment.handleConnectMmaFailed arg=${args[0]} ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (isHuaweiFragment(instance)) {
                    injectFragmentStatus(instance)
                    result = null
                    Log.w(TAG, "Fragment.handleConnectMmaFailed swallowed for virtual Huawei device")
                }
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.handleConnectMmaFailed skipped", it) }

        hookFragmentAncCommand("updateAncMode", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!) { commandArgs ->
            huaweiAncFromSettings(commandArgs[0] as? Int ?: 0)
        }
        hookFragmentAncCommand("updateAncLevel", String::class.java, Boolean::class.javaPrimitiveType!!) { commandArgs ->
            val level = commandArgs[0] as? String ?: ""
            huaweiAncFromLevelCommand(level)
        }
        hookFreeClip2SpatialSceneChange()
        hookFragmentAncUiRender()
        hookKeyConfigFragmentState()
    }

    private fun hookFreeClip2SpatialSceneChange() {
        runCatching {
            hookBefore(
                findMethod(
                    "com.android.settings.bluetooth.MiuiHeadsetFragment",
                    "onAncLevelChange",
                    Int::class.javaPrimitiveType!!,
                ),
            ) {
                if (!isHuaweiFragment(instance) ||
                    currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2
                ) {
                    return@hookBefore
                }
                val scene = FreeClip2SpatialScene.entries.getOrNull(args[0] as? Int ?: -1)
                    ?: return@hookBefore
                if (currentFreeClip2AudioState.spatialScene != scene) {
                    onFreeClip2AudioSelected(
                        HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE,
                        scene.extraValue,
                    )
                }
                result = null
            }
        }.onFailure { Log.w(TAG, "hook FreeClip 2 native spatial scene selector skipped", it) }
    }

    private fun hookKeyConfigFragmentState() {
        hookNativeKeyConfigPreferenceChange()

        runCatching {
            hookBefore(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment", "onCreate", 1)) {
                isFreeClip2EqualizerPage(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetKeyConfigFragment.onCreate skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment", "onCreateView", 3)) {
                Log.d(TAG, "MiuiHeadsetKeyConfigFragment.onCreateView after isHuawei=${isHuaweiKeyConfigFragment(instance)}")
                if (!isHuaweiKeyConfigFragment(instance)) return@hookAfter
                if (isFreeClip2EqualizerPage(instance)) {
                    configureFreeClip2EqualizerPage(instance)
                    requestFreeClip2AudioState("equalizer-create")
                    return@hookAfter
                }
                configureFreeBudsNativeGesturePage(instance)
                requestHuaweiGestureState(instance, "onCreateView")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetKeyConfigFragment.onCreateView skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment", "onResume", 0)) {
                Log.d(TAG, "MiuiHeadsetKeyConfigFragment.onResume after isHuawei=${isHuaweiKeyConfigFragment(instance)}")
                if (!isHuaweiKeyConfigFragment(instance)) return@hookAfter
                if (isFreeClip2EqualizerPage(instance)) {
                    configureFreeClip2EqualizerPage(instance)
                    requestFreeClip2AudioState("equalizer-resume")
                    return@hookAfter
                }
                configureFreeBudsNativeGesturePage(instance)
                requestHuaweiGestureState(instance, "onResume")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetKeyConfigFragment.onResume skipped", it) }
    }

    private fun hookNativeKeyConfigPreferenceChange() {
        var hookedListeners = 0
        (1..8).forEach { suffix ->
            runCatching {
                hookBefore(
                    findMethod(
                        "com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment\$$suffix",
                        "onPreferenceChange",
                        findClass("androidx.preference.Preference"),
                        Any::class.java,
                    ),
                ) {
                    if (handleNativeGesturePreferenceChange(instance, args.getOrNull(0), args.getOrNull(1))) {
                        result = true
                    }
                }
                hookedListeners++
            }
        }
        if (hookedListeners == 0) {
            Log.w(TAG, "hook MiuiHeadsetKeyConfigFragment.onPreferenceChange skipped: no listener found")
        } else {
            Log.d(TAG, "hooked MiuiHeadsetKeyConfigFragment preference listeners=$hookedListeners")
        }
    }

    private fun handleNativeGesturePreferenceChange(listener: Any?, preference: Any?, newValue: Any?): Boolean {
        val fragment = runCatching { getObjectField(listener, "this$0") }.getOrNull()
        return handleNativeGesturePreferenceValueChange(fragment, preference, newValue)
    }

    private fun nativeGesturePreferenceListener(fragment: Any): Any {
        val listenerClass = findClass("androidx.preference.Preference\$OnPreferenceChangeListener")
        val fragmentReference = WeakReference(fragment)
        return Proxy.newProxyInstance(appClassLoader, arrayOf(listenerClass)) { proxy, method, arguments ->
            when (method.name) {
                "onPreferenceChange" -> handleNativeGesturePreferenceValueChange(
                    fragmentReference.get(),
                    arguments?.getOrNull(0),
                    arguments?.getOrNull(1),
                )
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "HuaweiPodsNativeGesturePreferenceListener"
                else -> null
            }
        }
    }

    private fun handleNativeGesturePreferenceValueChange(
        fragment: Any?,
        preference: Any?,
        newValue: Any?,
    ): Boolean {
        if (!isHuaweiKeyConfigFragment(fragment)) return false
        val route = gestureDeviceRoute(fragment)
        if (!route.supportsGestureConfiguration) return false
        val key = runCatching { callCompatibleMethod(preference, "getKey") as? String }.getOrNull()
        val targetAddress = gestureDeviceAddress(fragment).orEmpty().ifBlank { currentAddress.orEmpty() }
        val targetContext = runCatching { callCompatibleMethod(preference, "getContext") as? Context }.getOrNull()
            ?: context
            ?: return false
        val nativeLongPressKind = if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            HuaweiGestureKind.LONG_PRESS
        } else {
            HuaweiGestureKind.SWIPE
        }
        val (kind, side) = when (key) {
            "left_double" -> HuaweiGestureKind.DOUBLE_TAP to HuaweiGestureSide.LEFT
            "right_double" -> HuaweiGestureKind.DOUBLE_TAP to HuaweiGestureSide.RIGHT
            "left_triple" -> HuaweiGestureKind.TRIPLE_TAP to HuaweiGestureSide.LEFT
            "right_triple" -> HuaweiGestureKind.TRIPLE_TAP to HuaweiGestureSide.RIGHT
            SETTINGS_FREECLIP2_SWIPE_LEFT_KEY -> HuaweiGestureKind.SWIPE to HuaweiGestureSide.LEFT
            SETTINGS_FREECLIP2_SWIPE_RIGHT_KEY -> HuaweiGestureKind.SWIPE to HuaweiGestureSide.RIGHT
            "long_press_left_headset" -> nativeLongPressKind to HuaweiGestureSide.LEFT
            "long_press_right_headset" -> nativeLongPressKind to HuaweiGestureSide.RIGHT
            else -> return false
        }
        if (kind == HuaweiGestureKind.SWIPE) {
            val action = HuaweiSwipeAction.fromExtra(newValue?.toString())
                ?: newValue?.toString()?.toIntOrNull()?.let { HuaweiSwipeAction.fromProtocolValue(route, it) }
                ?: return false
            if (action !in HuaweiSwipeAction.availableFor(route)) return false
            cacheSwipeAction(targetAddress, side, action)
            runCatching { callCompatibleMethod(preference, "setValue", action.extraValue) }
            sendHuaweiGestureFromSettings(targetContext, targetAddress, kind, side, action.extraValue)
            Log.i(
                TAG,
                "native key config gesture handled kind=${kind.extraValue} side=${side.extraValue} action=${action.extraValue} address=$targetAddress",
            )
            return true
        }
        val action = HuaweiTapAction.fromExtra(newValue?.toString())
            ?: newValue?.toString()?.toIntOrNull()?.let { HuaweiTapAction.fromProtocolValue(route, kind, it) }
            ?: return false
        if (action !in HuaweiTapAction.availableFor(route, kind)) return false
        cacheGestureAction(targetAddress, kind, side, route, action)
        runCatching { callCompatibleMethod(preference, "setValue", action.extraValue) }
        sendHuaweiGestureFromSettings(targetContext, targetAddress, kind, side, action.extraValue)
        Log.i(
            TAG,
            "native key config gesture handled kind=${kind.extraValue} side=${side.extraValue} action=${action.extraValue} address=$targetAddress",
        )
        return true
    }

    private fun hookFragmentAncUiRender() {
        runCatching {
            hookAfter(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", "updateAncUi", String::class.java, Boolean::class.javaPrimitiveType!!)) {
                Log.d(TAG, "MiuiHeadsetFragment.updateAncUi after args=${args.describeArgs()} ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                fragmentRootView(instance)?.let { root ->
                    runCatching { pruneFreeBudsUnsupportedViews(root) }
                        .onFailure { Log.w(TAG, "Settings prune after updateAncUi failed", it) }
                }
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.updateAncUi skipped", it) }
    }

    private fun hookFragmentAncCommand(
        methodName: String,
        vararg parameterTypes: Class<*>,
        mode: (List<Any?>) -> SettingsAncSelection?,
    ) {
        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", methodName, *parameterTypes)) {
                Log.d(TAG, "MiuiHeadsetFragment.$methodName before args=${args.describeArgs()} ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookBefore
                if (!shouldDispatchSettingsAncCommand(settingsAncInternalRenderDepth)) {
                    Log.d(TAG, "MiuiHeadsetFragment.$methodName ignored during internal Settings render")
                    return@hookBefore
                }
                val selection = mode(args)
                if (selection == null) {
                    result = null
                    injectFragmentStatus(instance)
                    Log.w(TAG, "MiuiHeadsetFragment.$methodName swallowed unsupported Huawei mapping args=${args.describeArgs()}")
                    return@hookBefore
                }
                val dispatched = dispatchAncSelection(selection)
                withInternalSettingsAncRender {
                    runCatching { callMethod(instance, "updateAncUi", settingsAncLevel(), false) }
                }
                injectFragmentStatus(instance)
                result = null
                Log.i(TAG, "MiuiHeadsetFragment.$methodName handled selection=$selection dispatched=$dispatched")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.$methodName skipped", it) }
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        loadState()
        val filter = IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedIntent = intent ?: return
                when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                    HuaweiPodsAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED -> {
                        val imageAddress = receivedIntent.getStringExtra(PodImageChangeNotifier.EXTRA_ADDRESS)
                        if (imageAddress.isNullOrBlank() || imageAddress.equals(currentAddress, ignoreCase = true)) {
                            settingsHeaderBitmapCache = null
                            updateFragments()
                            Log.i(TAG, "Settings headset image invalidated address=${imageAddress.orEmpty()}")
                        }
                    }
                    HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentAncConfirmed = false
                        saveState(context)
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> {
                        if (!targetsCurrentHuaweiDevice(receivedIntent)) return
                        val disconnectedAddress = receivedIntent.getStringExtra("address")
                        clearBatteryViews(disconnectedAddress)
                        forgetHeadsetFragments(disconnectedAddress)
                        clearCurrentHuaweiDevice(context)
                    }
                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentBattery = receivedIntent.batteryStatusFromExtras()
                            ?: receivedIntent.parcelableStatus()
                            ?: currentBattery
                        saveState(context)
                        updateBatteryViews()
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        val status = receivedIntent.getIntExtra("status", currentAnc)
                        if (status in 1..3) {
                            val selection = SettingsAncSelection(
                                status = status,
                                subMode = receivedIntent.getIntExtra("submode", -1).takeIf { it >= 0 },
                            )
                            if (!settingsAncPendingGate.shouldAcceptConfirmation(
                                    confirmed = selection,
                                    nowMs = SystemClock.elapsedRealtime(),
                                )
                            ) {
                                Log.i(TAG, "Deferred stale Settings ANC confirmation selection=$selection")
                                return
                            }
                            applyAncSelection(selection)
                            currentAncConfirmed = true
                        }
                        saveState(context)
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        if (settingsAncPendingGate.hasPending(SystemClock.elapsedRealtime())) {
                            Log.d(TAG, "Deferred Settings ANC level while mode confirmation is pending")
                            return
                        }
                        val route = currentHuaweiRoute()
                        val level = receivedIntent.getIntExtra("level", currentHuaweiAncLevel)
                        currentHuaweiAncLevel = if (route.supportsDiscreteAncLevels) {
                            level.takeIf(route::supportsAncSubMode) ?: currentHuaweiAncLevel
                        } else {
                            level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
                        }
                        saveState(context)
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_GESTURE_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        runCatching {
                            cacheGestureState(
                                intent = receivedIntent,
                                route = currentHuaweiRoute(),
                                address = currentAddress.orEmpty(),
                            )
                            updateGestureFragments()
                        }.onFailure {
                            Log.w(TAG, "Huawei gesture state update failed without affecting Settings", it)
                        }
                    }
                    HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED -> {
                        if (!receivedIntent.getBooleanExtra(
                                HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_CONFIRMED,
                                false,
                            )
                        ) {
                            return
                        }
                        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
                            !targetsCurrentHuaweiDevice(receivedIntent)
                        ) {
                            return
                        }
                        if (!rememberSupportedDevice(receivedIntent)) return
                        val spatialModeValue = receivedIntent.getStringExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_MODE,
                        )
                        val spatialSceneValue = receivedIntent.getStringExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_SCENE,
                        )
                        val soundEffectValue = receivedIntent.getStringExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_SOUND_EFFECT,
                        )
                        val equalizerPresetId = receivedIntent.takeIf {
                            it.hasExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID)
                        }?.getIntExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID,
                            -1,
                        )?.takeIf { it in 0x64..0x66 }
                        val equalizerGains = receivedIntent.getIntArrayExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS,
                        )?.toList()?.takeIf(::isValidFreeClip2EqualizerGains)
                        currentFreeClip2AudioState = currentFreeClip2AudioState.mergeExtraValues(
                            spatialModeValue = spatialModeValue,
                            spatialSceneValue = spatialSceneValue,
                            soundEffectValue = soundEffectValue,
                            equalizerPresetIdValue = equalizerPresetId,
                            equalizerNameValue = equalizerPresetId?.let {
                                receivedIntent.getStringExtra(
                                    HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME,
                                )
                            },
                            equalizerGainsValue = equalizerGains,
                        )
                        freeClip2AudioPendingGate.observeConfirmed(
                            spatialModeValue,
                            spatialSceneValue,
                            soundEffectValue,
                        )
                        saveCurrentFreeClip2AudioState(context)
                        updateFragments()
                    }
                }
                Log.d(TAG, "state action=${receivedIntent.action} address=$currentAddress anc=$currentAnc battery=${settingsBatteryString()}")
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        requestBluetoothStatus("receiver-register")
        Log.d(TAG, "registered status receiver context=$context")
    }

    private fun handleFreeClip2FeatureState(receivedIntent: Intent) {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
            !targetsCurrentHuaweiDevice(receivedIntent)
        ) {
            return
        }
        if (!rememberSupportedDevice(receivedIntent)) return
        val feature = FreeClip2BooleanFeature.fromExtraValue(
            receivedIntent.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE),
        ) ?: return
        pendingFreeClip2Features.remove(feature)
        val success = receivedIntent.getBooleanExtra(
            HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE_SUCCESS,
            false,
        )
        if (receivedIntent.hasExtra(HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE_ENABLED)) {
            currentFreeClip2FeatureStates[feature] = receivedIntent.getBooleanExtra(
                HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE_ENABLED,
                false,
            )
            confirmedFreeClip2Features.add(feature)
            saveCurrentFreeClip2FeatureState(context, feature)
            if (success) {
                failedFreeClip2Features.remove(feature)
            } else {
                failedFreeClip2Features.add(feature)
            }
        } else {
            failedFreeClip2Features.add(feature)
        }
        updateFreeClip2FeaturePreferences()
    }

    private fun requestBluetoothStatus(reason: String) {
        val ctx = context ?: return
        listOf(HuaweiPodsAction.ACTION_PODS_UI_INIT, HuaweiPodsAction.ACTION_REFRESH_STATUS).forEach { action ->
            ctx.sendBroadcast(Intent(action).apply {
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
        requestFreeClip2AudioState(reason)
        requestFreeClip2FeatureStates(reason)
        Log.d(TAG, "requested bluetooth status reason=$reason")
    }

    private fun requestFreeClip2AudioState(reason: String) {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val ctx = context ?: return
        val address = currentAddress?.takeIf(String::isNotBlank) ?: run {
            Log.w(TAG, "FreeClip 2 audio refresh skipped: missing address reason=$reason")
            return
        }
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_REFRESH).apply {
            putExtra("address", address)
            putExtra("device_name", currentName.orEmpty())
            encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.HUAWEI_FREECLIP2)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "FreeClip 2 audio state requested reason=$reason address=$address")
    }

    private fun requestFreeClip2FeatureStates(reason: String) {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        if (reason == "settings-periodic" &&
            confirmedFreeClip2Features.size == FreeClip2BooleanFeature.entries.size
        ) {
            return
        }
        val ctx = context ?: return
        val address = currentAddress?.takeIf(String::isNotBlank) ?: run {
            Log.w(TAG, "FreeClip 2 feature refresh skipped: missing address reason=$reason")
            return
        }
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_FEATURE_REFRESH).apply {
            putExtra("address", address)
            putExtra("device_name", currentName.orEmpty())
            encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.HUAWEI_FREECLIP2)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra(
                HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE_RESULT_RECEIVER,
                freeClip2FeatureResultReceiver,
            )
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "FreeClip 2 feature states requested reason=$reason address=$address")
    }

    private fun startPeriodicRefresh() {
        if (refreshLoopStarted) return
        refreshLoopStarted = true
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.postDelayed(refreshRunnable, SETTINGS_REFRESH_INTERVAL_MS)
        Log.d(TAG, "settings periodic refresh started")
    }

    private fun updateBatteryViews() {
        batteryViews.entries.toList().forEach { (view, device) ->
            if (!isCurrentHuaweiDevice(device)) return@forEach
            runCatching { updateBatteryView(view) }
                .onFailure { Log.w(TAG, "update battery view failed", it) }
        }
    }

    private fun updateBatteryView(view: Any?) {
        val values = settingsBatteryValues()
        callMethod(view, "onBatteryChanged", values[0], values[1], values[2])
        Log.d(TAG, "Battery.onBatteryChanged(int,int,int) forced=${values.joinToString(",")}")
    }

    private fun updateFragments() {
        headsetFragments.keys.toList().forEach { fragment ->
            if (isCurrentHuaweiFragment(fragment)) {
                renderFreeClip2AudioPreferences(fragment)
                injectFragmentStatus(fragment)
            }
        }
        updateFreeClip2EqualizerFragments()
    }

    private fun clearBatteryViews(address: String?) {
        val targetAddress = address?.takeIf(String::isNotBlank) ?: return
        batteryViews.entries.toList().forEach { (view, device) ->
            val viewAddress = runCatching { device.address }.getOrNull()
            if (!viewAddress.equals(targetAddress, ignoreCase = true)) return@forEach
            runCatching { callMethod(view, "onBatteryChanged", 255, 255, 255) }
                .onFailure { Log.w(TAG, "clear disconnected battery view failed", it) }
        }
    }

    private fun injectFragmentStatus(fragment: Any?) {
        runCatching {
            val route = currentHuaweiRoute()
            if (route.supportsAncStateReadback && !currentAncConfirmed) {
                schedulePruneFreeBudsUnsupportedViews(fragmentRootView(fragment))
                Log.d(TAG, "fragment status deferred until Huawei ANC state is confirmed route=$route")
                return@runCatching
            }
            val payload = "${settingsAncMode()}|$SETTINGS_FREEBUDS_ANC_OPTIONS|${settingsBatteryString()}|00"
            Log.d(TAG, "injectFragmentStatus payload=$payload ${fragmentDebug(fragment)}")
            withInternalSettingsAncRender {
                callMethod(fragment, "updateAtUiInfo", payload)
                if (shouldUpdateSettingsAncUi(route)) {
                    callMethod(fragment, "updateAncUi", settingsAncLevel(), false)
                }
            }
            schedulePruneFreeBudsUnsupportedViews(fragmentRootView(fragment))
            val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
            val address = device?.address
            if (address != null) {
                val refreshPayload = settingsRefreshPayload()
                Log.d(TAG, "injectFragmentStatus refreshPayload=$refreshPayload address=$address")
                withInternalSettingsAncRender {
                    callMethod(fragment, "refreshStatus", address, refreshPayload)
                }
            }
            Log.d(TAG, "fragment status injected anc=$currentAnc battery=${settingsBatteryString()}")
        }.onFailure { Log.w(TAG, "inject fragment status failed", it) }
    }

    private fun isHuaweiFragment(fragment: Any?): Boolean {
        return isHuaweiPod(fragmentBluetoothDevice(fragment))
    }

    private fun isCurrentHuaweiFragment(fragment: Any?): Boolean {
        return isCurrentHuaweiDevice(fragmentBluetoothDevice(fragment))
    }

    private fun isCurrentHuaweiDevice(device: BluetoothDevice?): Boolean {
        device ?: return false
        val address = runCatching { device.address }.getOrNull()?.takeIf(String::isNotBlank) ?: return false
        val activeAddress = currentAddress?.takeIf(String::isNotBlank) ?: return false
        if (!address.equals(activeAddress, ignoreCase = true)) return false
        val route = device.huaweiDeviceRoute().takeIf { it.isSupported }
            ?: knownHuaweiAddresses[address.uppercase()]?.takeIf { it.isSupported }
            ?: return false
        return route == currentHuaweiRoute()
    }

    private fun isHuaweiKeyConfigFragment(fragment: Any?): Boolean {
        return isHuaweiPod(fragmentBluetoothDevice(fragment))
    }

    private fun fragmentBluetoothDevice(fragment: Any?): BluetoothDevice? {
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        if (device != null) return device
        val args = runCatching { callMethod(fragment, "getArguments") as? Bundle }.getOrNull()
        return args?.parcelableDevice("BT_Device")
    }

    private fun isHuaweiPod(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching { device.name ?: device.alias }.getOrNull()
        val route = device.huaweiDeviceRoute().takeIf { it.isSupported }
            ?: address?.uppercase()?.let(knownHuaweiAddresses::get)?.takeIf { it.isSupported }
            ?: return false
        activateCurrentHuaweiDevice(address, name, route)
        return true
    }

    private fun BluetoothDevice?.describe(): String {
        if (this == null) return "null"
        val address = runCatching { this.address }.getOrNull()
        val name = runCatching { this.name }.getOrNull()
        val alias = runCatching { this.alias }.getOrNull()
        return "BluetoothDevice(address=$address,name=$name,alias=$alias)"
    }

    private fun List<Any?>.describeArgs(): String {
        return joinToString(prefix = "[", postfix = "]") { arg ->
            when (arg) {
                is BluetoothDevice -> arg.describe()
                else -> arg?.toString() ?: "null"
            }
        }
    }

    private fun fragmentDebug(fragment: Any?): String {
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        val deviceId = runCatching { getObjectField(fragment, "mDeviceId") as? String }.getOrNull()
        val support = runCatching { getObjectField(fragment, "mSupport") as? String }.getOrNull()
        val service = runCatching { getObjectField(fragment, "mService") }.getOrNull()
        val hfp = runCatching { getObjectField(fragment, "mBluetoothHfp") }.getOrNull()
        val cached = runCatching { getObjectField(fragment, "mCachedDevice") }.getOrNull()
        val supportAnc = runCatching { getObjectField(fragment, "mSupportAnc") }.getOrNull()
        val ancCached = runCatching { getObjectField(fragment, "mAncCached") }.getOrNull()
        val pendingAnc = runCatching { getObjectField(fragment, "mPendingAnc") }.getOrNull()
        val ancPendingStatus = runCatching { getObjectField(fragment, "mAncPendingStatus") }.getOrNull()
        return "fragment(device=${device.describe()},deviceId=$deviceId,support=$support,service=$service,hfp=$hfp,cached=$cached,supportAnc=$supportAnc,ancCached=$ancCached,pendingAnc=$pendingAnc,ancPending=$ancPendingStatus)"
    }

    private fun isHuaweiAddress(address: String): Boolean {
        val normalized = address.uppercase()
        return resolveHuaweiDeviceRoute(address, null).isSupported ||
            normalized == currentAddress?.uppercase() ||
            normalized in knownHuaweiAddresses
    }

    private fun rememberSupportedDevice(intent: Intent): Boolean {
        val address = intent.getStringExtra("address") ?: currentAddress
        val name = intent.getStringExtra("device_name") ?: currentName
        val route = decodeHuaweiDeviceRouteFromBroadcast(
            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
        ) ?: resolveHuaweiDeviceRoute(address, name)
        if (!route.isSupported) {
            Log.w(TAG, "ignored unsupported persisted/broadcast device=${name.orEmpty()}/${address.orEmpty()}")
            return false
        }
        activateCurrentHuaweiDevice(address, name, route)
        return true
    }

    private fun activateCurrentHuaweiDevice(
        address: String?,
        name: String?,
        route: HuaweiDeviceRoute,
    ) {
        val activeAddress = currentAddress?.takeIf(String::isNotBlank)
        val nextAddress = address?.takeIf(String::isNotBlank)
        val identityChanged = when {
            activeAddress != null || nextAddress != null ->
                activeAddress == null || nextAddress == null ||
                    !activeAddress.equals(nextAddress, ignoreCase = true)
            else -> currentName?.takeIf(String::isNotBlank) != name?.takeIf(String::isNotBlank)
        }
        if (identityChanged || currentHuaweiRoute() != route) {
            resetCurrentDeviceState(route)
            settingsHeaderBitmapCache = null
        }
        currentName = name
        currentAddress = address
        currentRoute = route
        nextAddress?.let { knownHuaweiAddresses[it.uppercase()] = route }
        if (identityChanged || currentFreeClip2AudioState == FreeClip2AudioUiState()) {
            loadCurrentFreeClip2AudioState()
        }
        if (identityChanged ||
            (currentFreeClip2FeatureStates.isEmpty() &&
                confirmedFreeClip2Features.isEmpty() &&
                pendingFreeClip2Features.isEmpty() &&
                failedFreeClip2Features.isEmpty())
        ) {
            loadCurrentFreeClip2FeatureStates()
        }
    }

    private fun resetCurrentDeviceState(route: HuaweiDeviceRoute) {
        currentBattery = BatteryParams()
        currentAnc = NoiseControlMode.OFF.broadcastStatus
        currentAncConfirmed = false
        currentHuaweiAncLevel = route.defaultAncSubMode ?: UNKNOWN_HUAWEI_ANC_SUBMODE
        currentTransparencySubMode = defaultTransparencySubMode(route)
        currentFreeClip2AudioState = FreeClip2AudioUiState()
        currentFreeClip2FeatureStates.clear()
        confirmedFreeClip2Features.clear()
        pendingFreeClip2Features.clear()
        failedFreeClip2Features.clear()
        freeClip2AudioPendingGate.clear()
        settingsAncPendingGate.reset()
    }

    private fun targetsCurrentHuaweiDevice(intent: Intent): Boolean {
        val activeAddress = currentAddress?.takeIf(String::isNotBlank)
        val targetAddress = intent.getStringExtra("address")?.takeIf(String::isNotBlank)
        if (activeAddress != null || targetAddress != null) {
            return activeAddress != null && targetAddress != null &&
                activeAddress.equals(targetAddress, ignoreCase = true)
        }
        val activeName = currentName?.takeIf(String::isNotBlank)
        val targetName = intent.getStringExtra("device_name")?.takeIf(String::isNotBlank)
        return activeName != null && targetName != null && activeName == targetName
    }

    private fun clearCurrentHuaweiDevice(ctx: Context?) {
        currentAddress = null
        currentName = null
        currentRoute = null
        currentBattery = BatteryParams()
        currentAnc = NoiseControlMode.OFF.broadcastStatus
        currentAncConfirmed = false
        currentHuaweiAncLevel = UNKNOWN_HUAWEI_ANC_SUBMODE
        currentTransparencySubMode = 0x02
        currentFreeClip2AudioState = FreeClip2AudioUiState()
        currentFreeClip2FeatureStates.clear()
        confirmedFreeClip2Features.clear()
        pendingFreeClip2Features.clear()
        failedFreeClip2Features.clear()
        freeClip2AudioPendingGate.clear()
        settingsAncPendingGate.reset()
        (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.remove("address")
            ?.remove("name")
            ?.remove(PREF_DEVICE_ROUTE)
            ?.remove("anc")
            ?.remove("huawei_anc_level")
            ?.remove("transparency_submode")
            ?.remove("left_battery")
            ?.remove("left_charging")
            ?.remove("left_connected")
            ?.remove("right_battery")
            ?.remove("right_charging")
            ?.remove("right_connected")
            ?.remove("case_battery")
            ?.remove("case_charging")
            ?.remove("case_connected")
            ?.apply()
    }

    private fun forgetHeadsetFragments(address: String?) {
        val targetAddress = address?.takeIf(String::isNotBlank)
        headsetFragments.keys.toList().forEach { fragment ->
            val fragmentAddress = runCatching { fragmentBluetoothDevice(fragment)?.address }.getOrNull()
            if (targetAddress == null || fragmentAddress?.equals(targetAddress, ignoreCase = true) == true) {
                headsetFragments.remove(fragment)
            }
        }
    }

    private fun settingsBatteryString(): String {
        return settingsBatteryValues().joinToString(",")
    }

    private fun settingsBatteryValues(): List<Int> {
        loadState()
        return listOf(
            batteryValue(currentBattery.left),
            batteryValue(currentBattery.right),
            batteryValue(currentBattery.case)
        )
    }

    private fun batteryValue(params: PodParams?): Int {
        if (params?.isConnected != true) return 255
        val value = params.battery.coerceIn(0, 100)
        return if (params.isCharging) value or 128 else value
    }

    private fun settingsAncMode(): String {
        loadState()
        if (!currentHuaweiRoute().supportsAnc) return "0"
        return when (currentAnc) {
            2 -> "1"
            3 -> "2"
            else -> "0"
        }
    }

    private fun updateGestureFragments() {
        keyConfigFragments.keys.toList().forEach { fragment ->
            if (isCurrentHuaweiKeyConfigFragment(fragment)) {
                if (isFreeClip2EqualizerPage(fragment)) {
                    configureFreeClip2EqualizerPage(fragment)
                } else {
                    configureFreeBudsNativeGesturePage(fragment)
                }
            }
        }
    }

    private fun updateFreeClip2EqualizerFragments() {
        keyConfigFragments.keys.toList().forEach { fragment ->
            if (isCurrentHuaweiKeyConfigFragment(fragment) &&
                isFreeClip2EqualizerPage(fragment)
            ) {
                configureFreeClip2EqualizerPage(fragment)
            }
        }
    }

    private fun isCurrentHuaweiKeyConfigFragment(fragment: Any?): Boolean {
        return isCurrentHuaweiDevice(fragmentBluetoothDevice(fragment))
    }

    private fun settingsAncLevel(): String {
        loadState()
        val route = currentHuaweiRoute()
        if (!route.supportsAnc) return "0000"
        return when (currentAnc) {
            2 -> if (route.supportsDiscreteAncLevels) {
                val miuiLevel = huaweiSubModeToMiuiDiscreteAncLevel(route, ancSubMode(route))
                    ?: return "0000"
                "01${miuiLevel.toString(16).padStart(2, '0')}"
            } else {
                "0100"
            }
            3 -> if (route.supportsTransparency) {
                val protocolSubMode = transparencySubMode(route)
                val settingsSubMode = if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
                    huaweiTransparencySubModeToMiuiLevel(route, protocolSubMode) ?: return "0000"
                } else {
                    protocolSubMode
                }
                "02${settingsSubMode.toString(16).padStart(2, '0')}"
            } else {
                "0000"
            }
            else -> "0000"
        }
    }

    private fun settingsRefreshPayload(): String {
        val battery = settingsBatteryString().split(",")
        val left = battery.getOrNull(0).orEmpty()
        val right = battery.getOrNull(1).orEmpty()
        val box = battery.getOrNull(2).orEmpty()
        val values = MutableList(16) { "" }
        values[0] = left
        values[1] = right
        values[2] = box
        values[7] = settingsAncLevel()
        values[8] = "false"
        values[11] = "00"
        values[13] = "00"
        values[14] = "00"
        return values.joinToString(",")
    }

    private fun huaweiAncFromSettings(mode: Int): SettingsAncSelection? {
        val route = currentHuaweiRoute()
        if (!route.supportsAnc) return null
        return when (mode) {
            // 模式按钮只负责进入模式；现代耳机必须发送 FF 过渡命令，具体档位由随后回读确认。
            1 -> SettingsAncSelection(2)
            2 -> if (route.supportsTransparency) {
                SettingsAncSelection(3)
            } else {
                null
            }
            0 -> SettingsAncSelection(1)
            else -> null
        }
    }

    private fun huaweiAncFromLevelCommand(level: String): SettingsAncSelection? {
        val route = currentHuaweiRoute()
        if (!route.supportsAnc) return null
        val normalized = level.trim().lowercase()
        if (normalized == "0000") return SettingsAncSelection(1)
        if (normalized.length < 4) return null
        val type = normalized.substring(0, 2)
        val miuiSubMode = normalized.substring(2, 4).toIntOrNull(16) ?: return null
        return when (type) {
            "01" -> when {
                !route.supportsAnc -> null
                route.supportsDiscreteAncLevels -> miuiDiscreteAncLevelToHuaweiSubMode(route, miuiSubMode)
                    ?.let { SettingsAncSelection(2, it) }
                else -> SettingsAncSelection(2)
            }
            "02" -> (if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
                miuiTransparencyLevelToHuaweiSubMode(route, miuiSubMode)
            } else {
                miuiSubMode
            })
                ?.takeIf { route.supportsTransparency && it in supportedTransparencySubModes(route) }
                ?.let { SettingsAncSelection(3, it) }
            else -> null
        }
    }

    private fun ancSubMode(route: HuaweiDeviceRoute): Int =
        currentHuaweiAncLevel.takeIf(route::supportsAncSubMode)
            ?: route.defaultAncSubMode
            ?: 0

    private fun defaultTransparencySubMode(route: HuaweiDeviceRoute): Int =
        if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) 0x02 else 0xFF

    private fun supportedTransparencySubModes(route: HuaweiDeviceRoute): Set<Int> =
        if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) setOf(0x01, 0x02) else setOf(0x01, 0xFF)

    private fun transparencySubMode(route: HuaweiDeviceRoute): Int =
        currentTransparencySubMode.takeIf { it in supportedTransparencySubModes(route) }
            ?: defaultTransparencySubMode(route)

    private fun applyAncSelection(selection: SettingsAncSelection) {
        val route = currentHuaweiRoute()
        currentAnc = selection.status
        selection.subMode?.let { subMode ->
            when (selection.status) {
                2 -> if (route.supportsDiscreteAncLevels) {
                    subMode.takeIf(route::supportsAncSubMode)?.let {
                        currentHuaweiAncLevel = it
                    }
                }
                3 -> if (subMode in supportedTransparencySubModes(route)) {
                    currentTransparencySubMode = subMode
                }
            }
        }
    }

    private fun settingsSupport(): String = "${fakeDeviceId()},$SETTINGS_FREEBUDS_SUPPORT_FLAGS"

    private fun fragmentRootView(fragment: Any?): View? {
        return runCatching { callMethod(fragment, "getView") as? View }.getOrNull()
            ?: runCatching { getObjectField(fragment, "mRootView") as? View }.getOrNull()
            ?: runCatching { getObjectField(fragment, "mView") as? View }.getOrNull()
    }

    private fun configureFreeBudsNativeGesturePage(fragment: Any?) {
        val address = gestureDeviceAddress(fragment).orEmpty()
        val route = gestureDeviceRoute(fragment)
        if (fragment != null) keyConfigFragments[fragment] = true

        val doubleTapActions = HuaweiTapAction.availableFor(route, HuaweiGestureKind.DOUBLE_TAP)
        val tripleTapActions = HuaweiTapAction.availableFor(route, HuaweiGestureKind.TRIPLE_TAP)
        val swipeActions = HuaweiSwipeAction.availableFor(route)
        val longPressActions = if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            HuaweiTapAction.availableFor(route, HuaweiGestureKind.LONG_PRESS)
        } else {
            emptyList()
        }
        nativeGesturePreferenceKeys.forEach { key -> hideNativePreference(fragment, key) }
        if (doubleTapActions.isEmpty() && tripleTapActions.isEmpty() &&
            swipeActions.isEmpty() && longPressActions.isEmpty()
        ) {
            // 未验证的系统手势模板不能直接复用；已验证但无法映射到原生项的控制仍保留在模块页。
            Log.d(TAG, "Huawei native gesture page hidden route=$route address=$address")
            return
        }
        if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            listOf(
                SETTINGS_FREECLIP2_SWIPE_LEFT_KEY,
                SETTINGS_FREECLIP2_SWIPE_RIGHT_KEY,
                "long_press_left_headset",
                "long_press_right_headset",
            ).forEachIndexed { index, key ->
                ensureNativeGestureDropDownPreference(fragment, key, index + 4)
            }
        }
        configureTapPreference(
            fragment,
            "mDoubleClickLeft",
            "left_double",
            address,
            route,
            HuaweiGestureKind.DOUBLE_TAP,
            HuaweiGestureSide.LEFT,
        )
        configureTapPreference(
            fragment,
            "mDoubleClickRight",
            "right_double",
            address,
            route,
            HuaweiGestureKind.DOUBLE_TAP,
            HuaweiGestureSide.RIGHT,
        )
        if (tripleTapActions.isNotEmpty()) {
            configureTapPreference(
                fragment,
                "mTripleClickLeft",
                "left_triple",
                address,
                route,
                HuaweiGestureKind.TRIPLE_TAP,
                HuaweiGestureSide.LEFT,
            )
            configureTapPreference(
                fragment,
                "mTripleClickRight",
                "right_triple",
                address,
                route,
                HuaweiGestureKind.TRIPLE_TAP,
                HuaweiGestureSide.RIGHT,
            )
        }
        if (swipeActions.isNotEmpty()) {
            val leftKey = if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                SETTINGS_FREECLIP2_SWIPE_LEFT_KEY
            } else {
                "long_press_left_headset"
            }
            val rightKey = if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                SETTINGS_FREECLIP2_SWIPE_RIGHT_KEY
            } else {
                "long_press_right_headset"
            }
            configureSwipePreference(fragment, leftKey, address, route, HuaweiGestureSide.LEFT)
            configureSwipePreference(fragment, rightKey, address, route, HuaweiGestureSide.RIGHT)
        }
        if (longPressActions.isNotEmpty()) {
            configureTapPreference(
                fragment,
                null,
                "long_press_left_headset",
                address,
                route,
                HuaweiGestureKind.LONG_PRESS,
                HuaweiGestureSide.LEFT,
            )
            configureTapPreference(
                fragment,
                null,
                "long_press_right_headset",
                address,
                route,
                HuaweiGestureKind.LONG_PRESS,
                HuaweiGestureSide.RIGHT,
            )
        }
        if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            nativeGesturePreferenceKeys.forEachIndexed { order, key ->
                nativePreference(fragment, key)?.let { preference ->
                    callCompatibleMethod(preference, "setOrder", order)
                }
            }
        }
        Log.d(TAG, "Huawei native gesture page configured route=$route address=$address")
    }

    private fun ensureNativeGestureDropDownPreference(
        fragment: Any?,
        key: String,
        order: Int,
    ): Any? {
        fragment ?: return null
        val category = nativePreference(fragment, "headsetKeyConfig") ?: return null
        val template = nativePreference(fragment, "left_double") ?: return null
        return runCatching {
            val existing = nativePreference(fragment, key)
            if (existing != null && template.javaClass.isInstance(existing)) {
                callCompatibleMethod(existing, "setOrder", order)
                return@runCatching existing
            }
            existing?.let { preference ->
                val parent = callCompatibleMethod(preference, "getParent") ?: category
                if (callCompatibleMethod(parent, "removePreference", preference) == false) {
                    return@runCatching null
                }
            }
            val preference = newHostPreference(template.javaClass, preferenceContext(template))
            callCompatibleMethod(preference, "setKey", key)
            callCompatibleMethod(preference, "setOrder", order)
            callCompatibleMethod(preference, "setPersistent", false)
            callCompatibleMethod(
                preference,
                "setOnPreferenceChangeListener",
                nativeGesturePreferenceListener(fragment),
            )
            preference.takeIf { callCompatibleMethod(category, "addPreference", it) != false }
        }.onFailure {
            Log.w(TAG, "create native gesture preference failed key=$key", it)
        }.getOrNull()
    }

    private fun configureTapPreference(
        fragment: Any?,
        fieldName: String?,
        key: String,
        address: String,
        route: HuaweiDeviceRoute,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
    ) {
        val preference = fieldName?.let { runCatching { getObjectField(fragment, it) }.getOrNull() }
            ?: nativePreference(fragment, key)
            ?: return
        val context = (runCatching { callCompatibleMethod(preference, "getContext") as? Context }.getOrNull())
            ?: context
            ?: return
        val actions = HuaweiTapAction.availableFor(route, kind)
        if (actions.isEmpty()) return
        val entries = actions.map { gestureActionLabel(context, it) }.toTypedArray()
        val values = actions.map { it.extraValue }.toTypedArray()
        val selected = readGestureAction(address, kind, side, route)
        runCatching {
            callCompatibleMethod(preference, "setPersistent", false)
            callCompatibleMethod(preference, "setTitle", gesturePreferenceTitle(context, kind, side))
            callCompatibleMethod(preference, "setEntries", entries)
            callCompatibleMethod(preference, "setEntryValues", values)
            callCompatibleMethod(preference, "setValue", selected.extraValue)
            callCompatibleMethod(preference, "setVisible", true)
            callCompatibleMethod(preference, "setEnabled", true)
        }.onFailure {
            Log.w(
                TAG,
                "configure native gesture preference failed key=$key kind=${kind.extraValue} side=${side.extraValue}",
                it,
            )
        }
    }

    private fun configureSwipePreference(
        fragment: Any?,
        key: String,
        address: String,
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
    ) {
        val preference = nativePreference(fragment, key) ?: return
        val preferenceContext = runCatching { callCompatibleMethod(preference, "getContext") as? Context }.getOrNull()
            ?: context
            ?: return
        val actions = HuaweiSwipeAction.availableFor(route)
        if (actions.isEmpty()) return
        val entries = actions.map { swipeActionLabel(preferenceContext, it) }.toTypedArray()
        val values = actions.map { it.extraValue }.toTypedArray()
        val selected = readSwipeAction(address, side, route)
        runCatching {
            callCompatibleMethod(preference, "setPersistent", false)
            callCompatibleMethod(
                preference,
                "setTitle",
                gesturePreferenceTitle(preferenceContext, HuaweiGestureKind.SWIPE, side),
            )
            callCompatibleMethod(preference, "setEntries", entries)
            callCompatibleMethod(preference, "setEntryValues", values)
            callCompatibleMethod(preference, "setValue", selected.extraValue)
            callCompatibleMethod(preference, "setVisible", true)
            callCompatibleMethod(preference, "setEnabled", true)
        }.onFailure {
            Log.w(TAG, "configure native swipe preference failed key=$key side=${side.extraValue}", it)
        }
    }

    private fun hideNativePreference(fragment: Any?, key: String) {
        val preference = nativePreference(fragment, key) ?: return
        runCatching { callCompatibleMethod(preference, "setVisible", false) }
            .recoverCatching { callCompatibleMethod(preference, "setEnabled", false) }
            .onFailure { Log.w(TAG, "hide native gesture preference failed key=$key", it) }
    }

    private fun nativePreference(fragment: Any?, key: String): Any? {
        return runCatching { callCompatibleMethod(fragment, "findPreference", key) }.getOrNull()
            ?: runCatching { callCompatibleMethod(fragment, "findPreference", key as CharSequence) }.getOrNull()
    }

    private fun callCompatibleMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            cls.declaredMethods.firstOrNull { method ->
                method.name == methodName &&
                    method.parameterTypes.size == args.size &&
                    method.parameterTypes.zip(args).all { (type, arg) -> isCompatibleArgument(type, arg) }
            }?.let { method ->
                method.isAccessible = true
                return method.invoke(instance, *args)
            }
            cls = cls.superclass
        }
        throw NoSuchMethodException("${instance.javaClass.name}.$methodName/${args.size}")
    }

    private fun isCompatibleArgument(type: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !type.isPrimitive
        val target = when (type) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Void.TYPE -> java.lang.Void::class.java
            else -> type
        }
        return target.isInstance(arg)
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun readGestureAction(
        address: String,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
        route: HuaweiDeviceRoute,
    ): HuaweiTapAction {
        val key = gestureCacheKey(address, kind, side)
        gestureActionCache[key]?.let { return it }
        val defaultAction = defaultGestureAction(route, kind, side)
        val usesLocalState =
            (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 && kind == HuaweiGestureKind.DOUBLE_TAP) ||
                (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 && kind == HuaweiGestureKind.LONG_PRESS)
        if (!usesLocalState) {
            return defaultAction
        }
        val defaultValue = defaultAction.protocolValue(route, kind) ?: return defaultAction
        val localPreferences = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return defaultAction
        val legacyValue = if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
            localPreferences.getInt(legacyGesturePrefKey(address, side), defaultValue)
        } else {
            defaultValue
        }
        val value = localPreferences.getInt(key, legacyValue)
        return HuaweiTapAction.fromProtocolValue(route, kind, value) ?: defaultAction
    }

    private fun cacheGestureAction(
        address: String,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
        route: HuaweiDeviceRoute,
        action: HuaweiTapAction,
    ) {
        val protocolValue = action.protocolValue(route, kind) ?: return
        val key = gestureCacheKey(address, kind, side)
        gestureActionCache[key] = action
        if ((route == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 && kind == HuaweiGestureKind.DOUBLE_TAP) ||
            (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 && kind == HuaweiGestureKind.LONG_PRESS)
        ) {
            runCatching {
                context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putInt(key, protocolValue)
                    ?.apply()
            }.onFailure { Log.w(TAG, "persist local FreeBuds 3 gesture failed key=$key", it) }
        }
    }

    private fun readSwipeAction(
        address: String,
        side: HuaweiGestureSide,
        route: HuaweiDeviceRoute,
    ): HuaweiSwipeAction = swipeActionCache[gestureCacheKey(address, HuaweiGestureKind.SWIPE, side)]
        ?.takeIf { it in HuaweiSwipeAction.availableFor(route) }
        ?: HuaweiSwipeAction.availableFor(route).firstOrNull()
        ?: HuaweiSwipeAction.NONE

    private fun cacheSwipeAction(
        address: String,
        side: HuaweiGestureSide,
        action: HuaweiSwipeAction,
    ) {
        swipeActionCache[gestureCacheKey(address, HuaweiGestureKind.SWIPE, side)] = action
    }

    private fun cacheGestureState(
        intent: Intent,
        route: HuaweiDeviceRoute,
        address: String,
    ) {
        cacheTapState(
            intent = intent,
            route = route,
            address = address,
            kind = HuaweiGestureKind.DOUBLE_TAP,
            leftExtra = HuaweiGestureController.EXTRA_DOUBLE_LEFT_ACTION,
            rightExtra = HuaweiGestureController.EXTRA_DOUBLE_RIGHT_ACTION,
            legacyLeftExtra = "left_action",
            legacyRightExtra = "right_action",
        )
        cacheTapState(
            intent = intent,
            route = route,
            address = address,
            kind = HuaweiGestureKind.TRIPLE_TAP,
            leftExtra = HuaweiGestureController.EXTRA_TRIPLE_LEFT_ACTION,
            rightExtra = HuaweiGestureController.EXTRA_TRIPLE_RIGHT_ACTION,
        )
        listOf(
            HuaweiGestureSide.LEFT to HuaweiGestureController.EXTRA_SWIPE_LEFT_ACTION,
            HuaweiGestureSide.RIGHT to HuaweiGestureController.EXTRA_SWIPE_RIGHT_ACTION,
        ).forEach { (side, extra) ->
            HuaweiSwipeAction.fromExtra(intent.getStringExtra(extra))
                ?.takeIf { it in HuaweiSwipeAction.availableFor(route) }
                ?.let { cacheSwipeAction(address, side, it) }
        }
    }

    private fun cacheTapState(
        intent: Intent,
        route: HuaweiDeviceRoute,
        address: String,
        kind: HuaweiGestureKind,
        leftExtra: String,
        rightExtra: String,
        legacyLeftExtra: String? = null,
        legacyRightExtra: String? = null,
    ) {
        listOf(
            Triple(HuaweiGestureSide.LEFT, leftExtra, legacyLeftExtra),
            Triple(HuaweiGestureSide.RIGHT, rightExtra, legacyRightExtra),
        ).forEach { (side, extra, legacyExtra) ->
            val value = intent.getStringExtra(extra) ?: legacyExtra?.let(intent::getStringExtra)
            HuaweiTapAction.fromExtra(value)
                ?.takeIf { it in HuaweiTapAction.availableFor(route, kind) }
                ?.let { cacheGestureAction(address, kind, side, route, it) }
        }
    }

    private fun defaultGestureAction(
        route: HuaweiDeviceRoute,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
    ): HuaweiTapAction {
        val preferred = when {
            route == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 && side == HuaweiGestureSide.LEFT ->
                HuaweiTapAction.NOISE_CANCELLATION
            route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 && kind == HuaweiGestureKind.LONG_PRESS ->
                HuaweiTapAction.NONE
            route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 && kind == HuaweiGestureKind.TRIPLE_TAP &&
                side == HuaweiGestureSide.LEFT -> HuaweiTapAction.PLAY_PREVIOUS
            route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 && kind == HuaweiGestureKind.TRIPLE_TAP ->
                HuaweiTapAction.PLAY_NEXT
            else -> HuaweiTapAction.PLAY_PAUSE
        }
        return preferred.takeIf { it in HuaweiTapAction.availableFor(route, kind) }
            ?: HuaweiTapAction.availableFor(route, kind).firstOrNull()
            ?: HuaweiTapAction.NONE
    }

    private fun gestureCacheKey(
        address: String,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
    ): String {
        val normalizedAddress = address.ifBlank { currentAddress ?: "unknown" }.uppercase()
        return "huawei_gesture_${normalizedAddress}_${kind.extraValue}_${side.extraValue}"
    }

    private fun legacyGesturePrefKey(address: String, side: HuaweiGestureSide): String {
        val normalizedAddress = address.ifBlank { currentAddress ?: "unknown" }.uppercase()
        return "huawei_gesture_${normalizedAddress}_${side.extraValue}"
    }

    private fun gestureActionLabel(context: Context, action: HuaweiTapAction): String {
        val (resId, fallback) = when (action) {
            HuaweiTapAction.PLAY_NEXT -> R.string.huawei_gesture_action_next to "下一首"
            HuaweiTapAction.PLAY_PREVIOUS -> R.string.huawei_gesture_action_previous to "上一首"
            HuaweiTapAction.PLAY_PAUSE -> R.string.huawei_gesture_action_play_pause to "播放/暂停"
            HuaweiTapAction.VOICE_ASSISTANT -> R.string.huawei_gesture_action_voice_assistant to "唤醒语音助手"
            HuaweiTapAction.NOISE_CANCELLATION -> R.string.huawei_gesture_action_noise_control to "噪声控制"
            HuaweiTapAction.SPATIAL_AUDIO -> R.string.huawei_gesture_action_spatial_audio to "空间音频开关"
            HuaweiTapAction.NONE -> R.string.huawei_gesture_action_none to "无"
        }
        return moduleString(context, resId, fallback)
    }

    private fun swipeActionLabel(context: Context, action: HuaweiSwipeAction): String {
        val (resId, fallback) = when (action) {
            HuaweiSwipeAction.VOLUME_CONTROL -> R.string.huawei_gesture_action_volume_control to "调节音量"
            HuaweiSwipeAction.TRACK_CONTROL -> R.string.huawei_gesture_action_track_control to "切换曲目"
            HuaweiSwipeAction.NONE -> R.string.huawei_gesture_action_none to "无"
        }
        return moduleString(context, resId, fallback)
    }

    private fun gesturePreferenceTitle(
        context: Context,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
    ): String {
        val (resId, fallback) = when (kind to side) {
            HuaweiGestureKind.DOUBLE_TAP to HuaweiGestureSide.LEFT ->
                R.string.huawei_gesture_left_double_tap to "左侧双击"
            HuaweiGestureKind.DOUBLE_TAP to HuaweiGestureSide.RIGHT ->
                R.string.huawei_gesture_right_double_tap to "右侧双击"
            HuaweiGestureKind.TRIPLE_TAP to HuaweiGestureSide.LEFT ->
                R.string.huawei_gesture_left_triple_tap to "左侧三击"
            HuaweiGestureKind.TRIPLE_TAP to HuaweiGestureSide.RIGHT ->
                R.string.huawei_gesture_right_triple_tap to "右侧三击"
            HuaweiGestureKind.LONG_PRESS to HuaweiGestureSide.LEFT ->
                R.string.huawei_gesture_left_long_press to "长按左侧触控区"
            HuaweiGestureKind.LONG_PRESS to HuaweiGestureSide.RIGHT ->
                R.string.huawei_gesture_right_long_press to "长按右侧触控区"
            HuaweiGestureKind.SWIPE to HuaweiGestureSide.LEFT ->
                R.string.huawei_gesture_left_swipe to "左侧轻滑"
            HuaweiGestureKind.SWIPE to HuaweiGestureSide.RIGHT ->
                R.string.huawei_gesture_right_swipe to "右侧轻滑"
            else -> R.string.huawei_gesture_controls_title to "手势操作"
        }
        return moduleString(context, resId, fallback)
    }

    private fun moduleString(context: Context, resId: Int, fallback: String): String {
        return runCatching {
            val moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            if (!ModuleResourceResolver.isCurrentModuleBuild(moduleContext)) return fallback
            moduleContext.getString(resId)
        }.getOrElse { fallback }
    }

    private fun gestureDeviceAddress(fragment: Any?): String? {
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        val argDevice = runCatching {
            (callMethod(fragment, "getArguments") as? Bundle)?.parcelableDevice("BT_Device")
        }.getOrNull()
        return runCatching { device?.address }.getOrNull()
            ?: runCatching { argDevice?.address }.getOrNull()
            ?: currentAddress
    }

    private fun gestureDeviceRoute(fragment: Any?): HuaweiDeviceRoute {
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        val argDevice = runCatching {
            (callMethod(fragment, "getArguments") as? Bundle)?.parcelableDevice("BT_Device")
        }.getOrNull()
        val target = device ?: argDevice
        val address = runCatching { target?.address }.getOrNull() ?: currentAddress
        val name = runCatching { target?.name ?: target?.alias }.getOrNull() ?: currentName
        if (address != null && address.equals(currentAddress, ignoreCase = true)) {
            currentRoute?.takeIf { it.isSupported }?.let { return it }
        }
        return resolveHuaweiDeviceRoute(address, name)
    }

    private fun requestHuaweiGestureState(fragment: Any?, reason: String) {
        if (gestureDeviceRoute(fragment) != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val targetContext = context ?: return
        val address = gestureDeviceAddress(fragment).orEmpty()
        targetContext.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_REFRESH).apply {
            putExtra(HuaweiGestureController.EXTRA_ADDRESS, address)
            encodeHuaweiDeviceRouteForBroadcast(gestureDeviceRoute(fragment))?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "Huawei gesture state requested reason=$reason address=$address")
    }

    private fun sendHuaweiGestureFromSettings(
        context: Context,
        address: String,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
        action: String,
    ) {
        val targetAddress = address.ifBlank { currentAddress.orEmpty() }
        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET).apply {
            putExtra(HuaweiGestureController.EXTRA_ADDRESS, targetAddress)
            encodeHuaweiDeviceRouteForBroadcast(currentHuaweiRoute())?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra(HuaweiGestureController.EXTRA_GESTURE_KIND, kind.extraValue)
            putExtra(HuaweiGestureController.EXTRA_SIDE, side.extraValue)
            putExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION, action)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.i(
            TAG,
            "Settings gesture requested address=$targetAddress kind=${kind.extraValue} side=${side.extraValue} action=$action",
        )
    }

    private fun schedulePruneFreeBudsUnsupportedViews(root: View?) {
        if (root == null) return
        observeSettingsScroll(root)
        if (pendingSettingsScrollPrunes.containsKey(root)) return
        val expectedAddress = currentAddress?.takeIf(String::isNotBlank) ?: return
        val expectedRoute = currentHuaweiRoute()
        listOf(0L, 180L).forEach { delay ->
            root.postDelayed({
                if (!expectedAddress.equals(currentAddress, ignoreCase = true) || expectedRoute != currentHuaweiRoute()) {
                    Log.d(TAG, "Settings unsupported row prune skipped for stale device address=$expectedAddress route=$expectedRoute")
                    return@postDelayed
                }
                runCatching { pruneFreeBudsUnsupportedViews(root) }
                    .onFailure { Log.w(TAG, "Settings unsupported row prune failed", it) }
            }, delay)
        }
    }

    private fun observeSettingsScroll(root: View) {
        if (observedSettingsRoots.put(root, true) == true) return
        root.viewTreeObserver.addOnScrollChangedListener {
            if (!root.isAttachedToWindow) return@addOnScrollChangedListener
            val task = object : Runnable {
                private val rootReference = WeakReference(root)

                override fun run() {
                    val target = rootReference.get() ?: return
                    if (pendingSettingsScrollPrunes[target] !== this) return
                    pendingSettingsScrollPrunes.remove(target)
                    if (!target.isAttachedToWindow) return
                    runCatching { pruneFreeBudsUnsupportedViews(target) }
                        .onFailure { Log.w(TAG, "Settings prune after scroll failed", it) }
                }
            }
            pendingSettingsScrollPrunes.put(root, task)?.let(root::removeCallbacks)
            root.postDelayed(task, SETTINGS_SCROLL_IDLE_DELAY_MS)
        }
    }

    private fun pruneFreeBudsUnsupportedViews(root: View) {
        // RecyclerView 会复用条目 View；先恢复当前根节点下的历史裁剪状态，再按现有标题
        // 重新应用策略，避免一个已隐藏条目被复用后继续误伤无关设置。
        restoreTrackedSettingsCapabilityViews(root)
        replaceSettingsHeaderImage(root)
        applyXiaomiHeadsetCardBackgrounds(root)
        val route = currentHuaweiRoute()
        val policy = settingsHeadsetUiPolicy(route)
        val modeContainer = modeButtonContainer(root)
        modeContainer?.let {
            setSettingsCapabilityViewVisible(
                it,
                policy.showAnc || route == HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            )
        }
        setSettingsRowsVisible(root, settingsEarTipFitKeywords, policy.showEarTipFitTest)
        setSettingsRowsVisible(root, gestureEntryKeywords, policy.showGestureConfiguration)
        redirectMoreSettingsToHuaweiSmartAudio(root)
        if (policy.showAnc) configureTransparencyModeView(root, modeContainer, route)
        replaceHuaweiAncLevelsWithHuaweiDial(root)
        configureFreeClip2SpatialModeView(root, modeContainer)
    }

    private fun applyXiaomiHeadsetCardBackgrounds(root: View) {
        if (isSettingsDarkMode(root.context)) return
        val resources = root.resources
        val packageName = root.context.packageName
        listOf("batteryCard", "ancCard", "renameCard", "otaCard").forEach { name ->
            val viewId = resources.getIdentifier(name, "id", packageName)
            val card = viewId.takeIf { it != 0 }?.let { root.findViewById<View>(it) } ?: return@forEach
            runCatching {
                val currentColor = (callCompatibleMethod(card, "getCardBackgroundColor") as? ColorStateList)?.defaultColor
                if (currentColor != Color.WHITE) {
                    callCompatibleMethod(card, "setCardBackgroundColor", Color.WHITE)
                }
            }
                .onFailure { Log.w(TAG, "apply Xiaomi headset card background failed", it) }
        }
    }

    /** 将宿主降噪卡片的三列结构复用于 FreeClip 2 空间音频。 */
    private fun configureFreeClip2SpatialModeView(root: View, modeContainer: View?) {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2 || modeContainer == null) return
        val labels = huaweiFreeClip2AudioLabels { resId, fallback ->
            moduleString(root.context, resId, fallback)
        }
        configureFreeClip2SpatialOption(
            root,
            optionId = "transport",
            imageId = "imageTransport",
            textId = "transparents",
            drawableName = "closeanc",
            label = labels.spatialModeOff,
            mode = FreeClip2SpatialAudioMode.OFF,
        )
        configureFreeClip2SpatialOption(
            root,
            optionId = "openAnc",
            imageId = "imageopenAnc",
            textId = "ancText",
            drawableName = "openanc",
            label = labels.spatialModeFixed,
            mode = FreeClip2SpatialAudioMode.FIXED,
        )
        configureFreeClip2SpatialOption(
            root,
            optionId = "closeAnc",
            imageId = "imageCloseAnc",
            textId = "closeAncText",
            drawableName = "transparent",
            label = labels.spatialModeHeadTracking,
            mode = FreeClip2SpatialAudioMode.HEAD_TRACKING,
        )
        configureFreeClip2SpatialSceneView(root, labels)
    }

    private fun configureFreeClip2SpatialOption(
        root: View,
        optionId: String,
        imageId: String,
        textId: String,
        drawableName: String,
        label: String,
        mode: FreeClip2SpatialAudioMode,
    ) {
        val option = root.hostView(optionId) ?: return
        val image = root.hostView(imageId) as? ImageView ?: return
        val text = root.hostView(textId) as? TextView ?: return
        val selected = currentFreeClip2AudioState.spatialMode == mode
        val resources = root.resources
        val packageName = root.context.packageName
        val drawableId = resources.getIdentifier(
            "${drawableName}_${if (selected) "on" else "off"}",
            "drawable",
            packageName,
        )
        if (drawableId != 0) {
            image.clearColorFilter()
            image.setImageResource(drawableId)
        }
        val imageHeightId = resources.getIdentifier(
            if (selected) "headset_set_dimens" else "headset_unset_dimens",
            "integer",
            packageName,
        )
        if (imageHeightId != 0) {
            image.layoutParams?.let { params ->
                params.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = resources.getInteger(imageHeightId)
                }
                image.layoutParams = params
            }
        }
        val textColorId = resources.getIdentifier(
            if (selected) "anc_text_color" else "first_text_color",
            "color",
            packageName,
        )
        if (textColorId != 0) {
            text.setTextColor(root.context.getColor(textColorId))
        }
        text.text = label
        option.isSelected = selected
        option.contentDescription = label
        option.setOnClickListener {
            if (currentFreeClip2AudioState.spatialMode != mode) {
                onFreeClip2AudioSelected(
                    HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE,
                    mode.extraValue,
                )
            }
        }
    }

    /** 使用宿主降噪卡片内置的四档调节区显示空间模式。 */
    private fun configureFreeClip2SpatialSceneView(
        root: View,
        labels: HuaweiFreeClip2AudioControlsView.Labels,
    ) {
        val adjustContainer = root.hostView("ancAdjust") ?: return
        val textContainer = root.hostView("ancAdjustText") ?: return
        val adjustView = root.hostView("ancAdjustView") ?: return
        root.hostView("ancAdjustView2")?.visibility = View.GONE
        root.hostView("transparentAdjust")?.visibility = View.GONE
        root.hostView("transparentAdjustText")?.visibility = View.GONE
        val visible = currentFreeClip2AudioState.spatialMode != FreeClip2SpatialAudioMode.OFF
        adjustContainer.visibility = if (visible) View.VISIBLE else View.GONE
        textContainer.visibility = if (visible) View.VISIBLE else View.GONE
        adjustView.visibility = if (visible) View.VISIBLE else View.GONE
        adjustView.isEnabled = visible
        if (!visible) return

        val options = listOf(
            "ancAdapterText" to labels.spatialSceneDefault,
            "ancLowText" to labels.spatialSceneTheater,
            "ancMediumText" to labels.spatialSceneCinema,
            "ancHighText" to labels.spatialSceneConcert,
        )
        val selectedIndex = FreeClip2SpatialScene.entries.indexOf(
            currentFreeClip2AudioState.spatialScene,
        ).coerceAtLeast(0)
        val resources = root.resources
        val packageName = root.context.packageName
        val selectedColorId = resources.getIdentifier("anc_text_color", "color", packageName)
        val normalColorId = resources.getIdentifier("anc_text_unselect_color", "color", packageName)
        options.forEachIndexed { index, (viewId, label) ->
            (root.hostView(viewId) as? TextView)?.apply {
                text = label
                visibility = View.VISIBLE
                val colorId = if (index == selectedIndex) selectedColorId else normalColorId
                if (colorId != 0) setTextColor(context.getColor(colorId))
            }
        }
        callCompatibleMethod(adjustView, "setPointCount", options.size)
        callCompatibleMethod(adjustView, "setCurrentPointIndex", selectedIndex)
        adjustView.contentDescription = labels.spatialScene
    }

    private fun View.hostView(name: String): View? {
        val id = resources.getIdentifier(name, "id", context.packageName)
        return id.takeIf { it != 0 }?.let { findViewById<View>(it) }
    }

    private fun setSettingsRowsVisible(
        root: View,
        keywords: List<String>,
        visible: Boolean,
    ) {
        val matches = mutableListOf<TextView>()
        collectExactTextMatches(root, keywords, matches)
        matches.forEach { textView ->
            val target = bestHideTarget(root, textView)
            if (target !== root && !target.isSystemScrollingContainer()) {
                val collapseRecyclerItem =
                    (target.parent as? View)?.isRecyclingRowsContainer() == true &&
                        target.layoutParams != null
                setSettingsCapabilityViewVisible(
                    view = target,
                    visible = visible,
                    collapseLayout = collapseRecyclerItem,
                )
                if (!visible) {
                    Log.d(
                        TAG,
                        "Settings unsupported row hidden route=${currentHuaweiRoute()} " +
                            "text=${textView.text} target=${target.javaClass.name}",
                    )
                }
            }
        }
    }

    private fun redirectMoreSettingsToHuaweiSmartAudio(root: View) {
        val launchIntent = huaweiSmartAudioLaunchIntent
            ?: root.context.packageManager
                .getLaunchIntentForPackage(HUAWEI_SMART_AUDIO_PACKAGE)
                ?.also { huaweiSmartAudioLaunchIntent = it }
            ?: return
        val matches = mutableListOf<TextView>()
        collectExactTextMatches(root, moreSettingsKeywords, matches)
        matches.forEach { textView ->
            val target = bestHideTarget(root, textView)
            if (target === root || target.isSystemScrollingContainer()) return@forEach
            target.setOnClickListener { view ->
                runCatching {
                    view.context.startActivity(Intent(launchIntent))
                }.onFailure {
                    Log.w(TAG, "启动华为智慧音频失败", it)
                }
            }
        }
    }

    /** 复用小米耳机页现有的下拉偏好项，不再动态创建自定义卡片。 */
    private fun installFreeClip2AudioPreferences(fragment: Any?) {
        if (fragment == null || currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        runCatching {
            val screen = callCompatibleMethod(fragment, "getPreferenceScreen") ?: return@runCatching
            val categoryTemplate = nativePreference(fragment, "switchConfig") ?: return@runCatching
            val dropDownClass = nativePreference(fragment, "codecType")?.javaClass
                ?: findClass("com.android.settingslib.miuisettings.preference.miuix.DropDownPreference")
            val categoryOrder = (callCompatibleMethod(categoryTemplate, "getOrder") as? Int) ?: 3
            val category = nativePreference(fragment, SETTINGS_FREECLIP2_AUDIO_CATEGORY_KEY)
                ?: newHostPreference(categoryTemplate, preferenceContext(categoryTemplate)).also {
                    shiftNativePreferenceOrders(screen, categoryOrder, 1)
                    callCompatibleMethod(it, "setKey", SETTINGS_FREECLIP2_AUDIO_CATEGORY_KEY)
                    callCompatibleMethod(it, "setOrder", categoryOrder)
                    check(callCompatibleMethod(screen, "addPreference", it) != false)
                }
            ensureFreeClip2AudioPreference(
                fragment = fragment,
                category = category,
                preferenceClass = dropDownClass,
                key = SETTINGS_FREECLIP2_SOUND_EFFECT_KEY,
                order = 0,
                kind = HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT,
            )
            nativePreference(fragment, "key_config")?.let { rowTemplate ->
                ensureFreeClip2EqualizerEntry(
                    fragment = fragment,
                    category = category,
                    rowTemplate = rowTemplate,
                )
            }
            renderFreeClip2AudioPreferences(fragment)
            Log.d(TAG, "Settings FreeClip 2 native audio preferences installed")
        }.onFailure { Log.w(TAG, "Settings FreeClip 2 native audio preference install failed", it) }
    }

    private fun ensureFreeClip2AudioPreference(
        fragment: Any,
        category: Any,
        preferenceClass: Class<*>,
        key: String,
        order: Int,
        kind: String,
    ): Any {
        nativePreference(fragment, key)?.let { return it }
        return newHostPreference(preferenceClass, preferenceContext(category)).also { preference ->
            callCompatibleMethod(preference, "setKey", key)
            callCompatibleMethod(preference, "setOrder", order)
            callCompatibleMethod(preference, "setPersistent", false)
            callCompatibleMethod(
                preference,
                "setOnPreferenceChangeListener",
                freeClip2AudioPreferenceListener(kind),
            )
            check(callCompatibleMethod(category, "addPreference", preference) != false)
        }
    }

    private fun ensureFreeClip2EqualizerEntry(
        fragment: Any,
        category: Any,
        rowTemplate: Any,
    ) {
        if (nativePreference(fragment, SETTINGS_FREECLIP2_EQUALIZER_ENTRY_KEY) != null) return
        newHostPreference(rowTemplate, preferenceContext(category)).also { preference ->
            callCompatibleMethod(preference, "setKey", SETTINGS_FREECLIP2_EQUALIZER_ENTRY_KEY)
            callCompatibleMethod(preference, "setOrder", 1)
            callCompatibleMethod(preference, "setPersistent", false)
            callCompatibleMethod(preference, "setShowRightArrow", true)
            callCompatibleMethod(
                preference,
                "setOnPreferenceClickListener",
                freeClip2EqualizerEntryClickListener(fragment),
            )
            check(callCompatibleMethod(category, "addPreference", preference) != false)
        }
    }

    private fun freeClip2EqualizerEntryClickListener(fragment: Any): Any {
        val listenerClass = findClass("androidx.preference.Preference\$OnPreferenceClickListener")
        val fragmentReference = WeakReference(fragment)
        return Proxy.newProxyInstance(appClassLoader, arrayOf(listenerClass)) { proxy, method, arguments ->
            when (method.name) {
                "onPreferenceClick" -> openFreeClip2EqualizerPage(fragmentReference.get())
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "HuaweiPodsFreeClip2EqualizerEntryListener"
                else -> null
            }
        }
    }

    private fun openFreeClip2EqualizerPage(fragment: Any?): Boolean {
        if (fragment == null || currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            return false
        }
        return runCatching {
            val activity = callCompatibleMethod(fragment, "getActivity") ?: return@runCatching false
            val equalizerFragment = findClass(
                "com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment",
            ).getConstructor().newInstance()
            callCompatibleMethod(
                equalizerFragment,
                "setArguments",
                Bundle().apply { putBoolean(SETTINGS_FREECLIP2_EQUALIZER_PAGE_ARGUMENT, true) },
            )
            freeClip2EqualizerFragments[equalizerFragment] = true
            callCompatibleMethod(activity, "changeFragment", equalizerFragment)
            true
        }.onFailure {
            Log.w(TAG, "Settings FreeClip 2 equalizer page launch failed", it)
        }.getOrDefault(false)
    }

    private fun freeClip2AudioPreferenceListener(kind: String): Any {
        val listenerClass = findClass("androidx.preference.Preference\$OnPreferenceChangeListener")
        return Proxy.newProxyInstance(appClassLoader, arrayOf(listenerClass)) { proxy, method, arguments ->
            when (method.name) {
                "onPreferenceChange" -> {
                    val value = arguments?.getOrNull(1)?.toString() ?: return@newProxyInstance false
                    onFreeClip2AudioPreferenceChanged(kind, value)
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "HuaweiPodsFreeClip2AudioPreferenceListener($kind)"
                else -> null
            }
        }
    }

    private fun onFreeClip2AudioPreferenceChanged(kind: String, value: String): Boolean {
        if (kind != HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT) return false
        val effect = FreeClip2SoundEffect.fromExtraValue(value) ?: return false
        if (effect == FreeClip2SoundEffect.CUSTOM) {
            if (currentFreeClip2AudioState.soundEffect != FreeClip2SoundEffect.CUSTOM) {
                currentFreeClip2AudioState = currentFreeClip2AudioState.copy(soundEffect = effect)
                saveCurrentFreeClip2AudioState(context)
                updateFragments()
            }
            return true
        }
        if (!effect.isSelectable) return false
        freeClip2EqualizerWriteGeneration += 1
        return value == currentFreeClip2AudioState.soundEffect.extraValue ||
            onFreeClip2AudioSelected(kind, value)
    }

    private fun freeClip2EqualizerPreferenceListener(index: Int): Any {
        val listenerClass = findClass("androidx.preference.Preference\$OnPreferenceChangeListener")
        return Proxy.newProxyInstance(appClassLoader, arrayOf(listenerClass)) { proxy, method, arguments ->
            when (method.name) {
                "onPreferenceChange" -> {
                    val gain = (arguments?.getOrNull(1) as? Number)?.toInt()
                        ?: return@newProxyInstance false
                    val gains = currentFreeClip2AudioState.equalizerGains.toMutableList()
                    gains[index] = gain.coerceIn(
                        HuaweiEqualizerCodec.GAIN_RANGE.first,
                        HuaweiEqualizerCodec.GAIN_RANGE.last,
                    )
                    writeFreeClip2CustomEqualizer(gains)
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "HuaweiPodsFreeClip2EqualizerListener($index)"
                else -> null
            }
        }
    }

    private fun renderFreeClip2AudioPreferences(fragment: Any?) {
        runCatching {
            val category = nativePreference(fragment, SETTINGS_FREECLIP2_AUDIO_CATEGORY_KEY)
                ?: return@runCatching
            val visible = currentHuaweiRoute() == HuaweiDeviceRoute.HUAWEI_FREECLIP2
            callCompatibleMethod(category, "setVisible", visible)
            if (!visible) return@runCatching
            val preferenceContext = preferenceContext(category)
            val labels = huaweiFreeClip2AudioLabels { resId, fallback ->
                moduleString(preferenceContext, resId, fallback)
            }
            nativePreference(fragment, SETTINGS_FREECLIP2_SOUND_EFFECT_KEY)?.let { preference ->
                renderFreeClip2AudioDropDown(
                    preference = preference,
                    title = labels.soundEffect,
                    summary = if (
                        currentFreeClip2AudioState.soundEffect == FreeClip2SoundEffect.CUSTOM
                    ) {
                        moduleString(
                            preferenceContext,
                            R.string.huawei_equalizer_title,
                            "自定义均衡器",
                        )
                    } else {
                        moduleString(
                            preferenceContext,
                            R.string.freeclip2_sound_effect_preset,
                            "官方预置音效",
                        )
                    },
                    entries = freeClip2SettingsSoundEffects().map { effect ->
                        when (effect) {
                            FreeClip2SoundEffect.DEFAULT -> labels.soundEffectDefault
                            FreeClip2SoundEffect.SPORT_ENHANCE -> labels.soundEffectSport
                            FreeClip2SoundEffect.TREBLE_ENHANCE -> labels.soundEffectTreble
                            FreeClip2SoundEffect.CLEAR_VOICE -> labels.soundEffectClearVoice
                            FreeClip2SoundEffect.CUSTOM -> labels.soundEffectCustom
                        } to effect.extraValue
                    },
                    value = currentFreeClip2AudioState.soundEffect.extraValue,
                    visible = true,
                )
            }
            renderFreeClip2EqualizerEntry(fragment, preferenceContext)
        }.onFailure { Log.w(TAG, "Settings FreeClip 2 native audio preference render failed", it) }
    }

    private fun renderFreeClip2EqualizerEntry(
        fragment: Any?,
        preferenceContext: Context,
    ) {
        val visible = shouldShowFreeClip2EqualizerEntry(currentFreeClip2AudioState.soundEffect)
        nativePreference(fragment, SETTINGS_FREECLIP2_EQUALIZER_ENTRY_KEY)?.let { preference ->
            callCompatibleMethod(
                preference,
                "setTitle",
                moduleString(preferenceContext, R.string.huawei_equalizer_title, "自定义均衡器"),
            )
            callCompatibleMethod(preference, "setSummary", null)
            callCompatibleMethod(preference, "setShowRightArrow", true)
            callCompatibleMethod(preference, "setVisible", visible)
            callCompatibleMethod(preference, "setEnabled", true)
        }
    }

    private fun isFreeClip2EqualizerPage(fragment: Any?): Boolean {
        if (fragment == null) return false
        if (freeClip2EqualizerFragments.containsKey(fragment)) return true
        val arguments = runCatching {
            callCompatibleMethod(fragment, "getArguments") as? Bundle
        }.getOrNull()
        val marked = arguments?.getBoolean(SETTINGS_FREECLIP2_EQUALIZER_PAGE_ARGUMENT, false) == true
        if (marked) freeClip2EqualizerFragments[fragment] = true
        return marked
    }

    private fun configureFreeClip2EqualizerPage(fragment: Any?) {
        if (fragment == null || gestureDeviceRoute(fragment) != HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            return
        }
        keyConfigFragments[fragment] = true
        runCatching {
            nativeGesturePreferenceKeys.forEach { key -> hideNativePreference(fragment, key) }
            val category = nativePreference(fragment, "headsetKeyConfig") ?: return@runCatching
            val preferenceContext = preferenceContext(category)
            val equalizerClass = findClass("com.android.settings.widget.MiuiSeekBarPreference")
            repeat(HuaweiEqualizerCodec.BAND_COUNT) { index ->
                val key = SETTINGS_FREECLIP2_EQUALIZER_BAND_KEY_PREFIX + index
                val preference = nativePreference(fragment, key)
                    ?: newHostPreference(equalizerClass, preferenceContext).also { created ->
                        callCompatibleMethod(created, "setKey", key)
                        callCompatibleMethod(created, "setOrder", index)
                        callCompatibleMethod(created, "setPersistent", false)
                        callCompatibleMethod(created, "setShowTitleIcon", false)
                        callCompatibleMethod(
                            created,
                            "setOnPreferenceChangeListener",
                            freeClip2EqualizerPreferenceListener(index),
                        )
                        check(callCompatibleMethod(category, "addPreference", created) != false)
                    }
                renderFreeClip2EqualizerBand(preference, preferenceContext, index)
            }
            val activity = callCompatibleMethod(fragment, "getActivity")
            val actionBar = callCompatibleMethod(activity, "getAppCompatActionBar")
            callCompatibleMethod(
                actionBar,
                "setTitle",
                moduleString(preferenceContext, R.string.huawei_equalizer_title, "自定义均衡器"),
            )
        }.onFailure {
            Log.w(TAG, "Settings FreeClip 2 equalizer page configuration failed", it)
        }
    }

    private fun renderFreeClip2EqualizerBand(
        preference: Any,
        preferenceContext: Context,
        index: Int,
    ) {
        val gain = currentFreeClip2AudioState.equalizerGains[index]
        callCompatibleMethod(
            preference,
            "setTitle",
            String.format(
                Locale.getDefault(),
                moduleString(
                    preferenceContext,
                    R.string.freebuds7i_eq_band,
                    "频段 %1\$d",
                ),
                index + 1,
            ),
        )
        callCompatibleMethod(preference, "setSummary", formatFreeClip2EqualizerGain(gain))
        callCompatibleMethod(preference, "setMin", HuaweiEqualizerCodec.GAIN_RANGE.first)
        callCompatibleMethod(preference, "setMax", HuaweiEqualizerCodec.GAIN_RANGE.last)
        callCompatibleMethod(preference, "setProgress", gain)
        callCompatibleMethod(preference, "setVisible", true)
        callCompatibleMethod(preference, "setEnabled", true)
    }

    private fun formatFreeClip2EqualizerGain(gain: Int): String {
        val sign = when {
            gain > 0 -> "+"
            gain < 0 -> "-"
            else -> ""
        }
        val magnitude = abs(gain)
        return "$sign${magnitude / 10}.${magnitude % 10} dB"
    }

    private fun renderFreeClip2AudioDropDown(
        preference: Any,
        title: String,
        summary: String? = null,
        entries: List<Pair<String, String>>,
        value: String,
        visible: Boolean,
    ) {
        callCompatibleMethod(preference, "setTitle", title)
        summary?.let { callCompatibleMethod(preference, "setSummary", it) }
        callCompatibleMethod(preference, "setEntries", entries.map { it.first }.toTypedArray())
        callCompatibleMethod(preference, "setEntryValues", entries.map { it.second }.toTypedArray())
        callCompatibleMethod(preference, "setValue", value)
        callCompatibleMethod(preference, "setVisible", visible)
        callCompatibleMethod(preference, "setEnabled", true)
    }

    private fun onFreeClip2AudioSelected(kind: String, value: String): Boolean {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return false
        val ctx = context ?: return false
        val address = currentAddress?.takeIf(String::isNotBlank) ?: run {
            Log.w(TAG, "Settings FreeClip 2 audio command skipped: missing address kind=$kind value=$value")
            return false
        }
        if (!freeClip2AudioPendingGate.tryBegin(kind, value, SystemClock.elapsedRealtime())) {
            Log.d(TAG, "Settings duplicate FreeClip 2 audio request ignored kind=$kind value=$value")
            return false
        }
        val sent = runCatching {
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_SET).apply {
                putExtra("address", address)
                putExtra("device_name", currentName.orEmpty())
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.HUAWEI_FREECLIP2))
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_KIND, kind)
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_VALUE, value)
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }.isSuccess
        if (!sent) {
            freeClip2AudioPendingGate.clear()
            Log.w(TAG, "Settings FreeClip 2 audio request send failed kind=$kind value=$value")
            return false
        }
        Log.i(TAG, "Settings FreeClip 2 audio requested address=$address kind=$kind value=$value")
        return true
    }

    private fun writeFreeClip2CustomEqualizer(gains: List<Int>): Boolean {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
            !isValidFreeClip2EqualizerGains(gains)
        ) {
            return false
        }
        val ctx = context ?: return false
        val address = currentAddress?.takeIf(String::isNotBlank) ?: return false
        val previous = currentFreeClip2AudioState
        val presetId = previous.equalizerPresetId.takeIf { it in 0x64..0x66 } ?: 0x64
        val presetName = previous.equalizerName.trim().ifEmpty { "HuaweiPods" }
        val generation = ++freeClip2EqualizerWriteGeneration
        currentFreeClip2AudioState = previous.copy(
            soundEffect = FreeClip2SoundEffect.CUSTOM,
            equalizerPresetId = presetId,
            equalizerName = presetName,
            equalizerGains = gains.toList(),
        )
        saveCurrentFreeClip2AudioState(ctx)
        updateFragments()
        SmartAudioEqualizerClient.setCustom(
            context = ctx,
            address = address,
            presetId = presetId,
            name = presetName,
            gains = gains,
        ) { success ->
            if (generation != freeClip2EqualizerWriteGeneration ||
                !address.equals(currentAddress, ignoreCase = true)
            ) {
                return@setCustom
            }
            if (!success) {
                currentFreeClip2AudioState = previous
                saveCurrentFreeClip2AudioState(ctx)
                updateFragments()
                Toast.makeText(
                    ctx,
                    moduleString(ctx, R.string.freeclip2_state_failed, "设置失败，请重试"),
                    Toast.LENGTH_SHORT,
                ).show()
                Log.w(TAG, "Settings FreeClip 2 custom equalizer write failed address=$address")
            }
        }
        return true
    }

    private fun installFreeClip2FeaturePreferences(fragment: Any?) {
        if (fragment == null || currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        runCatching {
            val screen = callCompatibleMethod(fragment, "getPreferenceScreen") ?: return@runCatching
            val propertyCategory = nativePreference(fragment, "ldac_container") ?: return@runCatching
            val rowTemplate = nativePreference(fragment, "notificationdisplay")
                ?: nativePreference(fragment, "abs_volume_pre")
                ?: return@runCatching
            val existingSmart = nativePreference(fragment, SETTINGS_FREECLIP2_SMART_CATEGORY_KEY)
            val existingSound = nativePreference(fragment, SETTINGS_FREECLIP2_SOUND_CATEGORY_KEY)
            val propertyOrder = (callCompatibleMethod(propertyCategory, "getOrder") as? Int) ?: 4
            val firstFeatureOrder = when {
                existingSmart != null ->
                    (callCompatibleMethod(existingSmart, "getOrder") as? Int) ?: propertyOrder
                existingSound != null ->
                    ((callCompatibleMethod(existingSound, "getOrder") as? Int) ?: propertyOrder) - 1
                else -> {
                    // 原生 switchConfig 和 ldac_container 相邻；先为两个同级分类腾出顺序。
                    shiftNativePreferenceOrders(screen, propertyOrder, 2)
                    propertyOrder
                }
            }
            val smartCategory = existingSmart ?: createFreeClip2FeatureCategory(
                screen = screen,
                categoryTemplate = propertyCategory,
                rowTemplate = rowTemplate,
                key = SETTINGS_FREECLIP2_SMART_CATEGORY_KEY,
                title = moduleString(
                    preferenceContext(rowTemplate),
                    R.string.freeclip2_smart_features,
                    "智能功能",
                ),
                order = firstFeatureOrder,
            )
            val soundCategory = existingSound ?: createFreeClip2FeatureCategory(
                screen = screen,
                categoryTemplate = propertyCategory,
                rowTemplate = rowTemplate,
                key = SETTINGS_FREECLIP2_SOUND_CATEGORY_KEY,
                title = moduleString(
                    preferenceContext(rowTemplate),
                    R.string.freeclip2_sound_and_connection,
                    "声音与连接",
                ),
                order = firstFeatureOrder + 1,
            )
            ensureFreeClip2FeaturePreferences(
                fragment,
                smartCategory,
                rowTemplate,
                freeClip2SmartFeatures,
            )
            ensureFreeClip2FeaturePreferences(
                fragment,
                soundCategory,
                rowTemplate,
                freeClip2SoundFeatures,
            )
            renderFreeClip2FeaturePreferences(fragment)
            Log.d(TAG, "Settings FreeClip 2 native preference categories installed")
        }.onFailure { Log.w(TAG, "Settings FreeClip 2 native preference install failed", it) }
    }

    private fun shiftNativePreferenceOrders(screen: Any, firstOrder: Int, amount: Int) {
        val count = callCompatibleMethod(screen, "getPreferenceCount") as? Int ?: return
        (0 until count)
            .mapNotNull { index ->
                val preference = callCompatibleMethod(screen, "getPreference", index) ?: return@mapNotNull null
                val order = callCompatibleMethod(preference, "getOrder") as? Int ?: return@mapNotNull null
                preference to order
            }
            .filter { (_, order) -> order >= firstOrder && order <= Int.MAX_VALUE - amount }
            .sortedByDescending { (_, order) -> order }
            .forEach { (preference, order) ->
                callCompatibleMethod(preference, "setOrder", order + amount)
            }
    }

    /** 仅调整顶层分类顺序，不改变分类内部结构和样式。 */
    private fun orderFreeClip2SettingsPreferences(fragment: Any?) {
        if (fragment == null || currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        runCatching {
            val screen = callCompatibleMethod(fragment, "getPreferenceScreen") ?: return@runCatching
            val count = callCompatibleMethod(screen, "getPreferenceCount") as? Int ?: return@runCatching
            val preferences = (0 until count).mapNotNull { index ->
                callCompatibleMethod(screen, "getPreference", index)
            }
            val preferencesByKey = preferences.mapNotNull { preference ->
                val key = callCompatibleMethod(preference, "getKey") as? String
                key?.let { it to preference }
            }.toMap()
            val leadingKeys = listOf(
                SETTINGS_FREECLIP2_AUDIO_CATEGORY_KEY,
                SETTINGS_FREECLIP2_SMART_CATEGORY_KEY,
                SETTINGS_FREECLIP2_SOUND_CATEGORY_KEY,
                "ldac_container",
                "switchConfig",
            )
            val trailingKeys = listOf("profile_container", "moreSettingsInAi")
            val orderedKeys = (leadingKeys + trailingKeys).toSet()
            val disconnectPreference = nativePreference(fragment, "mi_disconnect")
                ?: nativePreference(fragment, "mi_ignore")
            val disconnectCategory = disconnectPreference
                ?.let { preference -> callCompatibleMethod(preference, "getParent") }
                ?.takeIf { parent -> preferences.any { it === parent } }
            val orderedPreferences = leadingKeys.mapNotNull(preferencesByKey::get) +
                preferences.filter { preference ->
                    preference !== disconnectCategory &&
                        (callCompatibleMethod(preference, "getKey") as? String) !in orderedKeys
                } +
                trailingKeys.mapNotNull(preferencesByKey::get) +
                listOfNotNull(disconnectCategory)
            orderedPreferences.forEachIndexed { order, preference ->
                if ((callCompatibleMethod(preference, "getOrder") as? Int) != order) {
                    callCompatibleMethod(preference, "setOrder", order)
                }
            }
        }.onFailure { Log.w(TAG, "Settings FreeClip 2 preference ordering failed", it) }
    }

    private fun createFreeClip2FeatureCategory(
        screen: Any,
        categoryTemplate: Any,
        rowTemplate: Any,
        key: String,
        title: String,
        order: Int,
    ): Any {
        val preferenceContext = preferenceContext(rowTemplate)
        val category = newHostPreference(categoryTemplate, preferenceContext)
        callCompatibleMethod(category, "setKey", key)
        callCompatibleMethod(category, "setTitle", title)
        callCompatibleMethod(category, "setOrder", order)
        check(callCompatibleMethod(screen, "addPreference", category) != false)
        return category
    }

    private fun ensureFreeClip2FeaturePreferences(
        fragment: Any,
        category: Any,
        rowTemplate: Any,
        features: List<FreeClip2BooleanFeature>,
    ) {
        val preferenceContext = preferenceContext(rowTemplate)
        features.forEachIndexed { index, feature ->
            val existing = nativePreference(fragment, freeClip2FeaturePreferenceKey(feature))
            if (existing != null) {
                renderFreeClip2FeaturePreference(existing, feature)
                return@forEachIndexed
            }
            val preference = newHostPreference(rowTemplate, preferenceContext)
            callCompatibleMethod(preference, "setKey", freeClip2FeaturePreferenceKey(feature))
            callCompatibleMethod(preference, "setOrder", index)
            callCompatibleMethod(preference, "setPersistent", false)
            callCompatibleMethod(
                preference,
                "setOnPreferenceChangeListener",
                freeClip2FeaturePreferenceListener(feature),
            )
            callCompatibleMethod(category, "addPreference", preference)
            renderFreeClip2FeaturePreference(preference, feature)
        }
    }

    private fun newHostPreference(template: Any, context: Context): Any {
        return newHostPreference(template.javaClass, context)
    }

    private fun newHostPreference(preferenceClass: Class<*>, context: Context): Any {
        return preferenceClass.getConstructor(Context::class.java).newInstance(context)
    }

    private fun preferenceContext(preference: Any): Context {
        return callCompatibleMethod(preference, "getContext") as? Context
            ?: context
            ?: error("Settings preference context is unavailable")
    }

    private fun freeClip2FeaturePreferenceListener(feature: FreeClip2BooleanFeature): Any {
        val listenerClass = findClass("androidx.preference.Preference\$OnPreferenceChangeListener")
        return Proxy.newProxyInstance(appClassLoader, arrayOf(listenerClass)) { proxy, method, arguments ->
            when (method.name) {
                "onPreferenceChange" -> {
                    val enabled = arguments?.getOrNull(1) as? Boolean
                    enabled != null && onFreeClip2FeatureSelected(feature, enabled)
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "HuaweiPodsFreeClip2PreferenceListener(${feature.extraValue})"
                else -> null
            }
        }
    }

    private fun freeClip2FeaturePreferenceKey(feature: FreeClip2BooleanFeature): String {
        return SETTINGS_FREECLIP2_FEATURE_KEY_PREFIX + feature.extraValue
    }

    private fun renderFreeClip2FeaturePreferences(fragment: Any?) {
        FreeClip2BooleanFeature.entries.forEach { feature ->
            nativePreference(fragment, freeClip2FeaturePreferenceKey(feature))?.let { preference ->
                renderFreeClip2FeaturePreference(preference, feature)
            }
        }
    }

    private fun updateFreeClip2FeaturePreferences() {
        headsetFragments.keys.toList().forEach(::renderFreeClip2FeaturePreferences)
    }

    private fun renderFreeClip2FeaturePreference(
        preference: Any,
        feature: FreeClip2BooleanFeature,
    ) {
        val preferenceContext = preferenceContext(preference)
        val pending = feature in pendingFreeClip2Features
        val confirmed = feature in confirmedFreeClip2Features
        val failed = feature in failedFreeClip2Features
        callCompatibleMethod(
            preference,
            "setTitle",
            freeClip2FeatureTitle(preferenceContext, feature),
        )
        callCompatibleMethod(
            preference,
            "setSummary",
            when {
                pending -> moduleString(
                    preferenceContext,
                    R.string.freeclip2_state_applying,
                    "正在应用…",
                )
                failed -> moduleString(
                    preferenceContext,
                    R.string.freeclip2_state_failed,
                    "读取或设置失败，请重试",
                )
                confirmed -> freeClip2FeatureSummary(preferenceContext, feature)
                else -> moduleString(
                    preferenceContext,
                    R.string.freeclip2_state_loading,
                    "正在读取当前状态…",
                )
            },
        )
        if (!pending) {
            callCompatibleMethod(
                preference,
                "setChecked",
                currentFreeClip2FeatureStates[feature] == true,
            )
        }
        callCompatibleMethod(preference, "setEnabled", !pending && (confirmed || failed))
    }

    private fun freeClip2FeatureTitle(
        context: Context,
        feature: FreeClip2BooleanFeature,
    ): String = when (feature) {
        FreeClip2BooleanFeature.WEAR_DETECTION ->
            moduleString(context, R.string.freeclip2_wear_detection, "佩戴检测")
        FreeClip2BooleanFeature.DROP_REMINDER ->
            moduleString(context, R.string.freeclip2_drop_reminder, "掉落提醒")
        FreeClip2BooleanFeature.ADAPTIVE_VOLUME ->
            moduleString(context, R.string.freeclip2_adaptive_volume, "智感音量自适应")
        FreeClip2BooleanFeature.HEAD_MOTION_CONTROL ->
            moduleString(context, R.string.freeclip2_head_motion, "头动控制")
        FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY ->
            moduleString(context, R.string.freeclip2_sound_quality_priority, "声音质量优先")
        FreeClip2BooleanFeature.LOW_LATENCY ->
            moduleString(context, R.string.freeclip2_low_latency, "低时延模式")
        FreeClip2BooleanFeature.DUAL_DEVICE ->
            moduleString(context, R.string.freeclip2_dual_device, "双设备连接")
        FreeClip2BooleanFeature.CASE_PROMPT_SOUND ->
            moduleString(context, R.string.freeclip2_case_prompt_sound, "充电盒提示音")
    }

    private fun freeClip2FeatureSummary(
        context: Context,
        feature: FreeClip2BooleanFeature,
    ): String = when (feature) {
        FreeClip2BooleanFeature.WEAR_DETECTION -> moduleString(
            context,
            R.string.freeclip2_wear_detection_summary,
            "摘下耳机时自动暂停，重新佩戴时继续播放",
        )
        FreeClip2BooleanFeature.DROP_REMINDER -> moduleString(
            context,
            R.string.freeclip2_drop_reminder_summary,
            "耳机掉落时发出提醒",
        )
        FreeClip2BooleanFeature.ADAPTIVE_VOLUME -> moduleString(
            context,
            R.string.freeclip2_adaptive_volume_summary,
            "根据环境噪声自动调节播放音量",
        )
        FreeClip2BooleanFeature.HEAD_MOTION_CONTROL -> moduleString(
            context,
            R.string.freeclip2_head_motion_summary,
            "通过点头或摇头控制来电",
        )
        FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY -> moduleString(
            context,
            R.string.freeclip2_sound_quality_priority_summary,
            "优先使用高音质蓝牙传输",
        )
        FreeClip2BooleanFeature.LOW_LATENCY -> moduleString(
            context,
            R.string.freeclip2_low_latency_summary,
            "降低声音延迟，适合游戏等场景",
        )
        FreeClip2BooleanFeature.DUAL_DEVICE -> moduleString(
            context,
            R.string.freeclip2_dual_device_summary,
            "允许耳机同时连接两台设备",
        )
        FreeClip2BooleanFeature.CASE_PROMPT_SOUND -> moduleString(
            context,
            R.string.freeclip2_case_prompt_sound_summary,
            "控制充电盒开盖、充电等提示音",
        )
    }

    private fun onFreeClip2FeatureSelected(
        feature: FreeClip2BooleanFeature,
        enabled: Boolean,
    ): Boolean {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return false
        if (!pendingFreeClip2Features.add(feature)) return false
        failedFreeClip2Features.remove(feature)
        updateFreeClip2FeaturePreferences()
        val ctx = context ?: run {
            pendingFreeClip2Features.remove(feature)
            failedFreeClip2Features.add(feature)
            updateFreeClip2FeaturePreferences()
            return false
        }
        val address = currentAddress?.takeIf(String::isNotBlank) ?: run {
            pendingFreeClip2Features.remove(feature)
            failedFreeClip2Features.add(feature)
            updateFreeClip2FeaturePreferences()
            return false
        }
        val sent = runCatching {
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_FEATURE_SET).apply {
                putExtra("address", address)
                putExtra("device_name", currentName.orEmpty())
                encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.HUAWEI_FREECLIP2)?.let {
                    putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
                }
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE, feature.extraValue)
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE_ENABLED, enabled)
                putExtra(
                    HuaweiPodsAction.EXTRA_FREECLIP2_FEATURE_RESULT_RECEIVER,
                    freeClip2FeatureResultReceiver,
                )
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }.isSuccess
        if (!sent) {
            pendingFreeClip2Features.remove(feature)
            failedFreeClip2Features.add(feature)
            updateFreeClip2FeaturePreferences()
            Log.w(TAG, "Settings FreeClip 2 feature request send failed feature=$feature")
            return false
        }
        Log.i(TAG, "Settings FreeClip 2 feature requested address=$address feature=$feature enabled=$enabled")
        return true
    }

    private fun configureTransparencyModeView(
        root: View,
        modeContainer: View?,
        route: HuaweiDeviceRoute,
    ) {
        val matches = mutableListOf<TextView>()
        collectExactTextMatches(root, listOf("通透", "Transparency"), matches)
        matches.forEach { match ->
            val target = modeContainer
                ?.let { directChildBelowAncestor(it, match) }
                ?: bestHideTarget(root, match)
            if (target !== root && target !== modeContainer && !target.isSystemScrollingContainer()) {
                setSettingsCapabilityViewVisible(target, route.supportsTransparency)
            }
        }
    }

    private fun directChildBelowAncestor(ancestor: View, descendant: View): View? {
        var current: View = descendant
        while (true) {
            val parent = current.parent as? View ?: return null
            if (parent === ancestor) return current
            current = parent
        }
    }

    private fun replaceSettingsHeaderImage(root: View) {
        val bitmap = loadSettingsHeaderBitmap(root.context) ?: return
        val candidates = mutableListOf<ImageView>()
        collectHeadsetImageCandidates(root, candidates)
        candidates.forEach { imageView ->
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.adjustViewBounds = true
            Log.d(TAG, "Settings headset image replaced view=${imageView.javaClass.name} size=${imageView.width}x${imageView.height}")
        }
        if (candidates.isEmpty()) {
            Log.d(TAG, "Settings headset image candidate not found")
        }
    }

    private fun loadSettingsHeaderBitmap(context: Context): Bitmap? {
        val route = currentHuaweiRoute()
        val address = currentAddress?.takeIf(String::isNotBlank)
        val imagePreference = address?.let { runCatching { PodImagePrefs.find(prefs, it) }.getOrNull() }
        val key = SettingsHeaderImageKey(
            address = address.orEmpty().uppercase(),
            route = route,
            manualPath = imagePreference?.boxImagePath,
            cloudPath = imagePreference?.cloudBoxImagePath,
        )
        settingsHeaderBitmapCache
            ?.takeIf { it.key == key && !it.bitmap.isRecycled }
            ?.let { return it.bitmap }
        return runCatching {
            if (address != null) {
                PodImageLoader.loadBoxBitmap(context, prefs, address)
            } else {
                val moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
                if (!ModuleResourceResolver.isCurrentModuleBuild(moduleContext)) return null
                val resourceId = when (route) {
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS5 -> R.drawable.img_freebuds5_box
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> R.drawable.img_freebuds6i_settings
                    HuaweiDeviceRoute.HUAWEI_FREECLIP2 -> R.drawable.img_freeclip2_box
                    HuaweiDeviceRoute.HUAWEI_EYEWEAR2 -> R.drawable.img_eyewear2_box
                    else -> R.drawable.img_box
                }
                BitmapFactory.decodeResource(moduleContext.resources, resourceId)
            }
        }.onSuccess { bitmap ->
            if (bitmap != null) settingsHeaderBitmapCache = SettingsHeaderBitmapCache(key, bitmap)
            val source = when {
                imagePreference?.boxImagePath != null -> "manual"
                imagePreference?.cloudBoxImagePath != null -> "official"
                else -> "built-in"
            }
            Log.d(TAG, "Settings headset image loaded route=$route source=$source bitmap=${bitmap?.width}x${bitmap?.height}")
        }.onFailure {
            Log.w(TAG, "Settings headset image load failed", it)
        }.getOrNull()
    }

    private fun collectHeadsetImageCandidates(view: View, out: MutableList<ImageView>) {
        if (view is ImageView && view.isLargeSettingsImage()) {
            out.add(view)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectHeadsetImageCandidates(view.getChildAt(index), out)
            }
        }
    }

    private fun ImageView.isLargeSettingsImage(): Boolean {
        if (!isShown) return false
        val minSize = context.dp(88)
        val measuredLarge = width >= minSize && height >= minSize
        val params = layoutParams
        val declaredLarge = params != null && (
            (params.width >= minSize && params.height >= minSize) ||
                (params.width == ViewGroup.LayoutParams.MATCH_PARENT && params.height >= minSize)
            )
        return measuredLarge || declaredLarge
    }

    private fun replaceHuaweiAncLevelsWithHuaweiDial(root: View) {
        loadState()
        val route = currentHuaweiRoute()
        restoreFreeBuds6iTransparencyLabels(root)
        val existingDial = findTaggedView(root, SETTINGS_HUAWEI_DIAL_TAG) as? HuaweiAncLevelDialView
        val existingAncSelector =
            findTaggedView(root, SETTINGS_HUAWEI_ANC_SELECTOR_TAG) as? HuaweiAncSubModeSelectorView
        val existingTransparencySelector =
            findTaggedView(root, SETTINGS_HUAWEI_TRANSPARENCY_SELECTOR_TAG) as? HuaweiAncSubModeSelectorView
        val matches = mutableListOf<TextView>()
        collectTextMatches(root, ancLevelKeywords, matches)
        val anchorMatches = matches.filter { textView ->
            val text = textView.text?.toString().orEmpty()
            ancLevelAnchorKeywords.any { text.contains(it, ignoreCase = true) }
        }
        val levelAnchor = levelContainer(root, anchorMatches.ifEmpty { matches })
        if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            existingDial?.visibility = View.GONE
            existingAncSelector?.visibility = View.GONE
            existingTransparencySelector?.visibility = View.GONE
            return
        }
        if (!route.supportsAnc) {
            existingDial?.visibility = View.GONE
            existingAncSelector?.visibility = View.GONE
            existingTransparencySelector?.visibility = View.GONE
            levelAnchor?.let { setSettingsCapabilityViewVisible(it, false) }
            return
        }

        if (
            currentAnc == NoiseControlMode.TRANSPARENCY.broadcastStatus &&
            route.supportsTransparency
        ) {
            existingDial?.visibility = View.GONE
            existingAncSelector?.visibility = View.GONE
            if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
                existingTransparencySelector?.visibility = View.GONE
                val nativeAnchor = findFreeBuds6iNativeTransparencyAnchor(root)
                if (nativeAnchor != null) {
                    setSettingsCapabilityViewVisible(nativeAnchor, true)
                    relabelFreeBuds6iNativeTransparencyOptions(nativeAnchor)
                } else {
                    Log.w(TAG, "FreeBuds 6i native transparency selector not found")
                }
                return
            }
            levelAnchor?.let { setSettingsCapabilityViewVisible(it, false) }
            val anchor = levelAnchor ?: modeButtonContainer(root)
            if (anchor == null) {
                existingTransparencySelector?.visibility = View.GONE
                Log.d(TAG, "Settings transparency selector anchor not found route=$route")
                return
            }
            val selector = existingTransparencySelector ?: createHuaweiTransparencySelector(anchor)
            selector?.apply {
                render(
                    transparencySelectorOptions(anchor.context, route),
                    transparencySubMode(route),
                    isSettingsDarkMode(anchor.context),
                )
                visibility = View.VISIBLE
            }
            return
        }

        existingTransparencySelector?.visibility = View.GONE
        if (!route.supportsAncDirectionDial) {
            existingDial?.visibility = View.GONE
            val showDiscreteLevels = route.supportsDiscreteAncLevels &&
                currentAnc == NoiseControlMode.NOISE_CANCELLATION.broadcastStatus
            val useCustomSelector = showDiscreteLevels && usesCustomSettingsAncSelector(route)
            if (useCustomSelector) {
                levelAnchor?.let { setSettingsCapabilityViewVisible(it, false) }
                val anchor = levelAnchor ?: modeButtonContainer(root)
                if (anchor == null) {
                    existingAncSelector?.visibility = View.GONE
                    Log.d(TAG, "Settings Huawei ANC selector anchor not found route=$route")
                    return
                }
                val selector = existingAncSelector ?: createHuaweiAncSelector(anchor)
                selector?.apply {
                    render(
                        ancSelectorOptions(anchor.context, route),
                        ancSubMode(route),
                        isSettingsDarkMode(anchor.context),
                    )
                    visibility = View.VISIBLE
                }
                return
            }
            existingAncSelector?.visibility = View.GONE
            levelAnchor?.let { setSettingsCapabilityViewVisible(it, showDiscreteLevels) }
            return
        }
        existingAncSelector?.visibility = View.GONE
        if (!currentAnc.isSettingsNoiseCancellation()) {
            existingDial?.visibility = View.GONE
            return
        }

        val anchor = levelAnchor ?: modeButtonContainer(root)
        if (anchor == null) {
            existingDial?.visibility = View.GONE
            Log.d(TAG, "Settings Huawei ANC dial anchor not found matches=${matches.map { it.text }}")
            return
        }

        hideHuaweiAncLevelArea(root, anchor)

        val dial = existingDial ?: createHuaweiAncLevelDial(anchor)
        if (dial != null) {
            dial.setLevel(currentHuaweiAncLevel)
            dial.visibility = View.VISIBLE
        }
    }

    private fun currentHuaweiRoute(): HuaweiDeviceRoute {
        return currentRoute?.takeIf { it.isSupported }
            ?: resolveHuaweiDeviceRoute(currentAddress, currentName)
    }

    private fun createHuaweiAncLevelDial(anchor: View): HuaweiAncLevelDialView? {
        val parent = anchor.parent as? ViewGroup ?: return null
        if (parent.hasHuaweiAncDialChild()) {
            return parent.findHuaweiAncDialChild()
        }
        val dial = HuaweiAncLevelDialView(anchor.context) { level ->
            currentHuaweiAncLevel = level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
            saveState(anchor.context)
            sendHuaweiAncLevel(currentHuaweiAncLevel)
        }.apply {
            tag = SETTINGS_HUAWEI_DIAL_TAG
            setLevel(currentHuaweiAncLevel)
        }
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            anchor.context.dp(220)
        )
        runCatching {
            val index = parent.indexOfChild(anchor).takeIf { it >= 0 } ?: parent.childCount
            parent.addView(dial, index + 1, params)
            Log.d(TAG, "Settings Huawei ANC dial added parent=${parent.javaClass.name} anchor=${anchor.javaClass.name}")
            return dial
        }.onFailure { Log.w(TAG, "Settings Huawei ANC dial add failed", it) }
        return null
    }

    private fun createHuaweiTransparencySelector(anchor: View): HuaweiAncSubModeSelectorView? {
        val parent = anchor.parent as? ViewGroup ?: return null
        val existing = findTaggedView(parent, SETTINGS_HUAWEI_TRANSPARENCY_SELECTOR_TAG)
            as? HuaweiAncSubModeSelectorView
        if (existing != null) return existing
        val selector = HuaweiAncSubModeSelectorView(anchor.context) { subMode ->
            val route = currentHuaweiRoute()
            if (!route.supportsTransparency || subMode !in supportedTransparencySubModes(route)) {
                return@HuaweiAncSubModeSelectorView
            }
            val selection = SettingsAncSelection(NoiseControlMode.TRANSPARENCY.broadcastStatus, subMode)
            dispatchAncSelection(selection)
            schedulePruneFreeBudsUnsupportedViews(anchor.rootView)
        }.apply {
            tag = SETTINGS_HUAWEI_TRANSPARENCY_SELECTOR_TAG
        }
        return runCatching {
            val index = parent.indexOfChild(anchor).takeIf { it >= 0 } ?: parent.childCount
            parent.addView(
                selector,
                index + 1,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            selector
        }.onFailure { Log.w(TAG, "Settings transparency selector add failed", it) }
            .getOrNull()
    }

    private fun createHuaweiAncSelector(anchor: View): HuaweiAncSubModeSelectorView? {
        val parent = anchor.parent as? ViewGroup ?: return null
        val existing = findTaggedView(parent, SETTINGS_HUAWEI_ANC_SELECTOR_TAG)
            as? HuaweiAncSubModeSelectorView
        if (existing != null) return existing
        val selector = HuaweiAncSubModeSelectorView(anchor.context) { subMode ->
            val route = currentHuaweiRoute()
            if (!route.supportsDiscreteAncLevels || !route.supportsAncSubMode(subMode)) {
                return@HuaweiAncSubModeSelectorView
            }
            val selection = SettingsAncSelection(NoiseControlMode.NOISE_CANCELLATION.broadcastStatus, subMode)
            dispatchAncSelection(selection)
            schedulePruneFreeBudsUnsupportedViews(anchor.rootView)
        }.apply {
            tag = SETTINGS_HUAWEI_ANC_SELECTOR_TAG
        }
        return runCatching {
            val index = parent.indexOfChild(anchor).takeIf { it >= 0 } ?: parent.childCount
            parent.addView(
                selector,
                index + 1,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            selector
        }.onFailure { Log.w(TAG, "Settings Huawei ANC selector add failed", it) }
            .getOrNull()
    }

    private fun ancSelectorOptions(
        context: Context,
        route: HuaweiDeviceRoute,
    ): List<HuaweiAncSubModeSelectorView.Option> = route.ancLevelOptions.map { option ->
        val label = when (option.level) {
            HuaweiAncLevel.ADAPTIVE -> moduleString(context, R.string.anc_level_adaptive, "智慧动态")
            HuaweiAncLevel.LIGHT -> moduleString(context, R.string.anc_level_light, "轻度")
            HuaweiAncLevel.BALANCED -> moduleString(context, R.string.anc_level_balanced, "均衡")
            HuaweiAncLevel.DEEP -> moduleString(context, R.string.anc_level_deep, "深度")
        }
        HuaweiAncSubModeSelectorView.Option(option.protocolValue, label)
    }

    private fun transparencySelectorOptions(
        context: Context,
        route: HuaweiDeviceRoute,
    ): List<HuaweiAncSubModeSelectorView.Option> = listOf(
        HuaweiAncSubModeSelectorView.Option(
            defaultTransparencySubMode(route),
            moduleString(context, R.string.transparency_standard, "普通"),
        ),
        HuaweiAncSubModeSelectorView.Option(
            0x01,
            moduleString(context, R.string.transparency_voice, "人声增强"),
        ),
    )

    private fun isSettingsDarkMode(context: Context): Boolean =
        context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun hideHuaweiAncLevelArea(root: View, anchor: View) {
        setSettingsCapabilityViewVisible(anchor, false)
        Log.d(TAG, "Settings MIUI ANC level anchor hidden view=${anchor.javaClass.name}")
    }

    private fun setSettingsCapabilityViewVisible(
        view: View,
        visible: Boolean,
        collapseLayout: Boolean = false,
    ) {
        if (visible) {
            val original = hiddenSettingsCapabilityViews.remove(view) ?: return
            if (original.layoutCollapsed) {
                view.applySettingsRowLayoutState(original.layoutState)
            }
            view.visibility = original.visibility
            view.isEnabled = original.isEnabled
            view.isClickable = original.isClickable
            view.importantForAccessibility = original.importantForAccessibility
            view.requestLayout()
            return
        }
        hiddenSettingsCapabilityViews.getOrPut(view) {
            HiddenSettingsCapabilityView(
                visibility = view.visibility,
                isEnabled = view.isEnabled,
                isClickable = view.isClickable,
                importantForAccessibility = view.importantForAccessibility,
                layoutState = view.captureSettingsRowLayoutState(),
            )
        }
        val hiddenState = hiddenSettingsCapabilityViews.getValue(view)
        if (
            view.visibility != View.GONE ||
            view.isEnabled ||
            view.isClickable ||
            view.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO
        ) {
            hideViewOnly(view)
        }
        if (collapseLayout) {
            hiddenState.layoutCollapsed = true
            view.applySettingsRowLayoutState(hiddenState.layoutState.collapsed())
            view.requestLayout()
        }
    }

    private fun View.captureSettingsRowLayoutState(): SettingsRowLayoutState {
        val params = layoutParams
        val margins = params as? ViewGroup.MarginLayoutParams
        return SettingsRowLayoutState(
            height = params?.height,
            topMargin = margins?.topMargin,
            bottomMargin = margins?.bottomMargin,
            minimumHeight = minimumHeight,
        )
    }

    private fun View.applySettingsRowLayoutState(state: SettingsRowLayoutState) {
        val params = layoutParams
        if (params != null && state.height != null) {
            params.height = state.height
            if (params is ViewGroup.MarginLayoutParams) {
                state.topMargin?.let { params.topMargin = it }
                state.bottomMargin?.let { params.bottomMargin = it }
            }
            layoutParams = params
        }
        minimumHeight = state.minimumHeight
    }

    private fun restoreTrackedSettingsCapabilityViews(root: View) {
        hiddenSettingsCapabilityViews.keys
            .filter { view -> view === root || view.isDescendantOf(root) }
            .toList()
            .forEach { view -> setSettingsCapabilityViewVisible(view, true) }
    }

    private fun View.isDescendantOf(ancestor: View): Boolean {
        var current = parent as? View
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun hideViewOnly(view: View) {
        view.visibility = View.GONE
        view.isEnabled = false
        view.isClickable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.requestLayout()
    }

    private fun ViewGroup.hasHuaweiAncDialChild(): Boolean = findHuaweiAncDialChild() != null

    private fun ViewGroup.findHuaweiAncDialChild(): HuaweiAncLevelDialView? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.tag == SETTINGS_HUAWEI_DIAL_TAG && child is HuaweiAncLevelDialView) {
                return child
            }
        }
        return null
    }

    private fun levelContainer(root: View, matches: List<TextView>): View? {
        if (matches.isEmpty()) return null
        val common = commonAncestor(root, matches)
        if (common != null && common !== root && !common.isSystemScrollingContainer()) {
            return common
        }
        return bestHideTarget(root, matches.first()).takeIf { it !== root && !it.isSystemScrollingContainer() }
    }

    private fun modeButtonContainer(root: View): View? {
        root.hostView("ancLayoutInfo")?.let { return it }
        val matches = mutableListOf<TextView>()
        collectAncModeTextMatches(root, matches)
        val common = commonAncestor(root, matches)
        if (common != null && common !== root && !common.isSystemScrollingContainer()) {
            return common
        }
        return matches.firstOrNull()?.let { bestHideTarget(root, it) }
            ?.takeIf { it !== root && !it.isSystemScrollingContainer() }
    }

    private fun collectAncModeTextMatches(view: View, out: MutableList<TextView>) {
        if (view is TextView) {
            val text = view.text?.toString()?.trim().orEmpty()
            val contentDescription = view.contentDescription?.toString()?.trim().orEmpty()
            if (ancModeKeywords.any {
                    it.equals(text, ignoreCase = true) || it.equals(contentDescription, ignoreCase = true)
                }
            ) {
                out.add(view)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectAncModeTextMatches(view.getChildAt(index), out)
            }
        }
    }

    private fun commonAncestor(root: View, views: List<View>): View? {
        if (views.isEmpty()) return null
        val firstChain = ancestorChain(root, views.first())
        val otherChains = views.drop(1).map { ancestorChain(root, it).toSet() }
        return firstChain.firstOrNull { candidate ->
            candidate !== root && otherChains.all { candidate in it }
        }
    }

    private fun ancestorChain(root: View, view: View): List<View> {
        val result = mutableListOf<View>()
        var current: View? = view
        while (current != null) {
            result.add(current)
            if (current === root) break
            current = current.parent as? View
        }
        return result
    }

    private fun View.isSystemScrollingContainer(): Boolean {
        val className = javaClass.name
        return className.contains("RecyclerView") ||
            className.contains("ScrollView") ||
            className.contains("ListView")
    }

    private fun findTaggedView(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findTaggedView(view.getChildAt(index), tag)
                if (found != null) return found
            }
        }
        return null
    }

    private fun collectTextMatches(view: View, keywords: List<String>, out: MutableList<TextView>) {
        if (
            view.tag == SETTINGS_HUAWEI_ANC_SELECTOR_TAG ||
            view.tag == SETTINGS_HUAWEI_TRANSPARENCY_SELECTOR_TAG ||
            view.tag == SETTINGS_HUAWEI_DIAL_TAG
        ) {
            return
        }
        if (view is TextView) {
            val text = view.text?.toString().orEmpty()
            val contentDescription = view.contentDescription?.toString().orEmpty()
            if (keywords.any { text.contains(it, ignoreCase = true) || contentDescription.contains(it, ignoreCase = true) }) {
                out.add(view)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectTextMatches(view.getChildAt(index), keywords, out)
            }
        }
    }

    private fun Int.isSettingsNoiseCancellation(): Boolean = this == 2 || this in 5..8

    private fun bestHideTarget(root: View, textView: TextView): View {
        recyclerItemHideTarget(root, textView)?.let { return it }
        var target: View = textView
        var parent = textView.parent as? ViewGroup
        var depth = 0
        while (parent != null && parent !== root && depth < 4) {
            val parentClass = parent.javaClass.name
            if (
                parentClass.contains("RecyclerView") ||
                parentClass.contains("ScrollView") ||
                parentClass.contains("ListView")
            ) {
                break
            }
            val grandParent = parent.parent as? ViewGroup
            if (grandParent?.isRecyclingRowsContainer() == true) break
            if (parent.isClickable || parent.isFocusable || (grandParent != null && grandParent.childCount > 1)) {
                target = parent
                break
            }
            target = parent
            parent = grandParent
            depth++
        }
        return target
    }

    private fun findFreeBuds6iNativeTransparencyAnchor(root: View): View? {
        val matches = mutableListOf<TextView>()
        collectNativeTransparencyTextMatches(
            root,
            listOf(
                "人声增强",
                "透传模式",
                "Voice enhancement",
                "Transparency mode",
            ),
            matches,
        )
        if (matches.size < 2) return null
        return commonAncestor(root, matches)
            ?.takeIf { it !== root && !it.isSystemScrollingContainer() }
    }

    private fun collectNativeTransparencyTextMatches(
        view: View,
        values: List<String>,
        out: MutableList<TextView>,
    ) {
        if (
            view.tag == SETTINGS_HUAWEI_TRANSPARENCY_SELECTOR_TAG ||
            view.tag == SETTINGS_HUAWEI_ANC_SELECTOR_TAG
        ) return
        if (view is TextView) {
            val text = view.text?.toString()?.trim().orEmpty()
            val contentDescription = view.contentDescription?.toString()?.trim().orEmpty()
            if (values.any { it.equals(text, ignoreCase = true) || it.equals(contentDescription, ignoreCase = true) }) {
                out.add(view)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectNativeTransparencyTextMatches(view.getChildAt(index), values, out)
            }
        }
    }

    private fun relabelFreeBuds6iNativeTransparencyOptions(anchor: View) {
        val labels = mutableListOf<TextView>()
        collectExactTextMatches(
            anchor,
            listOf(
                "人声增强",
                "透传模式",
                "Voice enhancement",
                "Transparency mode",
            ),
            labels,
        )
        labels.forEach { label ->
            val original = label.text ?: return@forEach
            relabeledFreeBuds6iTransparencyTexts.putIfAbsent(label, original)
            label.text = when (original.toString().trim()) {
                "人声增强", "Voice enhancement" ->
                    moduleString(label.context, R.string.transparency_standard, "普通")
                "透传模式", "Transparency mode" ->
                    moduleString(label.context, R.string.transparency_voice, "人声增强")
                else -> original
            }
        }
    }

    private fun restoreFreeBuds6iTransparencyLabels(root: View) {
        relabeledFreeBuds6iTransparencyTexts.keys
            .filter { view -> view === root || view.isDescendantOf(root) }
            .toList()
            .forEach { view ->
                relabeledFreeBuds6iTransparencyTexts.remove(view)?.let { view.text = it }
            }
    }

    /**
     * RecyclerView/ListView 不会替内部 GONE 子树自动移除 ViewHolder 占位；
     * 对确认只承载一行设置的 item 隐藏直接子 View，复用时由现有追踪表恢复原状态。
     */
    private fun recyclerItemHideTarget(root: View, textView: TextView): View? {
        var child: View = textView
        var parent = child.parent as? ViewGroup
        var depth = 0
        while (parent != null && depth < 12) {
            if (parent.isRecyclingRowsContainer()) {
                val interactiveDescendants = countTopLevelInteractiveDescendants(child, limit = 2)
                return child.takeIf {
                    shouldCollapseSettingsRecyclerItem(
                        itemInteractive = child.isClickable || child.isFocusable,
                        topLevelInteractiveDescendantCount = interactiveDescendants,
                    )
                }
            }
            if (parent === root || parent.isNonRecyclingScrollContainer()) return null
            child = parent
            parent = parent.parent as? ViewGroup
            depth++
        }
        return null
    }

    private fun countTopLevelInteractiveDescendants(view: View, limit: Int): Int {
        if (view !is ViewGroup || limit <= 0) return 0
        var count = 0
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (child.isClickable || child.isFocusable) {
                count++
            } else {
                count += countTopLevelInteractiveDescendants(child, limit - count)
            }
            if (count >= limit) return limit
        }
        return count
    }

    private fun View.isRecyclingRowsContainer(): Boolean {
        val className = javaClass.name
        return className.contains("RecyclerView") || className.contains("ListView")
    }

    private fun View.isNonRecyclingScrollContainer(): Boolean =
        javaClass.name.contains("ScrollView")

    private fun sendHuaweiAncLevel(level: Int) {
        if (!currentHuaweiRoute().supportsAnc) {
            Log.w(TAG, "sendHuaweiAncLevel skipped: current route does not support ANC")
            return
        }
        val ctx = context ?: run {
            Log.w(TAG, "sendHuaweiAncLevel skipped: context is null level=$level")
            return
        }
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET).apply {
            currentAddress?.let { putExtra("address", it) }
            currentName?.let { putExtra("device_name", it) }
            encodeHuaweiDeviceRouteForBroadcast(currentHuaweiRoute())?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra("level", level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST))
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "Huawei ANC level requested from settings level=$level")
    }

    private class HuaweiAncLevelDialView(
        context: Context,
        private val onLevelChange: (Int) -> Unit
    ) : View(context) {
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = context.dp(1).toFloat()
            color = Color.argb(80, 36, 42, 54)
        }
        private val activeTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = context.dp(2).toFloat()
            color = Color.rgb(0, 122, 255)
        }
        private val diskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(12, 36, 42, 54)
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1.5f)
            color = Color.argb(32, 36, 42, 54)
        }
        private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1f)
            color = Color.argb(46, 255, 255, 255)
        }
        private val knobHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(42, 0, 122, 255)
        }
        private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(0, 122, 255)
        }
        private var level = 0

        init {
            isClickable = true
            minimumHeight = context.dp(200)
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        }

        fun setLevel(nextLevel: Int) {
            val safeLevel = nextLevel.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
            if (safeLevel == level) return
            level = safeLevel
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desiredHeight = context.dp(220)
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = resolveSize(desiredHeight, heightMeasureSpec)
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
            val contentHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
            val centerX = paddingLeft + contentWidth / 2f
            val centerY = paddingTop + contentHeight / 2f
            val radius = min(contentWidth, contentHeight) * 0.32f
            val innerTickRadius = radius + context.dp(8)
            val outerTickRadius = radius + context.dp(19)
            val selectedTick = level.toDialTick()

            canvas.drawCircle(centerX, centerY, radius * 1.08f, diskPaint)
            canvas.drawCircle(centerX, centerY, radius, ringPaint)
            canvas.drawCircle(centerX, centerY, radius * 0.72f, innerRingPaint)

            repeat(HUAWEI_ANC_DIAL_TICKS) { tick ->
                val major = tick % HUAWEI_ANC_TICKS_PER_LEVEL == 0
                val highlighted = circularDistance(tick, selectedTick, HUAWEI_ANC_DIAL_TICKS) <= 2
                val radians = Math.toRadians(tick * HUAWEI_ANC_DIAL_TICK_DEGREES.toDouble())
                val startRadius = if (major) innerTickRadius - context.dp(3) else innerTickRadius
                val startX = centerX + cos(radians).toFloat() * startRadius
                val startY = centerY + sin(radians).toFloat() * startRadius
                val endX = centerX + cos(radians).toFloat() * outerTickRadius
                val endY = centerY + sin(radians).toFloat() * outerTickRadius
                canvas.drawLine(startX, startY, endX, endY, if (highlighted) activeTickPaint else tickPaint)
            }

            val knobRadians = Math.toRadians(level.toDialDegrees().toDouble())
            val knobX = centerX + cos(knobRadians).toFloat() * radius * 0.86f
            val knobY = centerY + sin(knobRadians).toFloat() * radius * 0.86f
            val knobRadius = context.dp(15)
            canvas.drawCircle(knobX, knobY, knobRadius * 1.35f, knobHaloPaint)
            canvas.drawCircle(knobX, knobY, knobRadius.toFloat(), knobPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateLevelFromTouch(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    updateLevelFromTouch(event.x, event.y)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun updateLevelFromTouch(x: Float, y: Float) {
            val nextLevel = touchToHuaweiAncLevel(x, y, width.toFloat(), height.toFloat())
            if (nextLevel == level) return
            level = nextLevel
            invalidate()
            onLevelChange(nextLevel)
        }
    }

    private fun touchToHuaweiAncLevel(x: Float, y: Float, width: Float, height: Float): Int {
        val dx = x - width / 2f
        val dy = y - height / 2f
        val degrees = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        val normalized = (degrees - HUAWEI_ANC_DIAL_START_DEGREES + 360f) % 360f
        return ((normalized / (360f / (HUAWEI_ANC_LEVEL_LAST + 1))).roundToInt()) % (HUAWEI_ANC_LEVEL_LAST + 1)
    }

    private fun Int.toDialDegrees(): Float = HUAWEI_ANC_DIAL_START_DEGREES + (this * 360f / (HUAWEI_ANC_LEVEL_LAST + 1))

    private fun Int.toDialTick(): Int = ((toDialDegrees() / HUAWEI_ANC_DIAL_TICK_DEGREES).roundToInt()) % HUAWEI_ANC_DIAL_TICKS

    private fun circularDistance(a: Int, b: Int, modulo: Int): Int {
        val distance = abs(a - b)
        return min(distance, modulo - distance)
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

    private fun dispatchAncSelection(selection: SettingsAncSelection): Boolean {
        val route = currentHuaweiRoute()
        if (!route.supportsAnc) {
            Log.w(TAG, "Settings ANC selection ignored for non-ANC route=$route selection=$selection")
            return false
        }
        if (!settingsAncPendingGate.tryBegin(selection, SystemClock.elapsedRealtime())) {
            Log.d(
                TAG,
                "Settings ANC render/duplicate suppressed selection=$selection " +
                    "confirmed=${settingsAncPendingGate.lastConfirmed()}",
            )
            return false
        }
        applyAncSelection(selection)
        val waitsForReadback = route.supportsAncStateReadback
        currentAncConfirmed = !waitsForReadback
        saveState(context)
        if (!sendHuaweiAnc(selection)) {
            settingsAncPendingGate.clearPending()
            currentAncConfirmed = false
            Log.w(TAG, "Settings ANC selection could not be sent selection=$selection")
            return false
        }
        if (!waitsForReadback) {
            sendAncChanged(selection)
        }
        return true
    }

    private inline fun <T> withInternalSettingsAncRender(block: () -> T): T {
        settingsAncInternalRenderDepth += 1
        return try {
            block()
        } finally {
            settingsAncInternalRenderDepth -= 1
        }
    }

    private fun collectExactTextMatches(view: View, values: List<String>, out: MutableList<TextView>) {
        if (view is TextView) {
            val text = view.text?.toString()?.trim().orEmpty()
            val contentDescription = view.contentDescription?.toString()?.trim().orEmpty()
            if (values.any { it.equals(text, ignoreCase = true) || it.equals(contentDescription, ignoreCase = true) }) {
                out.add(view)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectExactTextMatches(view.getChildAt(index), values, out)
            }
        }
    }

    private fun sendHuaweiAnc(selection: SettingsAncSelection): Boolean {
        val ctx = context ?: run {
            Log.w(TAG, "sendHuaweiAnc skipped: context is null selection=$selection")
            return false
        }
        return runCatching {
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_ANC_SELECT).apply {
                currentAddress?.let { putExtra("address", it) }
                currentName?.let { putExtra("device_name", it) }
                encodeHuaweiDeviceRouteForBroadcast(currentHuaweiRoute())?.let {
                    putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
                }
                putExtra("status", selection.status)
                selection.subMode?.let { putExtra("submode", it) }
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
            true
        }.onFailure {
            Log.w(TAG, "sendHuaweiAnc failed selection=$selection", it)
        }.getOrDefault(false)
    }

    private fun sendAncChanged(selection: SettingsAncSelection) {
        val ctx = context ?: return
        listOf(BuildConfig.APPLICATION_ID, "com.android.settings", "com.milink.service").forEach { targetPackage ->
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED).apply {
                currentAddress?.let { putExtra("address", it) }
                currentName?.let { putExtra("device_name", it) }
                encodeHuaweiDeviceRouteForBroadcast(currentHuaweiRoute())?.let {
                    putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
                }
                putExtra("status", selection.status)
                selection.subMode?.let { putExtra("submode", it) }
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableDevice(key: String): BluetoothDevice? {
        return runCatching { getParcelableExtra(key, BluetoothDevice::class.java) }.getOrNull()
            ?: runCatching { getParcelableExtra<BluetoothDevice>(key) }.getOrNull()
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
        prefs.edit()
            .putString("address", currentAddress)
            .putString("name", currentName)
            .putString(PREF_DEVICE_ROUTE, encodeHuaweiDeviceRouteForBroadcast(currentHuaweiRoute()))
            .putInt("anc", currentAnc)
            .putInt("huawei_anc_level", currentHuaweiAncLevel)
            .putInt("transparency_submode", currentTransparencySubMode)
            .putInt("left_battery", currentBattery.left?.battery ?: 0)
            .putBoolean("left_charging", currentBattery.left?.isCharging == true)
            .putBoolean("left_connected", currentBattery.left?.isConnected == true)
            .putInt("right_battery", currentBattery.right?.battery ?: 0)
            .putBoolean("right_charging", currentBattery.right?.isCharging == true)
            .putBoolean("right_connected", currentBattery.right?.isConnected == true)
            .putInt("case_battery", currentBattery.case?.battery ?: 0)
            .putBoolean("case_charging", currentBattery.case?.isCharging == true)
            .putBoolean("case_connected", currentBattery.case?.isConnected == true)
            .apply()
    }

    private fun saveCurrentFreeClip2AudioState(ctx: Context?) {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val prefix = freeClip2AudioPreferencePrefix(currentAddress, currentName) ?: return
        val prefs = (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putString(prefix + HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_MODE, currentFreeClip2AudioState.spatialMode.extraValue)
            .putString(prefix + HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_SCENE, currentFreeClip2AudioState.spatialScene.extraValue)
            .putString(prefix + HuaweiPodsAction.EXTRA_FREECLIP2_SOUND_EFFECT, currentFreeClip2AudioState.soundEffect.extraValue)
            .putInt(
                prefix + HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID,
                currentFreeClip2AudioState.equalizerPresetId,
            )
            .putString(
                prefix + HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME,
                currentFreeClip2AudioState.equalizerName,
            )
            .putString(
                prefix + HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS,
                currentFreeClip2AudioState.equalizerGains.joinToString(","),
            )
            .apply()
    }

    private fun loadCurrentFreeClip2AudioState() {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            currentFreeClip2AudioState = FreeClip2AudioUiState()
            freeClip2AudioPendingGate.clear()
            return
        }
        val prefix = freeClip2AudioPreferencePrefix(currentAddress, currentName) ?: return
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        currentFreeClip2AudioState = FreeClip2AudioUiState().mergeExtraValues(
            spatialModeValue = prefs.getString(prefix + HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_MODE, null),
            spatialSceneValue = prefs.getString(prefix + HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_SCENE, null),
            soundEffectValue = prefs.getString(prefix + HuaweiPodsAction.EXTRA_FREECLIP2_SOUND_EFFECT, null),
            equalizerPresetIdValue = prefs.getInt(
                prefix + HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID,
                -1,
            ).takeIf { it in 0x64..0x66 },
            equalizerNameValue = prefs.getString(
                prefix + HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME,
                null,
            ),
            equalizerGainsValue = parseFreeClip2EqualizerGains(
                prefs.getString(prefix + HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS, null),
            ),
        )
    }

    private fun saveCurrentFreeClip2FeatureState(
        ctx: Context?,
        feature: FreeClip2BooleanFeature,
    ) {
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val enabled = currentFreeClip2FeatureStates[feature] ?: return
        val prefix = freeClip2AudioPreferencePrefix(currentAddress, currentName) ?: return
        val prefs = (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putBoolean(prefix + "feature_" + feature.extraValue, enabled)
            .apply()
    }

    private fun loadCurrentFreeClip2FeatureStates() {
        currentFreeClip2FeatureStates.clear()
        confirmedFreeClip2Features.clear()
        pendingFreeClip2Features.clear()
        failedFreeClip2Features.clear()
        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val prefix = freeClip2AudioPreferencePrefix(currentAddress, currentName) ?: return
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        FreeClip2BooleanFeature.entries.forEach { feature ->
            val key = prefix + "feature_" + feature.extraValue
            if (prefs.contains(key)) {
                currentFreeClip2FeatureStates[feature] = prefs.getBoolean(key, false)
            }
        }
    }

    private fun loadState() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val hasPersistedIdentity = prefs.contains("address") || prefs.contains("name")
        val persistedAddress = prefs.getString("address", null)
        val persistedName = prefs.getString("name", null)
        val persistedRoute = decodeHuaweiDeviceRouteFromBroadcast(
            prefs.getString(PREF_DEVICE_ROUTE, null),
        ) ?: resolveHuaweiDeviceRoute(persistedAddress, persistedName).takeIf { it.isSupported }
        val activeAddress = currentAddress?.takeIf(String::isNotBlank)
        val activeName = currentName?.takeIf(String::isNotBlank)
        val activeRoute = currentRoute?.takeIf { it.isSupported }
        val hasActivatedDevice = activeRoute != null && (activeAddress != null || activeName != null)
        if (hasActivatedDevice) {
            val identityMatches = when {
                activeAddress != null -> persistedAddress?.equals(activeAddress, ignoreCase = true) == true
                else -> persistedAddress.isNullOrBlank() && persistedName == activeName
            }
            if (!identityMatches || persistedRoute != activeRoute) {
                Log.d(
                    TAG,
                    "ignored stale persisted state active=$activeName/$activeAddress/$activeRoute " +
                        "persisted=${persistedName.orEmpty()}/${persistedAddress.orEmpty()}/$persistedRoute",
                )
                return
            }
        }
        if (hasPersistedIdentity && persistedRoute == null) {
            currentAddress = null
            currentName = null
            currentRoute = null
            currentBattery = BatteryParams()
            currentAnc = 1
            currentAncConfirmed = false
            currentHuaweiAncLevel = UNKNOWN_HUAWEI_ANC_SUBMODE
            currentTransparencySubMode = 0x02
            currentFreeClip2AudioState = FreeClip2AudioUiState()
            currentFreeClip2FeatureStates.clear()
            confirmedFreeClip2Features.clear()
            pendingFreeClip2Features.clear()
            failedFreeClip2Features.clear()
            freeClip2AudioPendingGate.clear()
            knownHuaweiAddresses.clear()
            prefs.edit()
                .remove("address")
                .remove("name")
                .remove(PREF_DEVICE_ROUTE)
                .remove("anc")
                .remove("huawei_anc_level")
                .remove("transparency_submode")
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
        val hasSavedBattery = prefs.getBoolean("left_connected", false) ||
            prefs.getBoolean("right_connected", false) ||
            prefs.getBoolean("case_connected", false)
        currentAddress = prefs.getString("address", currentAddress)
        currentName = prefs.getString("name", currentName)
        currentRoute = persistedRoute ?: currentRoute?.takeIf { it.isSupported }
        currentAnc = prefs.getInt("anc", currentAnc)
        val savedAncLevel = prefs.getInt("huawei_anc_level", currentHuaweiAncLevel)
        val route = currentHuaweiRoute()
        currentHuaweiAncLevel = if (route.supportsDiscreteAncLevels) {
            savedAncLevel.takeIf(route::supportsAncSubMode)
                ?: route.defaultAncSubMode
                ?: 0
        } else {
            savedAncLevel.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
        }
        currentTransparencySubMode = prefs.getInt("transparency_submode", currentTransparencySubMode)
        loadCurrentFreeClip2AudioState()
        if (currentFreeClip2FeatureStates.isEmpty() &&
            confirmedFreeClip2Features.isEmpty() &&
            pendingFreeClip2Features.isEmpty() &&
            failedFreeClip2Features.isEmpty()
        ) {
            loadCurrentFreeClip2FeatureStates()
        }
        currentAddress?.takeIf(String::isNotBlank)?.let { address ->
            currentRoute?.takeIf { it.isSupported }?.let { route ->
                knownHuaweiAddresses[address.uppercase()] = route
            }
        }
        if (!hasSavedBattery && hasCurrentBattery()) return
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
        )
    }

    private fun hasCurrentBattery(): Boolean {
        return currentBattery.left?.isConnected == true ||
            currentBattery.right?.isConnected == true ||
            currentBattery.case?.isConnected == true
    }

    private fun Bundle.parcelableDevice(key: String): BluetoothDevice? {
        return runCatching { getParcelable(key, BluetoothDevice::class.java) }.getOrNull()
            ?: runCatching { @Suppress("DEPRECATION") getParcelable<BluetoothDevice>(key) }.getOrNull()
    }
}
