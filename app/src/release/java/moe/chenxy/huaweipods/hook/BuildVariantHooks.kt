package moe.chenxy.huaweipods.hook

import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

internal object BuildVariantHooks {
    fun onPackageLoaded(entry: HookEntry, param: PackageLoadedParam) {
        if (param.packageName == "com.huawei.smartaudio") {
            entry.loadHook(SmartAudioImageIdentityHook, param.defaultClassLoader, param.packageName)
        }
    }
}
