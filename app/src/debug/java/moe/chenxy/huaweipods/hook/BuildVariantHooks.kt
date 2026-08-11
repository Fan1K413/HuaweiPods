package moe.chenxy.huaweipods.hook

import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import moe.chenxy.huaweipods.debugcapture.AiLifeCaptureHook
import moe.chenxy.huaweipods.debugcapture.SmartAudioCaptureTarget

internal object BuildVariantHooks {
    fun onPackageLoaded(entry: HookEntry, param: PackageLoadedParam) {
        if (!SmartAudioCaptureTarget.isAllowedSender(param.packageName)) return
        entry.loadHook(AiLifeCaptureHook, param.defaultClassLoader, param.packageName)
        entry.loadHook(SmartAudioFreeClip2BridgeHook, param.defaultClassLoader, param.packageName)
    }
}
