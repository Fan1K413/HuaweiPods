package moe.chenxy.huaweipods.hook

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import kotlin.math.roundToInt

/** FreeClip 2 在系统宿主页面中使用的紧凑音频控制区。 */
internal class HuaweiFreeClip2AudioControlsView(
    context: Context,
    private val onSpatialModeSelected: (FreeClip2SpatialAudioMode) -> Unit,
    private val onSpatialSceneSelected: (FreeClip2SpatialScene) -> Unit,
    private val onSoundEffectSelected: (FreeClip2SoundEffect) -> Unit,
) : LinearLayout(context) {
    data class Labels(
        val spatialAudio: String,
        val spatialModeOff: String,
        val spatialModeFixed: String,
        val spatialModeHeadTracking: String,
        val spatialScene: String,
        val spatialSceneDefault: String,
        val spatialSceneTheater: String,
        val spatialSceneCinema: String,
        val spatialSceneConcert: String,
        val soundEffect: String,
        val soundEffectDefault: String,
        val soundEffectSport: String,
        val soundEffectTreble: String,
        val soundEffectClearVoice: String,
    )

    init {
        orientation = VERTICAL
    }

    fun render(
        spatialMode: FreeClip2SpatialAudioMode,
        spatialScene: FreeClip2SpatialScene,
        soundEffect: FreeClip2SoundEffect,
        labels: Labels,
        darkSurface: Boolean,
        showSpatialScene: Boolean,
        compact: Boolean,
    ) {
        removeAllViews()
        setPadding(context.dp(if (compact) 4 else 10), context.dp(4), context.dp(if (compact) 4 else 10), context.dp(8))

        addSelector(
            title = labels.spatialAudio,
            labels = listOf(
                labels.spatialModeOff,
                labels.spatialModeFixed,
                labels.spatialModeHeadTracking,
            ),
            selectedIndex = FreeClip2SpatialAudioMode.entries.indexOf(spatialMode),
            darkSurface = darkSurface,
        ) { index ->
            FreeClip2SpatialAudioMode.entries.getOrNull(index)?.let(onSpatialModeSelected)
        }

        if (showSpatialScene && spatialMode != FreeClip2SpatialAudioMode.OFF) {
            addSelector(
                title = labels.spatialScene,
                labels = listOf(
                    labels.spatialSceneDefault,
                    labels.spatialSceneTheater,
                    labels.spatialSceneCinema,
                    labels.spatialSceneConcert,
                ),
                selectedIndex = FreeClip2SpatialScene.entries.indexOf(spatialScene),
                darkSurface = darkSurface,
            ) { index ->
                FreeClip2SpatialScene.entries.getOrNull(index)?.let(onSpatialSceneSelected)
            }
        }

        addSelector(
            title = labels.soundEffect,
            labels = listOf(
                labels.soundEffectDefault,
                labels.soundEffectSport,
                labels.soundEffectTreble,
                labels.soundEffectClearVoice,
            ),
            selectedIndex = FreeClip2SoundEffect.entries.indexOf(soundEffect),
            darkSurface = darkSurface,
        ) { index ->
            FreeClip2SoundEffect.entries.getOrNull(index)?.let(onSoundEffectSelected)
        }
    }

    private fun addSelector(
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        darkSurface: Boolean,
        onSelected: (Int) -> Unit,
    ) {
        addView(
            TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (darkSurface) Color.rgb(210, 214, 222) else Color.rgb(60, 66, 78))
                setPadding(context.dp(8), context.dp(5), context.dp(8), 0)
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            HuaweiAncSubModeSelectorView(context, onSelected).apply {
                render(
                    options = labels.mapIndexed { index, label ->
                        HuaweiAncSubModeSelectorView.Option(index, label)
                    },
                    selectedValue = selectedIndex.coerceAtLeast(0),
                    darkSurface = darkSurface,
                )
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
