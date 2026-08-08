package moe.chenxy.huaweipods.hook

import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

internal object BuildVariantHooks {
    @Suppress("UNUSED_PARAMETER")
    fun onPackageLoaded(entry: HookEntry, param: PackageLoadedParam) = Unit
}
