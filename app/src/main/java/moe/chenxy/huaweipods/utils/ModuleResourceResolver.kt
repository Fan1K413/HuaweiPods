package moe.chenxy.huaweipods.utils

import android.content.Context
import android.content.pm.PackageManager
import moe.chenxy.huaweipods.BuildConfig

/** 检测 APK 覆盖安装后，宿主进程是否仍在运行旧版 Hook dex。 */
object ModuleResourceResolver {
    fun createModuleContext(hostContext: Context): Context? = runCatching {
        hostContext.createPackageContext(
            BuildConfig.APPLICATION_ID,
            Context.CONTEXT_IGNORE_SECURITY,
        )
    }.getOrNull()

    fun isCurrentModuleBuild(moduleContext: Context): Boolean {
        val installedBuildId = runCatching {
            moduleContext.packageManager.getApplicationInfo(
                BuildConfig.APPLICATION_ID,
                PackageManager.GET_META_DATA,
            ).metaData?.getString(MODULE_BUILD_ID_META_DATA)
        }.getOrNull()
        return moduleBuildMatches(installedBuildId, BuildConfig.MODULE_BUILD_ID)
    }

    internal fun moduleBuildMatches(installedBuildId: String?, hookBuildId: String): Boolean =
        !installedBuildId.isNullOrBlank() && installedBuildId == hookBuildId

    private const val MODULE_BUILD_ID_META_DATA =
        "moe.chenxy.huaweipods.MODULE_BUILD_ID"
}
