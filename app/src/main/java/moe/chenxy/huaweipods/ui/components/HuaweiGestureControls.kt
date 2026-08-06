package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.pods.FreeBudsPro3GestureToggle
import moe.chenxy.huaweipods.pods.FreeBudsPro3LongPressAction
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiGestureController
import moe.chenxy.huaweipods.pods.HuaweiGestureKind
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.pods.HuaweiSwipeAction
import moe.chenxy.huaweipods.pods.HuaweiTapAction
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GESTURE_WRITE_CONFIRM_DELAY_MS = 300L

@Composable
fun HuaweiGestureControls(
    route: HuaweiDeviceRoute,
    address: String,
    modifier: Modifier = Modifier,
) {
    val layout = remember(route) { huaweiGestureControlLayout(route) }
    if (!layout.isVisible) return

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var confirmedReadback by remember(route, address) {
        mutableStateOf(HuaweiGestureReadback())
    }
    FreeClip2GestureReadbackEffect(
        enabled = route == HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        address = address,
    ) { update ->
        val merged = confirmedReadback.mergedWith(update)
        confirmedReadback = merged
        persistFreeClip2GestureReadback(prefs, address, merged)
    }

    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.huawei_gesture_controls_title),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title2,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            if (layout.tapKinds.isNotEmpty()) {
                GestureSectionTitle(R.string.huawei_gesture_tap_section)
                layout.tapKinds.forEach { kind ->
                    HuaweiGestureSide.entries.forEach { side ->
                        TapActionPreference(
                            route = route,
                            address = address,
                            kind = kind,
                            side = side,
                            prefs = prefs,
                            confirmedAction = confirmedReadback.tapActions[HuaweiTapSlot(kind, side)]
                                .takeIf { route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 },
                        )
                    }
                }
            }

            if (layout.hasSwipe) {
                GestureSectionTitle(R.string.huawei_gesture_swipe_section)
                HuaweiGestureSide.entries.forEach { side ->
                    SwipeActionPreference(
                        route = route,
                        address = address,
                        side = side,
                        prefs = prefs,
                        confirmedAction = confirmedReadback.swipeActions[side]
                            .takeIf { route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 },
                    )
                }
            }

            if (layout.hasFreeBudsPro3Controls) {
                FreeBudsPro3GestureControls(address = address, prefs = prefs)
            }

            Text(
                text = stringResource(
                    if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                        R.string.huawei_gesture_readback_hint
                    } else {
                        R.string.huawei_gesture_local_state_hint
                    },
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun FreeClip2GestureReadbackEffect(
    enabled: Boolean,
    address: String,
    onReadback: (HuaweiGestureReadback) -> Unit,
) {
    val context = LocalContext.current
    val currentOnReadback by rememberUpdatedState(onReadback)
    DisposableEffect(enabled, address, context) {
        if (!enabled) return@DisposableEffect onDispose { }

        val receiverContext = context.applicationContext ?: context
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedIntent = intent ?: return
                if (receivedIntent.action != HuaweiPodsAction.ACTION_HUAWEI_GESTURE_CHANGED) return
                val receivedAddress = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_ADDRESS)
                if (receivedAddress.isNullOrBlank() ||
                    !receivedAddress.equals(address, ignoreCase = true)
                ) return
                currentOnReadback(
                    freeClip2GestureReadback(
                        doubleLeft = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_DOUBLE_LEFT_ACTION)
                            ?: receivedIntent.getStringExtra("left_action"),
                        doubleRight = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_DOUBLE_RIGHT_ACTION)
                            ?: receivedIntent.getStringExtra("right_action"),
                        tripleLeft = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_TRIPLE_LEFT_ACTION),
                        tripleRight = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_TRIPLE_RIGHT_ACTION),
                        swipeLeft = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_SWIPE_LEFT_ACTION),
                        swipeRight = receivedIntent.getStringExtra(HuaweiGestureController.EXTRA_SWIPE_RIGHT_ACTION),
                    ),
                )
            }
        }
        receiverContext.registerReceiver(
            receiver,
            IntentFilter(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_CHANGED),
            Context.RECEIVER_EXPORTED,
        )
        val lifecycleOwner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                receiverContext.requestFreeClip2GestureState(address)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        receiverContext.requestFreeClip2GestureState(address)

        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            runCatching { receiverContext.unregisterReceiver(receiver) }
        }
    }
}

