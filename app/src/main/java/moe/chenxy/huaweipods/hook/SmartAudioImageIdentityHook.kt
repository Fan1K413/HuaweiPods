package moe.chenxy.huaweipods.hook

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import moe.chenxy.huaweipods.smartaudio.SmartAudioImageCache
import moe.chenxy.huaweipods.smartaudio.SmartAudioResourceIdentityPolicy

/** 把智慧音频已确认的当前设备资源身份安全交给模块进程，不观察或修改协议流量。 */
internal object SmartAudioImageIdentityHook : HookContext() {
    private const val TAG = "HuaweiPods-SmartAudioImage"
    private const val SETTLE_DELAY_MS = 100L

    private val installed = AtomicBoolean(false)
    private val publishScheduled = AtomicBoolean(false)
    private val mainHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(Looper.getMainLooper())
    }

    @Volatile
    private var applicationContext: Context? = null

    override fun onHook() {
        if (!installed.compareAndSet(false, true)) return
        hookCurrentDeviceIdentityUpdates()
        installApplicationBridge()
        Log.i(TAG, "Official Smart Audio image identity bridge enabled")
    }

    private fun installApplicationBridge() {
        resolveApplicationContext()?.let { context ->
            applicationContext = context
            schedulePublish()
            return
        }
        runCatching {
            val attachMethod = Application::class.java.getDeclaredMethod("attach", Context::class.java)
                .apply { isAccessible = true }
            hookAfter(attachMethod) {
                applicationContext = ((instance as? Application)?.applicationContext
                    ?: args.firstOrNull() as? Context)?.applicationContext
                schedulePublish()
            }
        }.onFailure { Log.w(TAG, "Unable to install Smart Audio application bridge", it) }
    }

    private fun hookCurrentDeviceIdentityUpdates() {
        val adapterClass = runCatching {
            findClass("com.huawei.audiodevicekit.devicerouter.DefaultCurrentDeviceBusAdapter")
        }.onFailure {
            Log.w(TAG, "Smart Audio current-device adapter unavailable", it)
        }.getOrNull() ?: return
        adapterClass.declaredMethods.filter { method ->
            SmartAudioResourceIdentityPolicy.isIdentityMutation(
                methodName = method.name,
                parameterTypeNames = method.parameterTypes.map { it.name },
            )
        }.forEach { method ->
            runCatching {
                method.isAccessible = true
                hookAfter(method) { schedulePublish() }
            }.onFailure { Log.w(TAG, "Unable to hook Smart Audio identity update", it) }
        }
    }

    private fun schedulePublish() {
        if (!publishScheduled.compareAndSet(false, true)) return
        mainHandler.postDelayed(
            {
                publishScheduled.set(false)
                publishCurrentIdentity()
            },
            SETTLE_DELAY_MS,
        )
    }

    private fun publishCurrentIdentity() {
        val context = applicationContext ?: resolveApplicationContext()?.also {
            applicationContext = it
        } ?: return
        runCatching {
            val pluginClass = findClass("com.huawei.audiodevicekit.kitutils.plugin.Plugin")
            val currentDeviceBusClass = findClass("q7.a")
            val getPlugin = pluginClass.declaredMethods.firstOrNull { method ->
                method.name == "get" &&
                    method.parameterTypes.contentEquals(arrayOf(Class::class.java))
            } ?: return@runCatching
            getPlugin.isAccessible = true
            val currentDeviceBus = getPlugin.invoke(null, currentDeviceBusClass) ?: return@runCatching
            val deviceInfo = invokeNoArg(currentDeviceBus, "getDeviceInfo")
            val identity = SmartAudioResourceIdentityPolicy.normalize(
                address = invokeString(currentDeviceBus, "T0")
                    ?: deviceInfo?.let { invokeString(it, "c") },
                modelId = invokeString(currentDeviceBus, "c1")
                    ?: deviceInfo?.let { invokeString(it, "g") },
                subModelId = invokeString(currentDeviceBus, "Z0")
                    ?: deviceInfo?.let { invokeString(it, "o") },
            ) ?: return@runCatching
            val extras = Bundle().apply {
                putString(SmartAudioImageCache.EXTRA_ADDRESS, identity.address)
                putString(SmartAudioImageCache.EXTRA_MODEL_ID, identity.modelId)
                putString(SmartAudioImageCache.EXTRA_SUB_MODEL_ID, identity.subModelId)
            }
            context.contentResolver.call(
                SmartAudioImageCache.providerUri,
                SmartAudioImageCache.PROVIDER_METHOD_RECORD_IDENTITY,
                null,
                extras,
            )
        }.onFailure {
            // 智慧音频内部实现可能随版本变化；失败仅保留模块内置图片。
            Log.d(TAG, "Current Smart Audio image identity unavailable", it)
        }
    }

    private fun resolveApplicationContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Application
    }.getOrNull()?.applicationContext

    private fun invokeNoArg(target: Any, methodName: String): Any? = runCatching {
        target.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.isEmpty()
        }?.invoke(target)
    }.getOrNull()

    private fun invokeString(target: Any, methodName: String): String? =
        (invokeNoArg(target, methodName) as? String)?.trim()?.takeIf(String::isNotEmpty)
}
