package moe.chenxy.huaweipods.hook

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** 融合设备中心宿主中使用的紧凑轨道式二级降噪选项。 */
internal class HuaweiAncSubModeSelectorView(
    context: Context,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    data class Option(
        val value: Int,
        val label: String,
    )

    init {
        orientation = VERTICAL
    }

    fun render(
        options: List<Option>,
        selectedValue: Int,
        darkSurface: Boolean,
    ) {
        removeAllViews()
        if (options.isEmpty()) return
        setPadding(context.dp(5), context.dp(2), context.dp(5), context.dp(2))
        addView(
            FrameLayout(context).apply {
                addView(
                    View(context).apply {
                        background = roundedTrack(darkSurface)
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        context.dp(6),
                    ).apply {
                        gravity = Gravity.TOP
                        topMargin = context.dp(8)
                        marginStart = context.dp(18)
                        marginEnd = context.dp(18)
                    },
                )
                addView(
                    LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        options.forEachIndexed { index, option ->
                            addView(
                                optionView(option, option.value == selectedValue, darkSurface),
                                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    if (index > 0) marginStart = context.dp(1)
                                },
                            )
                        }
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(45)),
        )
    }

    private fun optionView(
        option: Option,
        selected: Boolean,
        darkSurface: Boolean,
    ): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        isFocusable = true
        contentDescription = option.label
        setOnClickListener { onSelected(option.value) }
        addView(
            FrameLayout(context).apply {
                addView(
                    View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(optionPointColor(selected, darkSurface))
                        }
                    },
                    FrameLayout.LayoutParams(
                        context.dp(if (selected) 18 else 7),
                        context.dp(if (selected) 18 else 7),
                    ).apply {
                        gravity = Gravity.CENTER
                    },
                )
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(24)),
        )
        addView(
            TextView(context).apply {
                text = option.label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(optionTextColor(selected, darkSurface))
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun roundedTrack(darkSurface: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(
            if (darkSurface) Color.argb(42, 255, 255, 255)
            else Color.argb(24, 0, 0, 0),
        )
        cornerRadius = context.dp(4).toFloat()
    }

    private fun optionPointColor(selected: Boolean, darkSurface: Boolean): Int = when {
        selected -> Color.rgb(33, 150, 243)
        darkSurface -> Color.rgb(176, 184, 196)
        else -> Color.rgb(145, 165, 190)
    }

    private fun optionTextColor(selected: Boolean, darkSurface: Boolean): Int = when {
        selected -> Color.rgb(33, 150, 243)
        darkSurface -> Color.rgb(210, 214, 222)
        else -> Color.rgb(105, 115, 132)
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