@Composable
private fun TapActionPreference(
    route: HuaweiDeviceRoute,
    address: String,
    kind: HuaweiGestureKind,
    side: HuaweiGestureSide,
    prefs: SharedPreferences,
    confirmedAction: HuaweiTapAction? = null,
) {
    val actions = remember(route, kind) { HuaweiTapAction.availableFor(route, kind) }
    if (actions.isEmpty()) return

    val key = remember(route, address, kind, side) {
        tapPreferenceKey(route, address, kind, side)
    }
    var localSelected by remember(key) {
        mutableStateOf(readTapAction(prefs, key, actions))
    }
    val selected = confirmedAction?.takeIf(actions::contains) ?: localSelected
    val context = LocalContext.current
    GestureChoicePreference(
        title = stringResource(tapTitleRes(kind, side)),
        selected = selected,
        values = actions,
        label = { stringResource(it.labelRes()) },
        onSelected = { action, complete ->
            context.sendTapAction(route, address, kind, side, action) { success ->
                if (success) {
                    localSelected = action
                    prefs.edit().putString(key, action.extraValue).apply()
                    if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                        context.requestFreeClip2GestureState(
                            address = address,
                            delayMs = GESTURE_WRITE_CONFIRM_DELAY_MS,
                            force = true,
                        )
                    }
                }
                complete(success)
            }
        },
    )
}

@Composable
private fun SwipeActionPreference(
    route: HuaweiDeviceRoute,
    address: String,
    side: HuaweiGestureSide,
    prefs: SharedPreferences,
    confirmedAction: HuaweiSwipeAction? = null,
) {
    val actions = remember(route) { HuaweiSwipeAction.availableFor(route) }
    if (actions.isEmpty()) return

    val key = remember(route, address, side) { swipePreferenceKey(route, address, side) }
    var localSelected by remember(key) {
        mutableStateOf(readSwipeAction(prefs, key, actions))
    }
    val selected = confirmedAction?.takeIf(actions::contains) ?: localSelected
    val context = LocalContext.current
    GestureChoicePreference(
        title = stringResource(swipeTitleRes(side)),
        selected = selected,
        values = actions,
        label = { stringResource(it.labelRes()) },
        onSelected = { action, complete ->
            context.sendSwipeAction(route, address, side, action) { success ->
                if (success) {
                    localSelected = action
                    prefs.edit().putString(key, action.extraValue).apply()
                    if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                        context.requestFreeClip2GestureState(
                            address = address,
                            delayMs = GESTURE_WRITE_CONFIRM_DELAY_MS,
                            force = true,
                        )
                    }
                }
                complete(success)
            }
        },
    )
}

@Composable
private fun FreeBudsPro3GestureControls(
    address: String,
    prefs: SharedPreferences,
) {
    val context = LocalContext.current
    GestureSectionTitle(R.string.huawei_gesture_pro3_long_press_section)
    HuaweiGestureSide.entries.forEach { side ->
        FreeBudsPro3LongPressPreference(address, side, prefs)
    }

    GestureSectionTitle(R.string.huawei_gesture_pro3_pinch_section)
    FreeBudsPro3GestureToggle.entries.forEach { gesture ->
        val key = remember(address, gesture) { pro3TogglePreferenceKey(address, gesture) }
        GestureTogglePreference(
            stateKey = key,
            title = stringResource(gesture.labelRes()),
            initialValue = prefs.getBoolean(key, true),
            onChange = { enabled, complete ->
                context.sendPro3GestureToggle(address, gesture, enabled) { success ->
                    if (success) prefs.edit().putBoolean(key, enabled).apply()
                    complete(success)
                }
            },
        )
    }

    val swipeKey = remember(address) { pro3SwipePreferenceKey(address) }
    GestureTogglePreference(
        stateKey = swipeKey,
        title = stringResource(R.string.huawei_gesture_pro3_swipe_volume),
        initialValue = prefs.getBoolean(swipeKey, true),
        onChange = { enabled, complete ->
            context.sendPro3SwipeVolume(address, enabled) { success ->
                if (success) prefs.edit().putBoolean(swipeKey, enabled).apply()
                complete(success)
            }
        },
    )
}

