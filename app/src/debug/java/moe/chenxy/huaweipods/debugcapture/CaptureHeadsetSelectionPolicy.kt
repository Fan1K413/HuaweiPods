package moe.chenxy.huaweipods.debugcapture

/** 将“当前检测结果”与用户选择绑定，避免使用旧名称启动错误的采集会话。 */
internal object CaptureHeadsetSelectionPolicy {
    fun automaticTarget(result: DetectionResult?): ConnectedHeadset? =
        (result as? DetectionResult.Success)?.devices?.singleOrNull()

    fun selectedTarget(
        result: DetectionResult?,
        selectedAddress: String?,
        selectedFromConnection: Boolean,
    ): ConnectedHeadset? {
        if (!selectedFromConnection || selectedAddress.isNullOrBlank()) return null
        return (result as? DetectionResult.Success)
            ?.devices
            ?.singleOrNull { it.address.equals(selectedAddress, ignoreCase = true) }
    }
}
