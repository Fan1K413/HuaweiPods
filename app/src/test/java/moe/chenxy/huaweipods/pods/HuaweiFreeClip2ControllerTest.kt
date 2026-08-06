package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HuaweiFreeClip2ControllerTest {
    @Test
    fun `boolean feature packets match the guided capture`() {
        assertPacket("5A0006002B10010100B977", FreeClip2BooleanFeature.WEAR_DETECTION.packet(false))
        assertPacket("5A0006002B10010101A956", FreeClip2BooleanFeature.WEAR_DETECTION.packet(true))
        assertPacket("5A0009002BB4010107020100AFA4", FreeClip2BooleanFeature.DROP_REMINDER.packet(false))
        assertPacket("5A0009002BB4010107020101BF85", FreeClip2BooleanFeature.DROP_REMINDER.packet(true))
        assertPacket("5A0009002BB401010202010013E1", FreeClip2BooleanFeature.ADAPTIVE_VOLUME.packet(false))
        assertPacket("5A0009002BB401010202010103C0", FreeClip2BooleanFeature.ADAPTIVE_VOLUME.packet(true))
        assertPacket("5A0009002BB401010B020100E096", FreeClip2BooleanFeature.HEAD_MOTION_CONTROL.packet(false))
        assertPacket("5A0009002BB401010B020101F0B7", FreeClip2BooleanFeature.HEAD_MOTION_CONTROL.packet(true))
        assertPacket("5A0006002B870101002EC5", FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY.packet(false))
        assertPacket("5A0006002B870101013EE4", FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY.packet(true))
        assertPacket("5A0006002B6C010100B430", FreeClip2BooleanFeature.LOW_LATENCY.packet(false))
        assertPacket("5A0006002B6C010101A411", FreeClip2BooleanFeature.LOW_LATENCY.packet(true))
        assertPacket("5A0006002B2E01010037C4", FreeClip2BooleanFeature.DUAL_DEVICE.packet(false))
        assertPacket("5A0006002B2E01010127E5", FreeClip2BooleanFeature.DUAL_DEVICE.packet(true))
        assertPacket("5A0006002BB101010025B5", FreeClip2BooleanFeature.CASE_PROMPT_SOUND.packet(false))
        assertPacket("5A0006002BB10101013594", FreeClip2BooleanFeature.CASE_PROMPT_SOUND.packet(true))
    }

    @Test
    fun `spatial audio modes and scenes match the guided capture`() {
        assertPacket("5A0009002BB401011802010060ED", FreeClip2SpatialAudioMode.OFF.packet())
        assertPacket("5A0009002BB401011802010170CC", FreeClip2SpatialAudioMode.FIXED.packet())
        assertPacket("5A0009002BB401011802010240AF", FreeClip2SpatialAudioMode.HEAD_TRACKING.packet())
        assertPacket("5A0009002BB401011803010057DD", FreeClip2SpatialScene.DEFAULT.packet())
        assertPacket("5A0009002BB401011803010147FC", FreeClip2SpatialScene.AUDIO_THEATER.packet())
        assertPacket("5A0009002BB4010118030102779F", FreeClip2SpatialScene.CINEMA.packet())
        assertPacket("5A0009002BB401011803010367BE", FreeClip2SpatialScene.CONCERT_HALL.packet())
    }

    @Test
    fun `sound effect presets match the guided capture`() {
        assertPacket("5A0006002B4901010A9E71", FreeClip2SoundEffect.PRESET_1.packet())
        assertPacket("5A0006002B490101030F58", FreeClip2SoundEffect.PRESET_2.packet())
        assertPacket("5A0006002B49010109AE12", FreeClip2SoundEffect.PRESET_3.packet())
    }

    @Test
    fun `packets are returned as defensive copies`() {
        val first = FreeClip2SpatialAudioMode.FIXED.packet()
        first[0] = 0
        val second = FreeClip2SpatialAudioMode.FIXED.packet()
        assertFalse(first.contentEquals(second))
        assertPacket("5A0009002BB401011802010170CC", second)
    }

    private fun assertPacket(expected: String, actual: ByteArray) {
        assertArrayEquals(expected.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), actual)
    }
}
