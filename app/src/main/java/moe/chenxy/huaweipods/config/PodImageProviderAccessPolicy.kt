package moe.chenxy.huaweipods.config

internal object PodImageProviderAccessPolicy {
    private const val MODULE_PACKAGE = "moe.chenxy.huaweipods"

    private val imageConsumerPackages = setOf(
        "com.android.bluetooth",
        "com.android.settings",
        "com.milink.service",
        "com.xiaomi.bluetooth",
    )

    fun mayOpenImage(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE || callingPackage in imageConsumerPackages

    fun maySubmitOfficialImageIdentity(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE ||
            callingPackage == "com.android.bluetooth"
}