@Composable
private fun FreeBudsPro3LongPressPreference(
    address: String,
    side: HuaweiGestureSide,
    prefs: SharedPreferences,
) {
    val key = remember(address, side) { pro3LongPressPreferenceKey(address, side) }
    var selected by remember(key) {
        mutableStateOf(
            FreeBudsPro3LongPressAction.entries.firstOrNull {
                it.extraValue == prefs.getString(key, null)
            } ?: FreeBudsPro3LongPressAction.NOISE_CONTROL,
        )
    }
    val context = LocalContext.current
    GestureChoicePreference(
        title = stringResource(longPressTitleRes(side)),
        selected = selected,
        values = FreeBudsPro3LongPressAction.entries,
        label = { stringResource(it.labelRes()) },
        onSelected = { action, complete ->
            context.sendPro3LongPress(address, side, action) { success ->
                if (success) {
                    selected = action
                    prefs.edit().putString(key, action.extraValue).apply()
                }
                complete(success)
            }
        },
    )
}

@Composable
private fun GestureSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = MiuixTheme.colorScheme.primary,
        style = MiuixTheme.textStyles.headline1,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> GestureChoicePreference(
    title: String,
    selected: T,
    values: List<T>,
    label: @Composable (T) -> String,
    onSelected: (T, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember(title) { mutableStateOf(false) }
    var pending by remember(title) { mutableStateOf(false) }
    val selectedLabel = label(selected)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !pending, role = Role.Button) { showDialog = true }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(title, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.headline1)
        Text(
            selectedLabel,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }

    OverlayDialog(
        title = title,
        summary = selectedLabel,
        show = showDialog,
        onDismissRequest = { if (!pending) showDialog = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            values.forEach { value ->
                val isSelected = value == selected
                GestureChoiceRow(
                    label = label(value),
                    selected = isSelected,
                    enabled = !pending,
                    onClick = {
                        if (isSelected) {
                            showDialog = false
                        } else {
                            pending = true
                            onSelected(value) { success ->
                                pending = false
                                showDialog = false
                                if (!success) {
                                    Toast.makeText(
                                        context,
                                        R.string.huawei_gesture_send_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GestureChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Checkbox(state = ToggleableState(selected), enabled = enabled, onClick = onClick)
    }
}

@Composable
private fun GestureTogglePreference(
    stateKey: String,
    title: String,
    initialValue: Boolean,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember(stateKey, initialValue) { mutableStateOf(initialValue) }
    var pending by remember(stateKey) { mutableStateOf(false) }
    val toggle = {
        if (!pending) {
            val target = !enabled
            pending = true
            onChange(target) { success ->
                pending = false
                if (success) {
                    enabled = target
                } else {
                    Toast.makeText(
                        context,
                        R.string.huawei_gesture_send_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !pending, role = Role.Switch, onClick = toggle)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, enabled = !pending, onCheckedChange = { toggle() })
    }
}

@SuppressLint("MissingPermission")
private fun Context.gestureDevice(address: String): BluetoothDevice? {
    if (!BluetoothAdapter.checkBluetoothAddress(address)) return null
    return runCatching {
        getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.getRemoteDevice(address)
    }.getOrNull()
}

private fun Context.sendTapAction(
    route: HuaweiDeviceRoute,
    address: String,
    kind: HuaweiGestureKind,
    side: HuaweiGestureSide,
    action: HuaweiTapAction,
    complete: (Boolean) -> Unit,
) {
    val device = gestureDevice(address) ?: return complete(false)
    HuaweiGestureController.setTap(this, device, route, kind, side, action, complete)
}

private fun Context.sendSwipeAction(
    route: HuaweiDeviceRoute,
    address: String,
    side: HuaweiGestureSide,
    action: HuaweiSwipeAction,
    complete: (Boolean) -> Unit,
) {
    val device = gestureDevice(address) ?: return complete(false)
    HuaweiGestureController.setSwipe(this, device, route, side, action, complete)
}

private fun Context.sendPro3LongPress(
    address: String,
    side: HuaweiGestureSide,
    action: FreeBudsPro3LongPressAction,
    complete: (Boolean) -> Unit,
) {
    val device = gestureDevice(address) ?: return complete(false)
    HuaweiGestureController.setFreeBudsPro3LongPress(this, device, side, action, complete)
}

private fun Context.sendPro3GestureToggle(
    address: String,
    gesture: FreeBudsPro3GestureToggle,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = gestureDevice(address) ?: return complete(false)
    HuaweiGestureController.setFreeBudsPro3GestureToggle(this, device, gesture, enabled, complete)
}

private fun Context.sendPro3SwipeVolume(
    address: String,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = gestureDevice(address) ?: return complete(false)
    HuaweiGestureController.setFreeBudsPro3SwipeVolume(this, device, enabled, complete)
}

private fun Context.requestFreeClip2GestureState(
    address: String,
    delayMs: Long = 0L,
    force: Boolean = false,
) {
    val targetContext = applicationContext ?: this
    val request = {
        targetContext.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_REFRESH).apply {
            putExtra(HuaweiGestureController.EXTRA_ADDRESS, address)
            encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.HUAWEI_FREECLIP2)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra("force", force)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }
    if (delayMs > 0L) {
        Handler(Looper.getMainLooper()).postDelayed(request, delayMs)
    } else {
        request()
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

internal data class HuaweiTapSlot(
    val kind: HuaweiGestureKind,
    val side: HuaweiGestureSide,
)

internal data class HuaweiGestureReadback(
    val tapActions: Map<HuaweiTapSlot, HuaweiTapAction> = emptyMap(),
    val swipeActions: Map<HuaweiGestureSide, HuaweiSwipeAction> = emptyMap(),
) {
    fun mergedWith(update: HuaweiGestureReadback): HuaweiGestureReadback = HuaweiGestureReadback(
        tapActions = tapActions + update.tapActions,
        swipeActions = swipeActions + update.swipeActions,
    )
}

internal fun freeClip2GestureReadback(
    doubleLeft: String? = null,
    doubleRight: String? = null,
    tripleLeft: String? = null,
    tripleRight: String? = null,
    swipeLeft: String? = null,
    swipeRight: String? = null,
): HuaweiGestureReadback {
    val route = HuaweiDeviceRoute.HUAWEI_FREECLIP2
    val tapActions = buildMap {
        listOf(
            Triple(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureSide.LEFT, doubleLeft),
            Triple(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureSide.RIGHT, doubleRight),
            Triple(HuaweiGestureKind.TRIPLE_TAP, HuaweiGestureSide.LEFT, tripleLeft),
            Triple(HuaweiGestureKind.TRIPLE_TAP, HuaweiGestureSide.RIGHT, tripleRight),
        ).forEach { (kind, side, rawAction) ->
            HuaweiTapAction.fromExtra(rawAction)
                ?.takeIf { it in HuaweiTapAction.availableFor(route, kind) }
                ?.let { put(HuaweiTapSlot(kind, side), it) }
        }
    }
    val swipeActions = buildMap {
        listOf(
            HuaweiGestureSide.LEFT to swipeLeft,
            HuaweiGestureSide.RIGHT to swipeRight,
        ).forEach { (side, rawAction) ->
            HuaweiSwipeAction.fromExtra(rawAction)
                ?.takeIf { it in HuaweiSwipeAction.availableFor(route) }
                ?.let { put(side, it) }
        }
    }
    return HuaweiGestureReadback(tapActions = tapActions, swipeActions = swipeActions)
}

private fun persistFreeClip2GestureReadback(
    prefs: SharedPreferences,
    address: String,
    readback: HuaweiGestureReadback,
) {
    val editor = prefs.edit()
    readback.tapActions.forEach { (slot, action) ->
        editor.putString(
            tapPreferenceKey(HuaweiDeviceRoute.HUAWEI_FREECLIP2, address, slot.kind, slot.side),
            action.extraValue,
        )
    }
    readback.swipeActions.forEach { (side, action) ->
        editor.putString(
            swipePreferenceKey(HuaweiDeviceRoute.HUAWEI_FREECLIP2, address, side),
            action.extraValue,
        )
    }
    editor.apply()
}

internal data class HuaweiGestureControlLayout(
    val tapKinds: List<HuaweiGestureKind> = emptyList(),
    val hasSwipe: Boolean = false,
    val hasFreeBudsPro3Controls: Boolean = false,
) {
    val isVisible: Boolean
        get() = tapKinds.isNotEmpty() || hasSwipe || hasFreeBudsPro3Controls
}

internal fun huaweiGestureControlLayout(route: HuaweiDeviceRoute): HuaweiGestureControlLayout {
    if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS3) return HuaweiGestureControlLayout()
    val tapKinds = listOf(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureKind.TRIPLE_TAP)
        .filter { HuaweiTapAction.availableFor(route, it).isNotEmpty() }
    return HuaweiGestureControlLayout(
        tapKinds = tapKinds,
        hasSwipe = HuaweiSwipeAction.availableFor(route).isNotEmpty(),
        hasFreeBudsPro3Controls = route == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
    )
}

internal fun gesturePreferencePrefix(route: HuaweiDeviceRoute, address: String): String =
    "huawei_gesture_v2_${address.ifBlank { "unknown" }.uppercase(Locale.ROOT)}_${route.name.lowercase(Locale.ROOT)}"

private fun tapPreferenceKey(
    route: HuaweiDeviceRoute,
    address: String,
    kind: HuaweiGestureKind,
    side: HuaweiGestureSide,
): String = "${gesturePreferencePrefix(route, address)}_${kind.extraValue}_${side.extraValue}"

private fun swipePreferenceKey(
    route: HuaweiDeviceRoute,
    address: String,
    side: HuaweiGestureSide,
): String = "${gesturePreferencePrefix(route, address)}_swipe_${side.extraValue}"

private fun pro3LongPressPreferenceKey(address: String, side: HuaweiGestureSide): String =
    "${gesturePreferencePrefix(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, address)}_long_press_${side.extraValue}"

private fun pro3TogglePreferenceKey(address: String, gesture: FreeBudsPro3GestureToggle): String =
    "${gesturePreferencePrefix(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, address)}_${gesture.extraValue}"

private fun pro3SwipePreferenceKey(address: String): String =
    "${gesturePreferencePrefix(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, address)}_swipe_volume"

private fun readTapAction(
    prefs: SharedPreferences,
    key: String,
    actions: List<HuaweiTapAction>,
): HuaweiTapAction = HuaweiTapAction.fromExtra(prefs.getString(key, null))
    ?.takeIf(actions::contains)
    ?: actions.first()

private fun readSwipeAction(
    prefs: SharedPreferences,
    key: String,
    actions: List<HuaweiSwipeAction>,
): HuaweiSwipeAction = HuaweiSwipeAction.fromExtra(prefs.getString(key, null))
    ?.takeIf(actions::contains)
    ?: actions.first()

@StringRes
private fun tapTitleRes(kind: HuaweiGestureKind, side: HuaweiGestureSide): Int = when (kind to side) {
    HuaweiGestureKind.DOUBLE_TAP to HuaweiGestureSide.LEFT -> R.string.huawei_gesture_left_double_tap
    HuaweiGestureKind.DOUBLE_TAP to HuaweiGestureSide.RIGHT -> R.string.huawei_gesture_right_double_tap
    HuaweiGestureKind.TRIPLE_TAP to HuaweiGestureSide.LEFT -> R.string.huawei_gesture_left_triple_tap
    HuaweiGestureKind.TRIPLE_TAP to HuaweiGestureSide.RIGHT -> R.string.huawei_gesture_right_triple_tap
    else -> error("Unsupported tap control: $kind/$side")
}

@StringRes
private fun swipeTitleRes(side: HuaweiGestureSide): Int = when (side) {
    HuaweiGestureSide.LEFT -> R.string.huawei_gesture_left_swipe
    HuaweiGestureSide.RIGHT -> R.string.huawei_gesture_right_swipe
}

@StringRes
private fun longPressTitleRes(side: HuaweiGestureSide): Int = when (side) {
    HuaweiGestureSide.LEFT -> R.string.huawei_gesture_left_long_press
    HuaweiGestureSide.RIGHT -> R.string.huawei_gesture_right_long_press
}

@StringRes
private fun HuaweiTapAction.labelRes(): Int = when (this) {
    HuaweiTapAction.PLAY_NEXT -> R.string.huawei_gesture_action_next
    HuaweiTapAction.PLAY_PREVIOUS -> R.string.huawei_gesture_action_previous
    HuaweiTapAction.PLAY_PAUSE -> R.string.huawei_gesture_action_play_pause
    HuaweiTapAction.NOISE_CANCELLATION -> R.string.huawei_gesture_action_noise_control
    HuaweiTapAction.SPATIAL_AUDIO -> R.string.huawei_gesture_action_spatial_audio
    HuaweiTapAction.VOICE_ASSISTANT -> R.string.huawei_gesture_action_voice_assistant
    HuaweiTapAction.NONE -> R.string.huawei_gesture_action_none
}

@StringRes
private fun HuaweiSwipeAction.labelRes(): Int = when (this) {
    HuaweiSwipeAction.VOLUME_CONTROL -> R.string.huawei_gesture_action_volume_control
    HuaweiSwipeAction.TRACK_CONTROL -> R.string.huawei_gesture_action_track_control
    HuaweiSwipeAction.NONE -> R.string.huawei_gesture_action_none
}

@StringRes
private fun FreeBudsPro3LongPressAction.labelRes(): Int = when (this) {
    FreeBudsPro3LongPressAction.VOICE_ASSISTANT -> R.string.huawei_gesture_action_voice_assistant
    FreeBudsPro3LongPressAction.NOISE_CONTROL -> R.string.huawei_gesture_action_noise_control
    FreeBudsPro3LongPressAction.NONE -> R.string.huawei_gesture_action_none
}

@StringRes
private fun FreeBudsPro3GestureToggle.labelRes(): Int = when (this) {
    FreeBudsPro3GestureToggle.CALL_ANSWER_END -> R.string.huawei_gesture_pro3_call_answer_end
    FreeBudsPro3GestureToggle.CALL_REJECT -> R.string.huawei_gesture_pro3_call_reject
    FreeBudsPro3GestureToggle.MEDIA_PLAY_PAUSE -> R.string.huawei_gesture_pro3_media_play_pause
    FreeBudsPro3GestureToggle.MEDIA_NEXT -> R.string.huawei_gesture_pro3_media_next
    FreeBudsPro3GestureToggle.MEDIA_PREVIOUS -> R.string.huawei_gesture_pro3_media_previous
}
