package moe.chenxy.huaweipods.pods

/** 标识一次官方图片身份提交所属的耳机会话，阻止异步旧结果污染新会话。 */
internal data class HuaweiDeviceInfoPublishRequest(
    val generation: Long,
    val address: String,
    val route: HuaweiDeviceRoute,
    val identity: HuaweiDeviceInfoIdentity,
)

internal fun matchesHuaweiDeviceInfoPublishSession(
    request: HuaweiDeviceInfoPublishRequest,
    activeGeneration: Long,
    activeAddress: String?,
    activeRoute: HuaweiDeviceRoute,
    activeIdentity: HuaweiDeviceInfoIdentity?,
): Boolean =
    request.generation == activeGeneration &&
        request.address.equals(activeAddress, ignoreCase = true) &&
        request.route == activeRoute &&
        request.identity == activeIdentity
