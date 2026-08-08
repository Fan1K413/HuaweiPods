package moe.chenxy.huaweipods.smartaudio

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiDeviceInfoRoutePolicy

/**
 * App 内官方图片检索的已验证机型目录。未收录的路由不从名称或相似型号猜测。
 * FreeBuds 3 为旧协议机型，所以仅在用户选择官方配色时使用它的已知 modelId。
 */
internal object OfficialImageCatalogPolicy {
    private const val FREEBUDS3_LEGACY_MODEL_ID = "000027"

    fun modelIdForRoute(route: HuaweiDeviceRoute): String? = when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS3 -> FREEBUDS3_LEGACY_MODEL_ID
        else -> HuaweiDeviceInfoRoutePolicy.modelIdForRoute(route)
    }
}
