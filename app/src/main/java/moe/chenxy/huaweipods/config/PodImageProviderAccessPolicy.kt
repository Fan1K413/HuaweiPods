package moe.chenxy.huaweipods.config

internal object PodImageProviderAccessPolicy {
    private const val MODULE_PACKAGE = "moe.chenxy.huaweipods"
    private const val SMART_AUDIO_PACKAGE = "com.huawei.smartaudio"

    private val imageConsumerPackages = setOf(
        "com.android.bluetooth",
        "com.android.settings",
        "com.milink.service",
        "com.xiaomi.bluetooth",
    )

    fun mayOpenImage(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE || callingPackage in imageConsumerPackages

    fun maySubmitSmartAudioIdentity(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE || callingPackage == SMART_AUDIO_PACKAGE
}
