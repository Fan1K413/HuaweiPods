package moe.chenxy.huaweipods.debugcapture

/** Debug 协议采集唯一允许注入和接收事件的官方应用。 */
internal object SmartAudioCaptureTarget {
    const val PACKAGE_NAME = "com.huawei.smartaudio"

    fun isAllowedSender(packageName: String?): Boolean = packageName == PACKAGE_NAME

    fun matchesSession(sessionPackage: String?, senderPackage: String?): Boolean =
        sessionPackage == PACKAGE_NAME && senderPackage == PACKAGE_NAME
}
