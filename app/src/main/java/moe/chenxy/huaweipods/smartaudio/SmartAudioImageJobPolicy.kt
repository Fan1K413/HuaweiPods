package moe.chenxy.huaweipods.smartaudio

import java.security.MessageDigest

internal object SmartAudioImageJobPolicy {
    private const val JOB_ID_NAMESPACE = 0x4800_0000
    private const val JOB_ID_MASK = 0x07FF_FFFF

    /** 同一 MAC/型号/配色始终映射到同一 Job，重复身份事件只保留一个任务。 */
    fun jobId(identity: SmartAudioResourceIdentity): Int {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${identity.address}/${identity.modelId}/${identity.subModelId}".toByteArray())
        val hash = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        return JOB_ID_NAMESPACE or (hash and JOB_ID_MASK)
    }

    /** 永久格式错误与短暂网络错误均等到下一次身份事件再调度，避免后台无限重试。 */
    fun shouldRescheduleAfterFailure(): Boolean = false
}
