package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiFreeBuds5ControllerTest {
    @Test
    fun `boolean feature packets match the guided capture`() {
        assertPacket("5A0006002B10010100B977", FreeBuds5BooleanFeature.WEAR_DETECTION.packet(false))
        assertPacket("5A0006002B10010101A956", FreeBuds5BooleanFeature.WEAR_DETECTION.packet(true))
        assertPacket("5A0006002BA2010100A5CE", FreeBuds5BooleanFeature.HIGH_QUALITY_AUDIO.packet(false))
        assertPacket("5A0006002BA2010101B5EF", FreeBuds5BooleanFeature.HIGH_QUALITY_AUDIO.packet(true))
        assertPacket("5A0006002B6C010100B430", FreeBuds5BooleanFeature.LOW_LATENCY.packet(false))
        assertPacket("5A0006002B6C010101A411", FreeBuds5BooleanFeature.LOW_LATENCY.packet(true))
    }

    @Test
    fun `official sound effect packets match the guided capture`() {
        assertPacket("5A0006002B490101012F1A", FreeBuds5SoundEffect.DEFAULT.packet())
        assertPacket("5A0006002B490101021F79", FreeBuds5SoundEffect.BASS_ENHANCE.packet())
        assertPacket("5A0006002B490101030F58", FreeBuds5SoundEffect.TREBLE_ENHANCE.packet())
        assertPacket("5A0006002B49010109AE12", FreeBuds5SoundEffect.CLEAR_VOICE.packet())
    }

    @Test
    fun `state query packets match the captured protocol`() {
        assertPacket(
            "5A0005002B110100772A",
            HuaweiFreeBuds5Controller.wearDetectionStateQueryPacket(),
        )
        assertPacket(
            "5A0005002B4A02008C46",
            HuaweiFreeBuds5Controller.soundEffectStateQueryPacket(),
        )
        assertPacket(
            "5A0005002BA30101F794",
            HuaweiFreeBuds5Controller.highQualityAudioStateQueryPacket(),
        )
    }

    @Test
    fun `parses the latest wear detection state from concatenated frames`() {
        val enabled = hex("5A0006002B11010101DFE2")
        val disabled = hex("5A0006002B110101000FC3")

        assertTrue(HuaweiFreeBuds5Controller.parseWearDetectionState(enabled) == true)
        assertFalse(HuaweiFreeBuds5Controller.parseWearDetectionState(enabled + disabled) ?: true)
    }

    @Test
    fun `parses selected sound effect from field two rather than capability fields`() {
        val bass = hex("5A0014002B4A010101020102030401020309040101080071B2")
        val clearVoice = hex("5A0014002B4A01010102010903040102030904010108006F85")

        assertEquals(
            FreeBuds5SoundEffect.CLEAR_VOICE,
            HuaweiFreeBuds5Controller.parseSoundEffectState(bass + clearVoice),
        )
    }

    @Test
    fun `parses high quality audio readback`() {
        val enabled = hex("5A0009002BA3010101020101B623")
        val disabled = hex("5A0009002BA3010101020100A602")

        assertTrue(HuaweiFreeBuds5Controller.parseHighQualityAudioState(enabled) == true)
        assertFalse(HuaweiFreeBuds5Controller.parseHighQualityAudioState(enabled + disabled) ?: true)
    }

    @Test
    fun `partial readbacks merge without erasing earlier state`() {
        val wear = FreeBuds5SettingsState(wearDetection = true)
        val withEffect = mergeFreeBuds5SettingsState(
            wear,
            FreeBuds5SettingsState(soundEffect = FreeBuds5SoundEffect.BASS_ENHANCE),
        )
        val complete = mergeFreeBuds5SettingsState(
            withEffect,
            FreeBuds5SettingsState(highQualityAudio = false),
        )

        assertEquals(
            FreeBuds5SettingsState(
                wearDetection = true,
                soundEffect = FreeBuds5SoundEffect.BASS_ENHANCE,
                highQualityAudio = false,
            ),
            complete,
        )
    }

    @Test
    fun `rejects unknown and truncated states`() {
        val knownEffect = hex("5A0014002B4A010101020102030401020309040101080071B2")
        val unknownEffect = hex("5A0009002B4A01010102017F0000")
        assertNull(
            HuaweiFreeBuds5Controller.parseSoundEffectState(
                knownEffect + unknownEffect,
            ),
        )
        assertNull(
            HuaweiFreeBuds5Controller.parseHighQualityAudioState(
                hex("5A0009002BA30101010201020000"),
            ),
        )
        assertNull(
            HuaweiFreeBuds5Controller.parseWearDetectionState(
                hex("5A0006002B110101"),
            ),
        )
    }

    @Test
    fun `packet accessors return defensive copies`() {
        val featurePacket = FreeBuds5BooleanFeature.WEAR_DETECTION.packet(true)
        featurePacket[0] = 0
        assertPacket("5A0006002B10010101A956", FreeBuds5BooleanFeature.WEAR_DETECTION.packet(true))

        val effectPacket = FreeBuds5SoundEffect.BASS_ENHANCE.packet()
        effectPacket[0] = 0
        assertPacket("5A0006002B490101021F79", FreeBuds5SoundEffect.BASS_ENHANCE.packet())

        val queryPacket = HuaweiFreeBuds5Controller.soundEffectStateQueryPacket()
        queryPacket[0] = 0
        assertPacket("5A0005002B4A02008C46", HuaweiFreeBuds5Controller.soundEffectStateQueryPacket())
    }

    private fun assertPacket(expected: String, actual: ByteArray) {
        assertArrayEquals(hex(expected), actual)
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
