package moe.chenxy.huaweipods

import android.app.Application
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.smartaudio.SmartAudioImageCache

class HuaweiPodsApp : Application(), XposedServiceHelper.OnServiceListener {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        runCatching { SmartAudioImageCache.resumePending(this) }
            .onFailure { Log.w(TAG, "Unable to resume official image jobs", it) }
    }

    override fun onServiceBind(service: XposedService) {
        Log.d(TAG, "LSPosed service bound api=${service.apiVersion} framework=${service.frameworkName}/${service.frameworkVersionCode}")
        xposedService = service
        runCatching {
            DeviceRoutePrefs.syncWithRemote(
                prefs = getSharedPreferences(ConfigManager.PREFS_NAME, MODE_PRIVATE),
                service = service,
            )
            LowLatencyPrefs.syncWithRemote(
                prefs = getSharedPreferences(ConfigManager.PREFS_NAME, MODE_PRIVATE),
                service = service,
            )
            PodImagePrefs.syncSnapshotToRemote(
                prefs = getSharedPreferences(ConfigManager.PREFS_NAME, MODE_PRIVATE),
                service = service,
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to synchronize local pod images to remote preferences", error)
        }
        notifyListeners(service)
    }

    override fun onServiceDied(service: XposedService) {
        if (xposedService == service) {
            Log.d(TAG, "LSPosed service died")
            xposedService = null
            notifyListeners(null)
        }
    }

    private fun notifyListeners(service: XposedService?) {
        listeners.forEach { it(service) }
    }

    companion object {
        private const val TAG = "HuaweiPods-App"

        @Volatile
        var xposedService: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<(XposedService?) -> Unit>()

        fun addServiceListener(listener: (XposedService?) -> Unit) {
            listeners.add(listener)
            listener(xposedService)
        }

        fun removeServiceListener(listener: (XposedService?) -> Unit) {
            listeners.remove(listener)
        }
    }
}
