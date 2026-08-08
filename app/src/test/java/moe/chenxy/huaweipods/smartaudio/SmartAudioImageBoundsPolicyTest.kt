package moe.chenxy.huaweipods.smartaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartAudioImageBoundsPolicyTest {
    @Test
    fun `sparse Eyewear image is cropped with five percent content margin`() {
        val crop = SmartAudioImageBoundsPolicy.cropBounds(
            imageWidth = 1_008,
            imageHeight = 1_008,
            content = AlphaContentBounds(
                left = 22,
                top = 376,
                rightExclusive = 986,
                bottomExclusive = 624,
            ),
        )

        assertEquals(ImageCropBounds(0, 327, 1_008, 673), crop)
    }

    @Test
    fun `ordinary square earbud composition is not cropped`() {
        assertNull(
            SmartAudioImageBoundsPolicy.cropBounds(
                imageWidth = 1_008,
                imageHeight = 1_008,
                content = AlphaContentBounds(
                    left = 161,
                    top = 174,
                    rightExclusive = 846,
                    bottomExclusive = 833,
                ),
            ),
        )
    }

    @Test
    fun `already normalized Eyewear image is not cropped again`() {
        assertNull(
            SmartAudioImageBoundsPolicy.cropBounds(
                imageWidth = 1_008,
                imageHeight = 355,
                content = AlphaContentBounds(
                    left = 22,
                    top = 50,
                    rightExclusive = 986,
                    bottomExclusive = 298,
                ),
            ),
        )
    }
}
