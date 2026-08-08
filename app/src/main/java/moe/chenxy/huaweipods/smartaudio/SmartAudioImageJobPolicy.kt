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

    /** 失败等到下一次身份事件或模块启动恢复，避免永久资源错误形成后台重试循环。 */
    fun shouldRescheduleAfterFailure(): Boolean = false
}
