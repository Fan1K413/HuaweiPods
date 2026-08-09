package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.HuaweiPodsApp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiLowLatencyController
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 通用低时延自动保持开关，仅对已有逐机型 setter 抓包证据的路由展示。 */
@Composable
fun LowLatencyControl(address: String, route: HuaweiDeviceRoute) {
    if (!route.supportsLowLatencyControl) return
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    var enabled by remember(address, route) {
        mutableStateOf(LowLatencyPrefs.desiredOrNull(prefs, address, route) ?: false)
    }
    var pending by remember(address, route) { mutableStateOf(false) }
    val toggle = {
        if (!pending) {
            val target = !enabled
            val device = context.lowLatencyDevice(address)
            if (device == null) {
                Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
            } else {
                pending = true
                HuaweiLowLatencyController.setEnabled(context, device, route, target) { success ->
                    val stored = success && LowLatencyPrefs.setDesired(
                        prefs = prefs,
                        service = HuaweiPodsApp.xposedService,
                        address = address,
                        route = route,
                        enabled = target,
                    )
                    pending = false
                    if (stored) {
                        enabled = target
                    } else {
                        Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                    }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.low_latency_mode),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = stringResource(R.string.low_latency_auto_apply_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = if (enabled) ToggleableState.On else ToggleableState.Off,
            enabled = !pending,
            onClick = toggle,
        )
    }
}

@SuppressLint("MissingPermission")
private fun Context.lowLatencyDevice(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)
