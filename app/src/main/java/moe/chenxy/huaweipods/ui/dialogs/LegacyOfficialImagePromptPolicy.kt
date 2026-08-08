package moe.chenxy.huaweipods.ui.dialogs

import java.util.Locale
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute

/** FreeBuds 3 无法从已验证协议中读取配色，只在缺少主图时提示用户确认一次。 */
internal object LegacyOfficialImagePromptPolicy {
    fun isEligible(
        detailVisible: Boolean,
        deviceRoute: HuaweiDeviceRoute,
        connectedAddress: String,
        manualBoxImagePath: String?,
        cloudBoxImagePath: String?,
    ): Boolean =
        detailVisible &&
            deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 &&
            connectedAddress.isNotBlank() &&
            manualBoxImagePath.isNullOrBlank() &&
            cloudBoxImagePath.isNullOrBlank()
}

/**
 * 进程内的一次性门控。首次展示即视为已处理，因此用户主动关闭后本次进程不再打扰。
 */
internal class LegacyOfficialImagePromptGate {
    private val handledAddresses = mutableSetOf<String>()

    @Synchronized
    fun claimIfEligible(
        detailVisible: Boolean,
        deviceRoute: HuaweiDeviceRoute,
        connectedAddress: String,
        manualBoxImagePath: String?,
        cloudBoxImagePath: String?,
    ): Boolean {
        if (
            !LegacyOfficialImagePromptPolicy.isEligible(
                detailVisible = detailVisible,
                deviceRoute = deviceRoute,
                connectedAddress = connectedAddress,
                manualBoxImagePath = manualBoxImagePath,
                cloudBoxImagePath = cloudBoxImagePath,
            )
        ) {
            return false
        }
        return handledAddresses.add(connectedAddress.trim().uppercase(Locale.ROOT))
    }
}

internal val processLegacyOfficialImagePromptGate = LegacyOfficialImagePromptGate()
