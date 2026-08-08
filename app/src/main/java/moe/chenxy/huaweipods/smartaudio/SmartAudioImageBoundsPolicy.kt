package moe.chenxy.huaweipods.smartaudio

import kotlin.math.ceil
import kotlin.math.max

internal data class AlphaContentBounds(
    val left: Int,
    val top: Int,
    val rightExclusive: Int,
    val bottomExclusive: Int,
) {
    val width: Int get() = rightExclusive - left
    val height: Int get() = bottomExclusive - top
}

internal data class ImageCropBounds(
    val left: Int,
    val top: Int,
    val rightExclusive: Int,
    val bottomExclusive: Int,
) {
    val width: Int get() = rightExclusive - left
    val height: Int get() = bottomExclusive - top
}

/** 仅规范化透明留白极端的官方图，普通耳机方图保持原始构图。 */
internal object SmartAudioImageBoundsPolicy {
    private const val SPARSE_CONTENT_AREA_RATIO = 0.32
    private const val CONTENT_MARGIN_RATIO = 0.05

    fun cropBounds(
        imageWidth: Int,
        imageHeight: Int,
        content: AlphaContentBounds,
    ): ImageCropBounds? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (content.width <= 0 || content.height <= 0) return null
        if (content.left < 0 || content.top < 0) return null
        if (content.rightExclusive > imageWidth || content.bottomExclusive > imageHeight) return null
        val areaRatio = content.width.toDouble() * content.height /
            (imageWidth.toDouble() * imageHeight)
        if (areaRatio >= SPARSE_CONTENT_AREA_RATIO) return null

        val margin = ceil(max(content.width, content.height) * CONTENT_MARGIN_RATIO)
            .toInt()
            .coerceAtLeast(1)
        val crop = ImageCropBounds(
            left = (content.left - margin).coerceAtLeast(0),
            top = (content.top - margin).coerceAtLeast(0),
            rightExclusive = (content.rightExclusive + margin).coerceAtMost(imageWidth),
            bottomExclusive = (content.bottomExclusive + margin).coerceAtMost(imageHeight),
        )
        return crop.takeUnless {
            it.left == 0 && it.top == 0 &&
                it.rightExclusive == imageWidth && it.bottomExclusive == imageHeight
        }
    }
}
