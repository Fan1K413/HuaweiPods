package moe.chenxy.huaweipods.pods

/** 控制连接电量岛只在有效且合适的时机触发一次。 */
internal class BatteryIslandTriggerPolicy(
    private val reconnectCooldownMs: Long = 30_000L,
) {
    private val lastTriggerAtByAddress = mutableMapOf<String, Long>()
    private var sessionHandled = false

    @Synchronized
    fun onNewSession() {
        sessionHandled = false
    }

    @Synchronized
    fun shouldTrigger(address: String, hasConnectedEarBattery: Boolean, now: Long): Boolean {
        if (sessionHandled || !hasConnectedEarBattery) return false
        val key = address.uppercase()
        val lastTriggerAt = lastTriggerAtByAddress[key]
        sessionHandled = true
        if (lastTriggerAt != null && now - lastTriggerAt < reconnectCooldownMs) return false
        lastTriggerAtByAddress[key] = now
        return true
    }
}
