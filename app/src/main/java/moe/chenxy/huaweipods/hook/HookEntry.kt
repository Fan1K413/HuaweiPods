package moe.chenxy.huaweipods.hook

import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.hook.milink.MiLinkServiceHook
import moe.chenxy.huaweipods.pods.HuaweiDeviceRouteResolver

open class HookEntry : XposedModule() {
    private val TAG = "HuaweiPods-HookEntry"
    private val configListeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        BuildVariantHooks.onPackageLoaded(this, param)

        when (param.packageName) {
            "com.android.bluetooth" -> {
                loadHook(HeadsetStateDispatcher, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook(), param.defaultClassLoader, param.packageName)
            }
            "com.android.settings" -> loadHook(SettingsHeadsetHook, param.defaultClassLoader, param.packageName)
            "com.milink.service" -> loadHook(MiLinkServiceHook, param.defaultClassLoader, param.packageName)
            "com.xiaomi.bluetooth" -> {
                loadHook(MiBluetoothToastHook, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook(), param.defaultClassLoader, param.packageName)
            }
        }
    }

    internal fun loadHook(hook: HookContext, classLoader: ClassLoader, packageName: String) {
        Log.module = this
        hook.module = this
        hook.appClassLoader = classLoader
        hook.packageName = packageName
        hook.prefs = getRemotePreferences(ConfigManager.PREFS_NAME)
        LowLatencyPrefs.attachHookPreferences(hook.prefs)
        HuaweiDeviceRouteResolver.init(hook.prefs)
        Log.d(TAG, "loadHook package=$packageName hook=${hook.javaClass.simpleName}")
        ConfigManager.init(hook.prefs)
        val configListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == ConfigManager.PREF_KEY_CONFIG_JSON) {
                ConfigManager.refreshFromPrefs(sharedPreferences)
            }
            if (DeviceRoutePrefs.isBindingKey(key)) {
                HuaweiDeviceRouteResolver.refreshBindings()
            }
        }
        configListeners.add(configListener)
        hook.prefs.registerOnSharedPreferenceChangeListener(configListener)
        hook.onHook()
    }
}
